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
        resetConversation()
        ensureInitializedOnRuntimeThread()

        val prompt = analyzeImagePrompt(subject)

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
        val followUpPrompt = followUpPrompt(question)
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

    /**
     * 새 문제를 풀기 전에 이전 대화 문맥을 초기화합니다.
     * 엔진은 유지하고 대화만 새로 생성합니다. (R-02)
     */
    suspend fun resetConversation() = withContext(runtimeDispatcher) {
        generationMutex.withLock {
            runCatching { conversation?.cancelProcess() }
            runCatching { conversation?.close() }
            conversation = null
            val currentEngine = engine
            if (currentEngine != null) {
                conversation = currentEngine.createConversation(
                    ConversationConfig(
                        systemInstruction = Contents.of(SystemInstruction),
                        samplerConfig = SamplerConfig(
                            topK = 20,
                            topP = 0.9,
                            temperature = 0.35
                        ),
                        maxOutputToken = 2048
                    )
                )
            }
        }
    }

    private suspend fun ensureInitializedOnRuntimeThread() {
        if (engine != null && conversation != null) return

        val modelFile = ModelDownloadManager(appContext).modelFile
        require(modelFile.isFile && modelFile.length() >= 3_500_000_000L) {
            "Gemma 4 E4B 모델이 설치되지 않았습니다. 설정에서 모델을 다운로드해 주세요."
        }


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
                        systemInstruction = Contents.of(SystemInstruction),
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