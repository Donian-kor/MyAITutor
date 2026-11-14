package com.donian.myaitutor.ai

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
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

        /**
         * 모델 파일 메타데이터가 정한 **대화 입력 토큰 상한**.
         *
         * `.litertlm` 파일에 박힌 값으로, `EngineConfig.maxNumTokens`를 올려도 이 값은 바뀌지 않는다.
         * 실기기 실측: 입력 2,707토큰에서 `FAILED_PRECONDITION (status code 3)`,
         * "Exceeding the maximum number of tokens allowed: 2707 >= 2048".
         * 따라서 [QwenContextBudget]의 입력 예산은 이 값을 기준으로 계산해야 한다.
         */
        internal const val QWEN_MAX_CONTEXT_TOKENS: Int = 2_048

        /** 대화 전체 토큰 수가 이 값에 도달하면 문맥을 초기화한다. 출력 예약분만 제외한 값이다. */
        internal const val QWEN_MAX_CONVERSATION_TOKENS: Int = 1_024

        /**
         * 기본 출력 상한.
         *
         * 입력 한도(2,048)와 별개로, 출력은 512로는 풀이가 중간에 잘려 1,024로 올렸다.
         * 입력 예산은 2,048 − 1,024 − 514(시스템 지시문) − 64(여유) = 446토큰이다.
         */
        internal const val QWEN_DEFAULT_OUTPUT_TOKENS: Int = 1_024
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
                send(
                    AssistantStreamEvent.Error(
                        LiteRtErrorMapper.toUserMessage(error.localizedMessage)
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
            cancelRequested.set(false)
            return true
        }
        val stopped = withTimeoutOrNull(timeoutMillis) { active.await() } != null
        if (stopped) cancelRequested.set(false)
        return stopped
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

    /**
     * Qwen은 학습 전용이므로 최소 학습 지시문을 사용한다.
     *
     * 긴 안전 규칙은 앱 진입 단계(과목 선택·자유 대화 차단)에서 이미 걸러지므로
     * 모델에는 역할만 부여해 입력 예산을 확보한다.
     */
    @Volatile
    private var currentSystemInstruction: String = LearningSystemInstruction

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
            Log.i(
                TAG,
                "Qwen 엔진 초기화: backend=$backendName, maxNumTokens=${config.maxNumTokens}, " +
                    "outputToken=$QWEN_DEFAULT_OUTPUT_TOKENS, conversationToken=$QWEN_MAX_CONVERSATION_TOKENS"
            )
            val candidate = Engine(config)
            var initialized = false
            try {
                candidate.initialize()
                initialized = true
                val candidateConversation = createConversation(candidate)
                engine = candidate
                conversation = candidateConversation
                selectedBackendLabel = backendName
                logRssUsageIfExcessive(backendName)
                return
            } catch (error: Exception) {
                lastError = error
                if (initialized) runCatching { candidate.close() }
            }
        }

        if (availMem < CPU_FALLBACK_MIN_AVAIL_BYTES) {
            throw CpuFallbackBlockedException(
                "Qwen3 CPU 폴백이 차단되었습니다 (가용 메모리 ${availMem} bytes < ${CPU_FALLBACK_MIN_AVAIL_BYTES}).",
                lastError
            )
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

    /**
     * 초기화 직후 실제 점유 메모리가 [PEAK_RSS_LIMIT_BYTES]를 넘으면 경고만 남깁니다.
     *
     * 기기별로 GPU 메모리 집계 방식이 달라 로드를 실패시키지 않고 관측값 확보를 우선합니다.
     */
    private fun logRssUsageIfExcessive(backendName: String) {
        val usedBytes = totalPssBytes()
        if (usedBytes <= 0L) return
        if (usedBytes > PEAK_RSS_LIMIT_BYTES) {
            Log.w(
                TAG,
                "$backendName 백엔드 초기화 후 점유 메모리가 한도($PEAK_RSS_LIMIT_BYTES bytes)를 " +
                    "넘었습니다: $usedBytes bytes"
            )
        } else {
            Log.i(TAG, "$backendName 백엔드 초기화 후 점유 메모리: $usedBytes bytes")
        }
    }

    private fun totalPssBytes(): Long =
        runCatching {
            val info = Debug.MemoryInfo()
            Debug.getMemoryInfo(info)
            info.totalPss.toLong() * 1024L
        }.getOrDefault(0L)
}

/**
 * Qwen CPU 폴백이 가용 메모리 부족으로 차단되었음을 나타냅니다.
 * UI는 이 타입을 확인해 준비된 안내 문구로 사용자에게 전달합니다.
 */
class CpuFallbackBlockedException(
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)