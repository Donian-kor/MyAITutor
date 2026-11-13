package com.donian.myaitutor.tutor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorStreamParserTest {

    @Test
    fun `사진 인식 시작은 OCR 단계로 표시한다`() {
        val parser = TutorStreamParser()

        val state = parser.startOcr()

        assertTrue(state.isGenerating)
        assertEquals(TutorProgressStage.OCR, state.progressStage)
        assertTrue(state.progressMessages.contains("문제 사진 인식 시작"))
    }

    @Test
    fun `OCR 완료와 분석 시작은 ANALYZING 단계로 이동한다`() {
        val parser = TutorStreamParser()
        parser.startOcr()

        val afterOcr = parser.onOcrCompleted()
        assertEquals(TutorProgressStage.ANALYZING, afterOcr.progressStage)
        assertTrue(afterOcr.isGenerating)
        assertTrue(afterOcr.progressMessages.contains("OCR 문제 인식 완료"))

        val afterAnalysis = parser.onAnalysisStarted()
        assertEquals(TutorProgressStage.ANALYZING, afterAnalysis.progressStage)
        assertTrue(afterAnalysis.progressMessages.contains("AI 분석 시작"))
    }

    @Test
    fun `ThoughtActivity는 원문 없이 진행 상태만 갱신한다`() {
        val parser = TutorStreamParser()
        parser.startFollowUp()

        val result = parser.onChunk(TutorStreamChunk.ThoughtActivity)

        assertNull(result.answerTextDelta)
        assertTrue(result.state.isGenerating)
        assertEquals(TutorProgressStage.ANALYZING, result.state.progressStage)
        assertTrue(result.state.progressMessages.contains("AI가 문제를 분석하는 중"))

        // 같은 Thought 활동이 반복되어도 진행 메시지는 한 번만 기록합니다.
        val second = parser.onChunk(TutorStreamChunk.ThoughtActivity)
        assertEquals(
            1,
            second.state.progressMessages.count { it == "AI가 문제를 분석하는 중" }
        )
    }

    @Test
    fun `Answer 조각은 증가분으로 누적되고 EXPLAINING 단계로 전이한다`() {
        val parser = TutorStreamParser()
        parser.startFollowUp()

        val first = parser.onChunk(TutorStreamChunk.Answer("첫째 "))
        assertEquals("첫째 ", first.answerTextDelta)
        assertEquals(TutorProgressStage.EXPLAINING, first.state.progressStage)
        assertTrue(first.state.progressMessages.contains("해설 생성 시작"))

        val second = parser.onChunk(TutorStreamChunk.Answer("둘째"))
        assertEquals("둘째", second.answerTextDelta)
        assertEquals(
            1,
            second.state.progressMessages.count { it == "해설 생성 시작" }
        )
    }

    @Test
    fun `Answer의 특수 토큰은 UI로 노출되지 않는다`() {
        val parser = TutorStreamParser()
        parser.startFollowUp()

        val result = parser.onChunk(
            TutorStreamChunk.Answer("<end_of_thought>정답은 3입니다")
        )

        assertEquals("정답은 3입니다", result.answerTextDelta)
    }

    @Test
    fun `청크 경계를 걸친 특수 토큰도 노출되지 않는다`() {
        val parser = TutorStreamParser()
        parser.startFollowUp()

        val first = parser.onChunk(TutorStreamChunk.Answer("정답은 <end_of_th"))
        val second = parser.onChunk(TutorStreamChunk.Answer("ought>3입니다"))

        val combined = (first.answerTextDelta ?: "") + (second.answerTextDelta ?: "")
        assertEquals("정답은 3입니다", combined)
    }

    @Test
    fun `완료 시 남은 불완전 특수 토큰은 버린다`() {
        val parser = TutorStreamParser()
        parser.startFollowUp()

        val partial = parser.onChunk(TutorStreamChunk.Answer("끝 <|th"))
        assertEquals("끝 ", partial.answerTextDelta)

        val completed = parser.onChunk(TutorStreamChunk.Completed)
        assertNull(completed.answerTextDelta)
        assertTrue(completed.state.isCompleted)
        assertFalse(completed.state.isGenerating)
        assertEquals(TutorProgressStage.COMPLETED, completed.state.progressStage)
    }

    @Test
    fun `취소는 완료와 구분되고 미완성 조각을 이어 붙이지 않는다`() {
        val parser = TutorStreamParser()
        parser.startFollowUp()
        parser.onChunk(TutorStreamChunk.Answer("작성 중"))

        val result = parser.onChunk(TutorStreamChunk.Cancelled)

        assertNull(result.answerTextDelta)
        assertTrue(result.state.isCancelled)
        assertFalse(result.state.isCompleted)
        assertFalse(result.state.isGenerating)
        assertEquals(TutorProgressStage.CANCELLED, result.state.progressStage)
    }

    @Test
    fun `오류는 terminal 상태와 메시지를 남긴다`() {
        val parser = TutorStreamParser()
        parser.startFollowUp()

        val result = parser.onChunk(TutorStreamChunk.Error("추론 실패"))

        assertEquals("추론 실패", result.state.errorMessage)
        assertFalse(result.state.isGenerating)
        assertEquals(TutorProgressStage.ERROR, result.state.progressStage)
        assertTrue(result.state.isTerminal)
    }

    @Test
    fun `취소 완료를 확인하지 못하면 복구 필요 상태가 된다`() {
        val parser = TutorStreamParser()
        parser.startOcr()

        val requested = parser.onCancellationRequested()
        assertTrue(requested.isCancelling)
        assertTrue(requested.isGenerating)

        val unconfirmed = parser.onCancellationUnconfirmed()
        assertTrue(unconfirmed.requiresRecovery)
        assertFalse(unconfirmed.isGenerating)
        assertFalse(unconfirmed.isCancelling)
        assertFalse(unconfirmed.isCompleted)
    }

    @Test
    fun `완료 이벤트 없이 스트림이 끝나면 완료로 방어 처리한다`() {
        val parser = TutorStreamParser()
        parser.startFollowUp()
        parser.onChunk(TutorStreamChunk.Answer("답변"))

        val result = parser.onStreamEnded()

        assertTrue(result.state.isCompleted)
        assertFalse(result.state.isGenerating)
        assertNull(result.answerTextDelta)
    }

    @Test
    fun `이미 종료된 상태에서는 스트림 종료 처리가 상태를 바꾸지 않는다`() {
        val parser = TutorStreamParser()
        parser.startFollowUp()
        parser.onChunk(TutorStreamChunk.Completed)

        val result = parser.onStreamEnded()

        assertTrue(result.state.isCompleted)
        assertNull(result.answerTextDelta)
    }

    @Test
    fun `reset은 이전 진행 상태를 모두 지운다`() {
        val parser = TutorStreamParser()
        parser.startOcr()
        parser.onChunk(TutorStreamChunk.ThoughtActivity)

        parser.reset()

        val state = parser.state()
        assertEquals(TutorUiState(), state)
    }
}
