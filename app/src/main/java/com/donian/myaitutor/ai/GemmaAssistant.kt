package com.donian.myaitutor.ai

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.donian.myaitutor.model.ModelDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/**
 * Owns one LiteRT-LM Engine and one multi-turn Conversation.
 * All native runtime operations are serialized on one dedicated thread.
 */
class GemmaAssistant(context: Context) {
    private val appContext = context.applicationContext
    private val runtimeDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "focus-ai-litert-runtime").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private var engine: Engine? = null
    @Volatile
    private var conversation: Conversation? = null
    private var selectedBackendLabel: String = "미실행"
    private val generationMutex = Mutex()

    suspend fun preWarm() {
        withContext(runtimeDispatcher) {
            runCatching { ensureInitializedOnRuntimeThread() }
        }
    }

    suspend fun analyzeImage(
        imageFile: File,
        subject: String,
        onTextChanged: suspend (String) -> Unit
    ) = withContext(runtimeDispatcher) {
        require(imageFile.exists() && imageFile.length() > 0L) { "촬영된 이미지가 없습니다." }
        ensureInitializedOnRuntimeThread()

        val prompt = """
            [풀어야 할 문제]
            선택 과목: $subject

            이번 요청에 첨부된 새 이미지와 선택 과목만 기준으로 문제를 직접 읽고 분석해 주세요.
            이전 대화의 문제나 답변을 이어서 처리하지 마세요.
            OCR 텍스트만 가정하지 말고, 이미지 속 문장, 식, 표, 그래프, 형식을 모두 확인하세요.
            수식은 실제 수학식으로 표시될 수 있도록 표준 LaTeX 문법으로 작성하세요.
            문장 안의 짧은 수식은 ${'$'}...${'$'}, 독립된 수식은 ${'$'}${'$'}...${'$'}${'$'}로 감싸세요.
            분수는 \\frac{a}{b}, 제곱근은 \\sqrt{x}, 지수와 아래첨자는 x^{2}, a_{n}처럼 작성하세요.
            수학 기호를 ASCII 문자로 풀어 쓰거나 코드 블록에 넣지 말고, 수식 외의 설명은 한국어로 작성하세요.
            첫 답변은 전체 과정을 한 번에 풀지 말고, 문제의 핵심과 필요한 최소 계산 및 최종 답을 3~5문장으로 간결하게 설명하세요.
            자세한 유도 과정, 검산, 추가 개념은 사용자가 후속 질문으로 선택할 때까지 생략하세요.
            계산식이 필요하면 핵심 식만 짧게 보여주세요. 앱이 후속 선택 메뉴를 제공하므로 답변에 선택지 목록을 적지 마세요.

            이미지가 흐리거나 문제 텍스트가 잘렸거나 식·형식을 읽기 어렵다면 추측으로 말고,
            어떤 부분을 다시 촬영해야 하는지 구체적으로 알려주세요.
            여러 문제가 보이면 중앙의 문제 하나만 선택해 풀이하고, 읽기 어려운 부분이 있으면 그 사실을 먼저 알려주세요.
        """.trimIndent()

        streamResponse(
            conversation = requireNotNull(conversation),
            contents = Contents.of(
                Content.ImageFile(imageFile.absolutePath),
                Content.Text(prompt)
            ),
            onTextChanged = onTextChanged
        )
    }

    suspend fun askFollowUp(
        question: String,
        onTextChanged: suspend (String) -> Unit
    ) = withContext(runtimeDispatcher) {
        require(question.isNotBlank()) { "질문 내용을 입력해 주세요." }
        ensureInitializedOnRuntimeThread()
        val followUpPrompt = """
            아래 질문에 직접 답변하세요. 문제의 이전 문맥을 활용하되, 학생이 묻지 않은 선택지나 다음 질문 메뉴는 출력하지 마세요.
            학생의 질문:
            $question
        """.trimIndent()
        streamResponse(
            conversation = requireNotNull(conversation),
            contents = Contents.of(Content.Text(followUpPrompt)),
            onTextChanged = onTextChanged
        )
    }

    suspend fun backendLabel(): String = withContext(runtimeDispatcher) { selectedBackendLabel }

    /**
     * 진행 중인 답변 생성을 중단합니다.
     * 런타임 스레드는 생성 중이라 대기할 수 없으므로, 네이티브 취소 플래그만
     * 즉시 세팅하고 반환합니다. 메인 스레드에서 호출해도 안전합니다.
     */
    fun cancelGeneration() {
        runCatching { conversation?.cancelProcess() }
    }

    private suspend fun ensureInitializedOnRuntimeThread() {
        if (engine != null && conversation != null) return

        val modelFile = ModelDownloadManager(appContext).modelFile
        require(modelFile.isFile && modelFile.length() >= 3_500_000_000L) {
            "Gemma 4 E4B 모델이 설치되지 않았습니다. 설정에서 모델을 다운로드해 주세요."
        }

        val systemInstruction = """
            당신은 MyAITutor의 과목별 문제 학습 도우미입니다.
            목표는 학생이 답만 얻는 것이 아니라, 개념을 이해하고 스스로 풀 수 있도록 하는 것입니다.
            문제마다 친절하고 차분한 한국어 존댓말을 사용하세요.
            새 문제의 첫 답변은 문제의 핵심, 필요한 최소 계산, 최종 답만 3~5문장으로 간결하게 설명하세요.
            사용자가 자세한 풀이를 요청한 경우에만 단계별 유도 과정과 검산을 설명하고, 답변만 요청하면 정답만 제시하세요.
            근거가 부족하거나 촬영 이미지가 불명확하면 답을 꾸며내지 말고 사용자에게 재촬영을 청하세요.
            후속 질문은 현재 문제의 맥락에 맞춰 요청한 내용만 답하고, 앞선 설명을 불필요하게 반복하지 마세요.
            답변 끝에 선택지 목록을 텍스트로 적지 마세요. 앱 화면에 후속 선택 메뉴가 표시됩니다.
            모든 수학식은 표준 LaTeX 문법으로 작성하세요. 문장 안 수식은 ${'$'}...${'$'}, 독립된 수식은 ${'$'}${'$'}...${'$'}${'$'}로 감싸세요.
            분수는 \\frac{a}{b}, 제곱근은 \\sqrt{x}, 위·아래첨자는 x^{2}, a_{n}처럼 표현하세요.
            수식을 ASCII 문자로 풀어 쓰거나 코드 블록에 넣지 말고, 풀이 설명은 한국어로 작성하세요.
            계산식은 한 줄에 하나씩 쓰고, 수식 주위에 코드 블록을 사용하지 마세요.
            핵심 용어는 굵게 표시해도 됩니다. 다만 표, 코드 블록, 링크, 이미지는 사용하지 마세요.
        """.trimIndent()

        val configs = listOf(
            "GPU" to EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.GPU(),
                visionBackend = Backend.GPU(),
                cacheDir = appContext.cacheDir.absolutePath
            ),
            "CPU" to EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU(),
                visionBackend = Backend.CPU(),
                cacheDir = appContext.cacheDir.absolutePath
            )
        )

        var lastError: Throwable? = null
        for ((backendName, config) in configs) {
            val candidate = Engine(config)
            var initialized = false
            try {
                candidate.initialize()
                initialized = true
                val candidateConversation = candidate.createConversation(
                    ConversationConfig(
                        systemInstruction = Contents.of(systemInstruction),
                        samplerConfig = SamplerConfig(
                            topK = 20,
                            topP = 0.9,
                            temperature = 0.35
                        ),
                        maxOutputToken = 2048
                    )
                )
                engine = candidate
                conversation = candidateConversation
                selectedBackendLabel = backendName
                return
            } catch (error: Exception) {
                lastError = error
                // Do not call close() on an Engine whose initialize() failed.
                // If initialization succeeded but conversation creation failed, release it.
                if (initialized) runCatching { candidate.close() }
            }
        }

        throw IllegalStateException(
            "Gemma 4 E4B가 GPU와 CPU에서 모두 초기화하지 못했습니다. 기기 메모리와 모델 파일을 확인해 주세요.",
            lastError
        )
    }

    private suspend fun streamResponse(
        conversation: Conversation,
        contents: Contents,
        onTextChanged: suspend (String) -> Unit
    ) {
        val accumulated = StringBuilder()
        generationMutex.withLock {
            try {
                conversation.sendMessageAsync(contents).collect { message ->
                    accumulated.append(message.toString())
                    withContext(Dispatchers.Main.immediate) {
                        onTextChanged(accumulated.toString())
                    }
                }
            } finally {
                // 취소 정리가 끝난 뒤에만 다음 요청이 같은 대화를 사용할 수 있습니다.
                if (!currentCoroutineContext().isActive) {
                    runCatching { conversation.cancelProcess() }
                }
            }
        }
    }

    suspend fun release() = withContext(runtimeDispatcher) {
        generationMutex.withLock {
            runCatching { conversation?.close() }
            conversation = null
            runCatching { engine?.close() }
            engine = null
            selectedBackendLabel = "미실행"
        }
    }
}