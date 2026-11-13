package com.donian.myaitutor.ai

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import com.donian.myaitutor.model.ModelDownloadManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns one LiteRT-LM Engine and one multi-turn Conversation.
 * All native runtime operations are serialized on one dedicated thread.
 */
class GemmaAssistant(context: Context) : Assistant {
    private val appContext = context.applicationContext

    companion object {
        private const val TAG = "GemmaAssistant"

        /** 대화가 이 토큰 수를 넘으면 문맥을 초기화합니다. (N-12) */
        private const val MAX_CONVERSATION_TOKENS = 8_000

        /** Google AI Edge Gallery와 동일한 Thought 채널 이름. */
        private const val THOUGHT_CHANNEL = "thought"

        private const val EMPTY_RESPONSE_ERROR = "AI 추론 결과가 비어 있습니다."
    }

    private val runtimeDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "focus-ai-litert-runtime").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private var engine: Engine? = null

    @Volatile
    private var conversation: Conversation? = null

    /** 현재 네이티브 추론이 끝났음을 알리는 신호. 취소 완료 확인에 사용합니다. */
    @Volatile
    private var activeCompletion: CompletableDeferred<Unit>? = null

    /** 아직 네이티브 요청이 시작되기 전에 들어온 취소를 놓치지 않기 위한 플래그. */
    private val cancelRequested = AtomicBoolean(false)

    private var selectedBackendLabel: String = "미실행"
    private val generationMutex = Mutex()

    override suspend fun preWarm() {
        withContext(runtimeDispatcher) {
            try {
                ensureInitializedOnRuntimeThread()
            } catch (error: Exception) {
                Log.w(TAG, "모델 사전 로드에 실패했습니다: ${error.message}", error)
                throw error
            }
        }
    }

    override fun streamAnswer(request: AssistantRequest): Flow<AssistantStreamEvent> = channelFlow {
        require(request.textParts.isNotEmpty()) { "Gemma에 전달할 텍스트가 없습니다." }
        val completion = CompletableDeferred<Unit>()
        try {
            withContext(runtimeDispatcher) {
                generationMutex.withLock {
                    streamLocked(request, completion)
                }
            }
        } finally {
            // 엔진 초기화 실패 등으로 send가 시작되지 않아도 대기자가 풀리도록 보장합니다.
            if (!completion.isCompleted) completion.complete(Unit)
            if (activeCompletion === completion) activeCompletion = null
            cancelRequested.set(false)
        }
    }

    /**
     * generationMutex를 잡은 상태에서 실제 네이티브 스트림을 수행합니다.
     * 네이티브 Flow를 끝까지 수집해야 취소/완료 신호를 확인할 수 있습니다.
     */
    private suspend fun ProducerScope<AssistantStreamEvent>.streamLocked(
        request: AssistantRequest,
        completion: CompletableDeferred<Unit>
    ) {
        if (cancelRequested.get()) {
            send(AssistantStreamEvent.Cancelled)
            return
        }

        if (request.startsNewProblem) {
            resetConversationLocked()
        }
        ensureInitializedOnRuntimeThread()
        resetIfContextTooLongLocked()

        if (cancelRequested.get()) {
            send(AssistantStreamEvent.Cancelled)
            return
        }

        val activeConversation = requireNotNull(conversation)
        val contents = Contents.of(
            *request.textParts.map { Content.Text(it) }.toTypedArray()
        )
        val thinkingConfig = if (request.options.thinkingEnabled) {
            ThinkingConfig(true, request.options.thinkingTokenBudget)
        } else {
            // Thinking OFF 학년은 토큰 예산 인자를 전달하지 않습니다.
            ThinkingConfig(false)
        }

        activeCompletion = completion
        try {
            activeConversation.sendMessageAsync(
                contents = contents,
                maxOutputToken = request.options.maxOutputTokens,
                thinkingConfig = thinkingConfig
            ).collect { message ->
                // Thought 채널의 원문은 절대 이벤트에 담지 않습니다.
                val thoughtText = message.channels[THOUGHT_CHANNEL]
                if (!thoughtText.isNullOrBlank()) {
                    send(AssistantStreamEvent.ThoughtActivity)
                }
                val answerText = message.toString()
                if (answerText.isNotEmpty()) {
                    send(AssistantStreamEvent.Answer(answerText))
                }
            }
            send(AssistantStreamEvent.Completed)
        } catch (error: CancellationException) {
            // cancelProcess()로 네이티브 추론이 종료된 경우 또는 수집 코루틴이 취소된 경우입니다.
            runCatching { send(AssistantStreamEvent.Cancelled) }
            if (!currentCoroutineContext().isActive) {
                // 수집만 취소된 경우에도 네이티브 추론을 멈춥니다.
                runCatching { activeConversation.cancelProcess() }
            }
        } catch (error: Exception) {
            runCatching {
                send(
                    AssistantStreamEvent.Error(
                        error.localizedMessage ?: "AI 추론 중 오류가 발생했습니다."
                    )
                )
            }
        } finally {
            if (activeCompletion === completion) activeCompletion = null
            completion.complete(Unit)
        }
    }

    override suspend fun awaitGenerationStopped(timeoutMillis: Long): Boolean {
        val active = activeCompletion
        if (active == null) {
            // 네이티브 요청이 아직 시작되지 않았으므로 다음 요청을 막을 이유가 없습니다.
            cancelRequested.set(false)
            return true
        }
        val stopped = withTimeoutOrNull(timeoutMillis) { active.await() } != null
        if (stopped) cancelRequested.set(false)
        return stopped
    }

    override suspend fun analyzeImage(
        ocrText: String,
        subject: String,
        onTextChanged: suspend (String) -> Unit
    ) {
        require(ocrText.isNotBlank()) { "인식된 문제 텍스트가 없습니다." }
        collectLegacy(
            request = AssistantRequest(
                textParts = listOf(
                    "[OCR 인식 텍스트]\n$ocrText",
                    analyzeImagePrompt(subject, TutorGrade.ELEMENTARY)
                ),
                startsNewProblem = true
            ),
            onTextChanged = onTextChanged
        )
    }

    override suspend fun askFollowUp(
        question: String,
        subject: String,
        onTextChanged: suspend (String) -> Unit
    ) {
        require(question.isNotBlank()) { "질문 내용을 입력해 주세요." }
        collectLegacy(
            request = AssistantRequest(
                textParts = listOf(
                    followUpPrompt(question, subject, TutorGrade.ELEMENTARY)
                ),
                startsNewProblem = false
            ),
            onTextChanged = onTextChanged
        )
    }

    /** 기존 콜백 API를 새 스트리밍 API 위에서 동작하게 유지합니다. */
    private suspend fun collectLegacy(
        request: AssistantRequest,
        onTextChanged: suspend (String) -> Unit
    ) {
        val accumulated = StringBuilder()
        var received = false
        streamAnswer(request).collect { event ->
            when (event) {
                is AssistantStreamEvent.Answer -> {
                    accumulated.append(event.text)
                    received = true
                    withContext(Dispatchers.Main.immediate) {
                        onTextChanged(accumulated.toString())
                    }
                }
                is AssistantStreamEvent.Error -> throw IllegalStateException(event.message)
                AssistantStreamEvent.Cancelled -> throw CancellationException("AI 추론이 취소되었습니다.")
                AssistantStreamEvent.Completed, AssistantStreamEvent.ThoughtActivity -> Unit
            }
        }
        if (!received) throw IllegalStateException(EMPTY_RESPONSE_ERROR)
    }

    override suspend fun backendLabel(): String = withContext(runtimeDispatcher) { selectedBackendLabel }

    override suspend fun getTokenCount(): Int = withContext(runtimeDispatcher) {
        runCatching { conversation?.getTokenCount() ?: 0 }.getOrElse { 0 }
    }

    /**
     * 진행 중인 답변 생성을 중단합니다.
     * 런타임 스레드는 생성 중이라 대기할 수 없으므로, 네이티브 취소 플래그만
     * 즉시 세팅하고 반환합니다. 실제 종료 확인은 [awaitGenerationStopped]가 담당합니다.
     */
    override fun cancelGeneration() {
        cancelRequested.set(true)
        runCatching { conversation?.cancelProcess() }
    }

    /**
     * 새 문제를 풀기 전에 이전 대화 문맥을 초기화합니다.
     * 엔진은 유지하고 대화만 새로 생성합니다. (R-02)
     */
    override suspend fun resetConversation() = withContext(runtimeDispatcher) {
        generationMutex.withLock {
            resetConversationLocked()
        }
    }

    /** generationMutex를 이미 잡은 상태에서 Conversation만 교체합니다. */
    private suspend fun resetConversationLocked() {
        runCatching { conversation?.cancelProcess() }
        runCatching { conversation?.close() }
        conversation = null
        val currentEngine = engine ?: return
        conversation = createConversation(currentEngine)
    }

    /**
     * 대화 토큰 수가 상한을 넘으면 문맥을 비워 재답변 오류를 방지합니다. (N-12)
     */
    private suspend fun resetIfContextTooLongLocked() {
        val current = conversation ?: return
        val tokens = runCatching { current.getTokenCount() }.getOrElse { return }
        if (tokens < MAX_CONVERSATION_TOKENS) return
        Log.i(TAG, "대화 토큰 수($tokens)가 상한($MAX_CONVERSATION_TOKENS)을 넘어 문맥을 초기화합니다.")
        resetConversationLocked()
    }

    private fun createConversation(currentEngine: Engine): Conversation =
        currentEngine.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(SystemInstruction),
                samplerConfig = SamplerConfig(
                    topK = 20,
                    topP = 0.9,
                    temperature = 0.35
                ),
                // null이면 모델 메타데이터의 기본 채널 설정을 사용합니다.
                channels = null,
                maxOutputToken = DEFAULT_MAX_OUTPUT_TOKENS
            )
        )

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
                val candidateConversation = createConversation(candidate)
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

    override suspend fun release() = withContext(runtimeDispatcher) {
        runCatching { conversation?.cancelProcess() }
        generationMutex.withLock {
            runCatching { conversation?.close() }
            conversation = null
            runCatching { engine?.close() }
            engine = null
            selectedBackendLabel = "미실행"
        }
    }
}
