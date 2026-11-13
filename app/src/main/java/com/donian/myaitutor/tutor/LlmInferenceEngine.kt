package com.donian.myaitutor.tutor

import com.donian.myaitutor.ai.Assistant
import com.donian.myaitutor.ai.AssistantRequest
import com.donian.myaitutor.ai.AssistantStreamEvent
import com.donian.myaitutor.ai.AssistantStreamOptions
import com.donian.myaitutor.ai.DEFAULT_MAX_OUTPUT_TOKENS
import com.donian.myaitutor.ai.DEFAULT_THINKING_TOKEN_BUDGET
import com.donian.myaitutor.ai.ChatSystemInstruction
import com.donian.myaitutor.ai.SystemInstruction
import com.donian.myaitutor.ai.TutorGrade
import com.donian.myaitutor.ai.analyzeImagePrompt
import com.donian.myaitutor.ai.followUpPrompt
import com.donian.myaitutor.ai.freeChatPrompt
import com.donian.myaitutor.ai.standaloneQuestionPrompt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 앱의 두 가지 사용 모드.
 * 전환 시 Conversation을 새로 만들어 시스템 지시문을 교체합니다.
 */
enum class TutorMode {
    /** 사진·OCR 기반 과목 학습과 학습 질문. */
    LEARNING,

    /** 과목과 무관한 자유 대화(고민·일상·잡담). */
    FREE_CHAT;

    /** 이 모드에서 사용할 시스템 지시문. */
    val systemInstruction: String
        get() = when (this) {
            LEARNING -> SystemInstruction
            FREE_CHAT -> ChatSystemInstruction
        }
}

/**
 * 요청별 추론 설정. Conversation이나 Engine을 다시 만들지 않고 그대로 전달합니다.
 */
data class InferenceOptions(
    val thinkingEnabled: Boolean,
    val thinkingTokenBudget: Int,
    val maxOutputTokens: Int,
    val isFollowUp: Boolean
)

/**
 * OCR 최초 문제 풀이와 후속 질문을 모두 처리하는 튜터 추론 요청.
 *
 * @param text OCR로 추출한 문제 텍스트 또는 사용자의 후속 질문.
 */
data class TutorRequest(
    val text: String,
    val subject: String,
    val grade: TutorGrade,
    val options: InferenceOptions,
    val isStandaloneQuestion: Boolean = false,
    val mode: TutorMode = TutorMode.LEARNING
)

/** 학년별 Thinking/출력 토큰 설정을 한 곳에서 만듭니다. */
fun inferenceOptionsFor(grade: TutorGrade, isFollowUp: Boolean): InferenceOptions =
    InferenceOptions(
        thinkingEnabled = grade.usesThinking,
        thinkingTokenBudget = if (grade.usesThinking) DEFAULT_THINKING_TOKEN_BUDGET else 0,
        maxOutputTokens = DEFAULT_MAX_OUTPUT_TOKENS,
        isFollowUp = isFollowUp
    )

/**
 * 튜터 추론 엔진 공통 인터페이스. 스트리밍은 [TutorStreamChunk] Flow로 통일합니다.
 */
interface LlmInferenceEngine {
    fun stream(request: TutorRequest): Flow<TutorStreamChunk>

    /** 네이티브 취소가 실제로 끝날 때까지 기다립니다. */
    fun cancelGeneration()
    suspend fun awaitGenerationStopped(timeoutMillis: Long): Boolean

    /** 엔진은 유지하고 Conversation을 교체합니다. 모드 전환 시 지시문을 함께 바꿉니다. */
    suspend fun resetConversation(systemInstruction: String = SystemInstruction)
    suspend fun backendLabel(): String
    suspend fun getTokenCount(): Int
    suspend fun preWarm()
    suspend fun release()
}

/**
 * 기존 [com.donian.myaitutor.ai.GemmaAssistant] 인스턴스를 그대로 감싸는 어댑터.
 * Engine/Conversation을 새로 만들지 않습니다.
 */
