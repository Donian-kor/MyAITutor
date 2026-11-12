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
import kotlinx.coroutines.flow.collect
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
    private var conversation: Conversation? = null
    private var selectedBackendLabel: String = "미실행"

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

            카메라로 촬영한 이미지의 문제를 직접 읽고 분석해 주세요.
            OCR 텍스트만 가정하지 말고, 이미지 속 문장, 식, 표, 그래프, 형식을 모두 확인하세요.
            수식은 실제 수학식으로 표시될 수 있도록 표준 LaTeX 문법으로 작성하세요.
            문장 안의 짧은 수식은 ${'$'}...${'$'}, 독립된 수식은 ${'$'}${'$'}...${'$'}${'$'}로 감싸세요.
            분수는 \\frac{a}{b}, 제곱근은 \\sqrt{x}, 지수와 아래첨자는 x^{2}, a_{n}처럼 작성하세요.
            수학 기호를 ASCII 문자로 풀어 쓰거나 코드 블록에 넣지 말고, 수식 외의 설명은 한국어로 작성하세요.
            풀이 문장은 한국어로 유지하고, 계산식은 한 줄에 하나씩 적으세요.

            문제 해결 과정은 단계별로 구어체 존댓말로 설명하세요.
            다음 순서로 작성해 주세요.
            1. 문제에서 묻는 내용을 정리
            2. 필요한 기초 개념 설명
            3. 핵심 원리나 공식 설명
            4. 문제에 개념 적용
            5. 계산 과정은 세세하게 단계별로
            6. 최종 답안
            7. 검산하는 근거 제시
            8. 마지막에 이해를 돕는 짧은 확인 질문

            이미지가 흐리거나 문제 텍스트가 잘렸거나 식·형식을 읽기 어렵다면 추측으로 말고,
            어떤 부분을 다시 촬영해야 하는지 구체적으로 알려주세요.
            여러 문제가 보이면, 중앙에 있는 문제 하나만 선 분석하고, 문제가 여러 개라면
            내용 중 어떤 선택지도 빼지 말고 안내해 주세요.
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
        streamResponse(
            conversation = requireNotNull(conversation),
            contents = Contents.of(Content.Text(question)),
            onTextChanged = onTextChanged
        )
    }

    suspend fun backendLabel(): String = withContext(runtimeDispatcher) { selectedBackendLabel }

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
            문제를 받으면 개념을 먼저 정리하고, 그다음 문제 적용을 단계별로 상세히 설명하세요.
            근거가 부족하거나 촬영 이미지가 불명확하면 답을 꾸며내지 말고 사용자에게 재촬영을 청하세요.
            학생이 추가 질문을 하면, 현재 문제의 맥락을 먼저 설명하고 기억해서 계속 도와주세요.
            모든 수학식은 표준 LaTeX 문법으로 작성하세요. 문장 안 수식은 ${'$'}...${'$'}, 독립된 수식은 ${'$'}${'$'}...${'$'}${'$'}로 감싸세요.
            분수는 \\frac{a}{b}, 제곱근은 \\sqrt{x}, 위·아래첨자는 x^{2}, a_{n}처럼 표현하세요.
            수식을 ASCII 문자로 풀어 쓰거나 코드 블록에 넣지 말고, 풀이 설명은 한국어로 작성하세요.
            계산식은 한 줄에 하나씩 쓰고, 수식 주위에 코드 블록을 사용하지 마세요.
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
        conversation.sendMessageAsync(contents).collect { message ->
            accumulated.append(message.toString())
            withContext(Dispatchers.Main.immediate) {
                onTextChanged(accumulated.toString())
            }
        }
    }

    suspend fun release() = withContext(runtimeDispatcher) {
        runCatching { conversation?.close() }
        conversation = null
        runCatching { engine?.close() }
        engine = null
        selectedBackendLabel = "미실행"
    }
}