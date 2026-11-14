package com.donian.myaitutor.ai

import android.app.ActivityManager
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
import com.donian.myaitutor.model.QwenModelDownloadManager
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

class QwenAssistant(context: Context) : Assistant {
    private val appContext = context.applicationContext

    companion object {
        private const val TAG = "QwenAssistant"
        private const val THOUGHT_CHANNEL = "thought"
        private const val EMPTY_RESPONSE_ERROR = "AI 추론 결과가 비어 있습니다."
        internal const val QWEN_MAX_CONTEXT_TOKENS: Int = 2_048
        internal const val QWEN_MAX_CONVERSATION_TOKENS: Int = 1_536
        internal const val QWEN_DEFAULT_OUTPUT_TOKENS: Int = 512
        private const val CPU_FALLBACK_MIN_AVAIL_BYTES: Long = 5_368_709_120L
        private const val PEAK_RSS_LIMIT_BYTES: Long = 5_905_580_032L // 5.5GB
    }

    private val runtimeDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "focus-ai-qwen-runtime").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private var engine: Engine? = null
    @Volatile
    private var conversation: Conversation? = null
    @Volatile
    private var activeCompletion: CompletableDeferred<Unit>? = null
    private val cancelRequested = AtomicBoolean(false)
    private var selectedBackendLabel: String = "미실행"
    private val generationMutex = Mutex()

    override suspend fun preWarm() {
        withContext(runtimeDispatcher) {
            try {
                ensureInitializedOnRuntimeThread()
            } catch (error: Exception) {
                Log.w(TAG, "Qwen 모델 사전 로드에 실패했습니다: ${error.message}", error)
                throw error
            }
        }
    }

    override fun streamAnswer(request: AssistantRequest): Flow<AssistantStreamEvent> = channelFlow {
        require(request.textParts.isNotEmpty()) { "Qwen에 전달할 텍스트가 없습니다." }
        val completion = CompletableDeferred<Unit>()
        try {
            withContext(runtimeDispatcher) {
                generationMutex.withLock {
                    streamLocked(request, completion)
                }
            }
        } finally {
            if (!completion.isCompleted) completion.complete(Unit)
            if (activeCompletion === completion) activeCompletion = null
            cancelRequested.set(false)
        }
    }

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
        // Qwen은 비-Thinking 모델: ThinkingConfig(false)를 명시적으로 전달.
        val thinkingConfig = ThinkingConfig(false)

        activeCompletion = completion
        try {
            activeConversation.sendMessageAsync(
                contents = contents,
                maxOutputToken = request.options.maxOutputTokens.coerceAtMost(QWEN_DEFAULT_OUTPUT_TOKENS).takeIf { it > 0 }
                    ?: QWEN_DEFAULT_OUTPUT_TOKENS,
                thinkingConfig = thinkingConfig
            ).collect { message ->
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
            runCatching { send(AssistantStreamEvent.Cancelled) }
            if (!currentCoroutineContext().isActive) {
                runCatching { activeConversation.cancelProcess() }
            }
        } catch (error: Exception) {
            runCatching {
                send(AssistantStreamEvent.Error(error.localizedMessage ?: "AI 추론 중 오류가 발생했습니다."))
            }
        } finally {
            if (activeCompletion === completion) activeCompletion = null
            completion.complete(Unit)
        }
    }

    override suspend fun awaitGenerationStopped(timeoutMillis: Long): Boolean {
        val active = activeCompletion
        if (active == null) {
            cancelRequested.set(false)
            return true
        }
        val stopped = withTimeoutOrNull(timeoutMillis) { active.await() } != null
        if (stopped) cancelRequested.set(false)
        return stopped
    }

    override suspend fun analyzeImage(ocrText: String, subject: String, onTextChanged: suspend (String) -> Unit) {
        require(ocrText.isNotBlank()) { "인식된 문제 텍스트가 없습니다." }
        collectLegacy(
            request = AssistantRequest(
                textParts = listOf("[OCR 인식 텍스트]\n$ocrText", analyzeImagePrompt(subject, TutorGrade.ELEMENTARY)),
                startsNewProblem = true
            ),
            onTextChanged = onTextChanged
        )
    }

    override suspend fun askFollowUp(question: String, subject: String, onTextChanged: suspend (String) -> Unit) {
        require(question.isNotBlank()) { "질문 내용을 입력해 주세요." }
        collectLegacy(
            request = AssistantRequest(
                textParts = listOf(followUpPrompt(question, subject, TutorGrade.ELEMENTARY)),
                startsNewProblem = false
            ),
            onTextChanged = onTextChanged
        )
    }

    private suspend fun collectLegacy(request: AssistantRequest, onTextChanged: suspend (String) -> Unit) {
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

    override fun cancelGeneration() {
        cancelRequested.set(true)
        runCatching { conversation?.cancelProcess() }
    }

    override suspend fun resetConversation(systemInstruction: String) =
        withContext(runtimeDispatcher) {
            generationMutex.withLock {
                currentSystemInstruction = systemInstruction
                resetConversationLocked()
            }
        }

    @Volatile
    private var currentSystemInstruction: String = SystemInstruction

    private fun resetConversationLocked() {
        runCatching { conversation?.cancelProcess() }
        runCatching { conversation?.close() }
        conversation = null
        val currentEngine = engine ?: return
        conversation = createConversation(currentEngine)
    }

    private suspend fun resetIfContextTooLongLocked() {
        val current = conversation ?: return
        val tokens = runCatching { current.getTokenCount() }.getOrElse { return }
        if (tokens < QWEN_MAX_CONVERSATION_TOKENS) return
        Log.i(TAG, "Qwen 대화 토큰 수($tokens)가 상한($QWEN_MAX_CONVERSATION_TOKENS)을 넘어 문맥을 초기화합니다.")
        resetConversationLocked()
    }

    private fun createConversation(currentEngine: Engine): Conversation =
        currentEngine.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(currentSystemInstruction),
                samplerConfig = SamplerConfig(
                    topK = 20,
                    topP = 0.8,
                    temperature = 0.7
                ),
                channels = null,
                maxOutputToken = QWEN_DEFAULT_OUTPUT_TOKENS,
                chatTemplate = Qwen3ChatTemplate.TEMPLATE
            )
        )

    private suspend fun ensureInitializedOnRuntimeThread() {
        if (engine != null && conversation != null) return

        // 크기만으로는 손상된 파일을 걸러낼 수 없으므로 저장된 SHA-256 검증 완료 상태까지 요구한다.
        val downloadManager = QwenModelDownloadManager(appContext)
        require(downloadManager.isFileVerified()) {
            "Qwen3-4B-Instruct-2507 모델 검증이 완료되지 않았습니다. 설정에서 다운로드와 검증을 완료해 주세요."
        }
        val modelFile = downloadManager.modelFile

        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val availMem = memInfo.availMem

        val configs = mutableListOf<Pair<String, EngineConfig>>()
        configs += "GPU" to EngineConfig(
            modelPath = modelFile.absolutePath,
            backend = Backend.GPU(),
            maxNumTokens = QWEN_MAX_CONTEXT_TOKENS,
            cacheDir = appContext.cacheDir.absolutePath
        )
        if (availMem >= CPU_FALLBACK_MIN_AVAIL_BYTES) {
            configs += "CPU" to EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU(),
                maxNumTokens = QWEN_MAX_CONTEXT_TOKENS,
                cacheDir = appContext.cacheDir.absolutePath
            )
        }

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
                if (initialized) runCatching { candidate.close() }
            }
        }

        if (availMem < CPU_FALLBACK_MIN_AVAIL_BYTES) {
            throw IllegalStateException("Qwen3 CPU 폴백이 차단되었습니다 (가용 메모리 ${availMem} bytes < ${CPU_FALLBACK_MIN_AVAIL_BYTES}).", lastError)
        }
        throw IllegalStateException(
            "Qwen3-4B-Instruct-2507가 GPU와 CPU에서 모두 초기화하지 못했습니다. 기기 메모리와 모델 파일을 확인해 주세요.",
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