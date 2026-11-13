package com.donian.myaitutor.tutor

import com.donian.myaitutor.ai.Assistant
import com.donian.myaitutor.ai.AssistantRequest
import com.donian.myaitutor.ai.AssistantStreamEvent
import com.donian.myaitutor.ai.AssistantStreamOptions
import com.donian.myaitutor.ai.DEFAULT_MAX_OUTPUT_TOKENS
import com.donian.myaitutor.ai.DEFAULT_THINKING_TOKEN_BUDGET
import com.donian.myaitutor.ai.SystemInstruction
import com.donian.myaitutor.ai.TutorGrade
import com.donian.myaitutor.ai.analyzeImagePrompt
import com.donian.myaitutor.ai.followUpPrompt
import com.donian.myaitutor.ai.standaloneQuestionPrompt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Qwen3 전용 튜터 엔진. Qwen은 학습 모드 전용이므로 자유 대화 프롬프트를 생성하지 않습니다.
 */
class QwenTutorEngine(
    private val assistant: Assistant
) : LlmInferenceEngine {

    override fun stream(request: TutorRequest): Flow<TutorStreamChunk> = flow {
        require(request.mode != TutorMode.FREE_CHAT) { "Qwen은 자유 대화 모드를 지원하지 않습니다." }
        val textParts = when {
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
            startsNewProblem = request.isStandaloneQuestion || !request.options.isFollowUp,
            options = AssistantStreamOptions(
                maxOutputTokens = request.options.maxOutputTokens.coerceAtMost(512),
                thinkingEnabled = false,
                thinkingTokenBudget = 0
            )
        )
        try {
            assistant.streamAnswer(assistantRequest).collect { event ->
                when (event) {
                    AssistantStreamEvent.ThoughtActivity -> emit(TutorStreamChunk.ThoughtActivity)
                    is AssistantStreamEvent.Answer -> emit(TutorStreamChunk.Answer(event.text))
                    AssistantStreamEvent.Completed -> emit(TutorStreamChunk.Completed)
                    AssistantStreamEvent.Cancelled -> emit(TutorStreamChunk.Cancelled)
                    is AssistantStreamEvent.Error -> emit(TutorStreamChunk.Error(event.message))
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            emit(TutorStreamChunk.Error(error.localizedMessage ?: "AI 추론 중 오류가 발생했습니다."))
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