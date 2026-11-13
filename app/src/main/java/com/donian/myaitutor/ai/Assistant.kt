package com.donian.myaitutor.ai

import kotlinx.coroutines.flow.Flow

/**
 * 한 번의 Gemma 추론 요청. 프롬프트 조립은 호출부(튜터 엔진)가 끝낸 상태로 전달합니다.
 *
 * @param textParts Gemma에 순서대로 전달할 Content.Text 조각들.
 * @param startsNewProblem true이면 기존 대화를 닫고 새 문제용 Conversation으로 교체합니다.
 */
data class AssistantRequest(
    val textParts: List<String>,
    val startsNewProblem: Boolean = false,
    val options: AssistantStreamOptions = AssistantStreamOptions()
)

/** 요청별 추론 옵션. Conversation/Engine을 다시 만들지 않고 그대로 전달합니다. */
data class AssistantStreamOptions(
    val maxOutputTokens: Int = DEFAULT_MAX_OUTPUT_TOKENS,
    val thinkingEnabled: Boolean = false,
    val thinkingTokenBudget: Int = 0
)

/**
 * SDK 종속성을 제거한 스트림 이벤트.
 * Thought 채널의 원문은 이 이벤트에 담지 않습니다.
 */
sealed interface AssistantStreamEvent {
    data object ThoughtActivity : AssistantStreamEvent
    data class Answer(val text: String) : AssistantStreamEvent
    data object Completed : AssistantStreamEvent
    data object Cancelled : AssistantStreamEvent
    data class Error(val message: String) : AssistantStreamEvent
}

/**
 * Gemma 런타임과의 상호작용을 추상화한 인터페이스.
 * 테스트에서는 가짜 구현을 주입할 수 있습니다.
 */
interface Assistant {
    /**
     * 새 스트리밍 API. Thought/Answer 채널을 분리하고 요청별 Thinking·출력 토큰 설정을 적용합니다.
     */
    fun streamAnswer(request: AssistantRequest): Flow<AssistantStreamEvent>

    /** 취소 후 네이티브 추론이 실제로 끝날 때까지 기다립니다. */
    suspend fun awaitGenerationStopped(timeoutMillis: Long): Boolean

    /** 엔진은 유지하고 활성 Conversation만 안전하게 교체합니다. */
    suspend fun resetConversation()

    // 기존 호출부 호환 API. 스트리밍 API로도 동일한 요청을 수행할 수 있습니다.
    suspend fun analyzeImage(ocrText: String, subject: String, onTextChanged: suspend (String) -> Unit)
    suspend fun askFollowUp(question: String, onTextChanged: suspend (String) -> Unit)
    suspend fun backendLabel(): String
    suspend fun getTokenCount(): Int
    fun cancelGeneration()
    suspend fun preWarm()
    suspend fun release()
}
