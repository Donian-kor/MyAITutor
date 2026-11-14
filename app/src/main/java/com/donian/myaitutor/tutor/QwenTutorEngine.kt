package com.donian.myaitutor.tutor

import android.util.Log
import com.donian.myaitutor.ai.Assistant
import com.donian.myaitutor.ai.AssistantRequest
import com.donian.myaitutor.ai.AssistantStreamEvent
import com.donian.myaitutor.ai.AssistantStreamOptions
import com.donian.myaitutor.ai.QwenAssistant
import com.donian.myaitutor.ai.QwenContextBudget
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
 *
 * 2,048 컨텍스트 예산을 넘지 않도록 입력을 [QwenContextBudget]로 축약해 전달합니다.
 *
 * @param contextOverflowMessage 예산 부족으로 입력을 담지 못한 경우 사용자에게 보여줄 안내 문구.
 */
class QwenTutorEngine(
    private val assistant: Assistant,
    private val contextOverflowMessage: String
) : LlmInferenceEngine {

    override fun stream(request: TutorRequest): Flow<TutorStreamChunk> = flow {
        require(request.mode != TutorMode.FREE_CHAT) { "Qwen은 자유 대화 모드를 지원하지 않습니다." }
        val promptParts = when {
            request.options.isFollowUp && !request.isStandaloneQuestion ->
                listOf(followUpPrompt(request.text, request.subject, request.grade))
            request.isStandaloneQuestion ->
                listOf(standaloneQuestionPrompt(request.text, request.subject, request.grade))
            else -> listOf(
                "[OCR 인식 텍스트]\n${request.text}",
                analyzeImagePrompt(request.subject, request.grade)
            )
        }

        val maxOutputTokens = request.options.maxOutputTokens
            .coerceAtMost(QwenAssistant.QWEN_DEFAULT_OUTPUT_TOKENS)
        // 시스템 지시문은 Conversation이 이미 보유하므로 입력 예산에서 먼저 예약한다.
        val inputBudget = QwenContextBudget.inputBudgetTokens(
            contextTokens = QwenAssistant.QWEN_MAX_CONTEXT_TOKENS,
            outputTokens = maxOutputTokens,
            systemInstructionTokens = QwenContextBudget.estimateTokens(SystemInstruction)
        )
        val budget = QwenContextBudget.fit(promptParts, inputBudget)
        if (budget.overflow) {
            emit(TutorStreamChunk.Error(contextOverflowMessage))
            return@flow
        }
        if (budget.truncated) {
            // 진단 로그에 문제 원문은 남기지 않고 축약 여부만 기록한다.
            Log.i(TAG, "Qwen 입력을 ${inputBudget}토큰 예산에 맞춰 축약했습니다.")
        }

        val assistantRequest = AssistantRequest(
            textParts = budget.parts,
            startsNewProblem = request.isStandaloneQuestion || !request.options.isFollowUp,
            options = AssistantStreamOptions(
                maxOutputTokens = maxOutputTokens,
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

    private companion object {
        const val TAG = "QwenTutorEngine"
    }
}