class RealGemmaTutorEngine(
    private val assistant: Assistant
) : LlmInferenceEngine {

    override fun stream(request: TutorRequest): Flow<TutorStreamChunk> = flow {
        val textParts = when {
            request.mode == TutorMode.FREE_CHAT ->
                listOf(freeChatPrompt(request.text, request.grade))
            request.options.isFollowUp && !request.isStandaloneQuestion ->
                listOf(followUpPrompt(request.text, request.subject, request.grade))
            request.isStandaloneQuestion ->
                listOf(standaloneQuestionPrompt(request.text, request.subject, request.grade))
            else -> listOf(
                "[OCR 인식 텍스트]\n${request.text}",
                analyzeImagePrompt(request.subject, request.grade)
            )
        }
        val assistantRequest = AssistantRequest(
            textParts = textParts,
            // 자유 대화는 매 턴 같은 대화를 이어가므로 새 문제를 시작하지 않습니다.
            startsNewProblem = request.mode == TutorMode.LEARNING &&
                (request.isStandaloneQuestion || !request.options.isFollowUp),
            options = AssistantStreamOptions(
                maxOutputTokens = request.options.maxOutputTokens,
                thinkingEnabled = request.options.thinkingEnabled,
                thinkingTokenBudget = request.options.thinkingTokenBudget
            )
        )
        try {
            assistant.streamAnswer(assistantRequest).collect { event ->
                when (event) {
                    AssistantStreamEvent.ThoughtActivity ->
                        emit(TutorStreamChunk.ThoughtActivity)
                    is AssistantStreamEvent.Answer ->
                        emit(TutorStreamChunk.Answer(event.text))
                    AssistantStreamEvent.Completed ->
                        emit(TutorStreamChunk.Completed)
                    AssistantStreamEvent.Cancelled ->
                        emit(TutorStreamChunk.Cancelled)
                    is AssistantStreamEvent.Error ->
                        emit(TutorStreamChunk.Error(event.message))
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            emit(
                TutorStreamChunk.Error(
                    error.localizedMessage ?: "AI 추론 중 오류가 발생했습니다."
                )
            )
        }
    }

    override fun cancelGeneration() = assistant.cancelGeneration()

    override suspend fun awaitGenerationStopped(timeoutMillis: Long): Boolean =
        assistant.awaitGenerationStopped(timeoutMillis)

    override suspend fun resetConversation(systemInstruction: String) =
        assistant.resetConversation(systemInstruction)
    override suspend fun backendLabel(): String = assistant.backendLabel()
    override suspend fun getTokenCount(): Int = assistant.getTokenCount()
    override suspend fun preWarm() = assistant.preWarm()
    override suspend fun release() = assistant.release()
}

/**
 * 실제 모델을 초기화하지 않는 독립 테스트 엔진.
 * Thinking ON/OFF, Thought 활동, 실시간 Answer, 단계 변화, 완료/오류/취소/재시작을 검증할 수 있습니다.
 */
class FakeGemmaTutorEngine(
    private val chunkProvider: (TutorRequest) -> List<TutorStreamChunk> = {
        listOf(
            TutorStreamChunk.Answer("테스트 해설"),
            TutorStreamChunk.Completed
        )
    },
    var awaitStoppedResult: Boolean = true,
    var backend: String = "FAKE",
    var tokenCount: Int = 0,
    var tokenCountFailure: Boolean = false
) : LlmInferenceEngine {

    val requests = mutableListOf<TutorRequest>()
    var cancelCalls = 0
    var resetConversationCalls = 0
    var preWarmCalls = 0
    var releaseCalls = 0

    /** 마지막 [resetConversation]에 전달된 시스템 지시문. 모드 전환 검증에 사용합니다. */
    var lastSystemInstruction: String? = null
        private set

    /** 테스트에서 스트림을 원하는 시점까지 붙잡아 둘 때 사용합니다. */
    var gate: CompletableDeferred<Unit>? = null

    /** 테스트에서 취소 확인을 원하는 시점까지 지연시킬 때 사용합니다. */
    var stopGate: CompletableDeferred<Unit>? = null

    @Volatile
    var streaming: Boolean = false
        private set

    override fun stream(request: TutorRequest): Flow<TutorStreamChunk> = flow {
        requests += request
        streaming = true
        try {
            gate?.await()
            chunkProvider(request).forEach { chunk ->
                emit(chunk)
                delay(0)
            }
        } finally {
            streaming = false
        }
    }

    override fun cancelGeneration() {
        cancelCalls++
    }

    override suspend fun awaitGenerationStopped(timeoutMillis: Long): Boolean {
        stopGate?.await()
        return awaitStoppedResult
    }

    override suspend fun resetConversation(systemInstruction: String) {
        resetConversationCalls++
        lastSystemInstruction = systemInstruction
    }

    override suspend fun backendLabel(): String = backend
    override suspend fun getTokenCount(): Int {
        if (tokenCountFailure) throw IllegalStateException("토큰 조회 실패")
        return tokenCount
    }
    override suspend fun preWarm() {
        preWarmCalls++
    }

    override suspend fun release() {
        releaseCalls++
    }
}
