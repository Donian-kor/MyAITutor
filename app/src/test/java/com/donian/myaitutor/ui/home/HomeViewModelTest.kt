package com.donian.myaitutor.ui.home

import android.app.Application
import android.content.Context
import com.donian.myaitutor.ai.Assistant
import com.donian.myaitutor.ai.AssistantRequest
import com.donian.myaitutor.ai.AssistantStreamEvent
import com.donian.myaitutor.ai.TutorGrade
import com.donian.myaitutor.ai.followUpAnswerPrefix
import com.donian.myaitutor.model.ModelDownloadRepository
import com.donian.myaitutor.model.ModelDownloadSnapshot
import com.donian.myaitutor.model.ModelDownloadStatus
import com.donian.myaitutor.ocr.OcrBlock
import com.donian.myaitutor.ocr.OcrTextRecognizer
import com.donian.myaitutor.tutor.FakeGemmaTutorEngine
import com.donian.myaitutor.tutor.RealGemmaTutorEngine
import com.donian.myaitutor.tutor.TutorRequest
import com.donian.myaitutor.tutor.TutorStreamChunk
import com.donian.myaitutor.tutor.inferenceOptionsFor
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private class TestApp : Application(), OcrTextRecognizer {
        var recognizedBlocks = listOf(
            OcrBlock(
                text = "인식된 문제",
                type = OcrBlock.OcrBlockType.TEXT,
                bounds = null,
                confidence = 1.0f,
                readingOrder = 0,
                sourceFile = ""
            )
        )

        override fun getApplicationContext(): Context = this
        override suspend fun recognize(imageFile: File): List<OcrBlock> = recognizedBlocks
    }

    /**
     * GemmaAssistant와 동일한 계약을 따르는 테스트용 구현.
     * 스트리밍은 [FakeGemmaTutorEngine]이 담당하므로 여기서는 호출 여부만 기록합니다.
     */
    private class FakeAssistant : Assistant {
        var streamCalls = 0
        var cancelCalls = 0
        var resetCalls = 0
        var awaitStoppedCalls = 0
        var lastRequest: AssistantRequest? = null

        override fun streamAnswer(request: AssistantRequest): Flow<AssistantStreamEvent> {
            streamCalls++
            lastRequest = request
            return emptyFlow()
        }

        override suspend fun awaitGenerationStopped(timeoutMillis: Long): Boolean {
            awaitStoppedCalls++
            return true
        }

        override suspend fun resetConversation() {
            resetCalls++
        }

        override suspend fun analyzeImage(
            ocrText: String,
            subject: String,
            onTextChanged: suspend (String) -> Unit
        ) {
            streamCalls++
            onTextChanged("분석 결과")
        }

        override suspend fun askFollowUp(question: String, onTextChanged: suspend (String) -> Unit) {
            streamCalls++
            onTextChanged("후속 답변")
        }

        override suspend fun backendLabel(): String = "TEST"
        override suspend fun getTokenCount(): Int = 0
        override fun cancelGeneration() {
            cancelCalls++
        }

        override suspend fun preWarm() {}
        override suspend fun release() {}
    }

    private class FakeRepo(private val ready: Boolean = true) : ModelDownloadRepository {
        override val modelFile: File = File("fake.litertlm")
        override fun snapshot() = ModelDownloadSnapshot(
            if (ready) ModelDownloadStatus.READY else ModelDownloadStatus.NOT_INSTALLED,
            0L, 0L, ""
        )

        override fun startDownload(): Long = 1L
        override fun deleteModel() {}
        override fun isModelReady(): Boolean = ready
        override fun needsHashVerification(): Boolean = false
        override suspend fun verifyModelHash(): Boolean = true
    }

    private fun createViewModel(
        scope: kotlinx.coroutines.CoroutineScope,
        app: TestApp = TestApp(),
        assistant: FakeAssistant = FakeAssistant(),
        engine: FakeGemmaTutorEngine = FakeGemmaTutorEngine(),
        ready: Boolean = true,
        stringProvider: StringProvider = StringProvider { _, _ -> "테스트" }
    ): Pair<HomeViewModel, FakeGemmaTutorEngine> {
        val vm = HomeViewModel(
            app,
            FakeRepo(ready),
            assistant,
            scope,
            kotlinx.coroutines.Dispatchers.Unconfined,
            false,
            ocrLogger = {},
            stringProvider = stringProvider,
            tutorEngine = engine
        )
        return vm to engine
    }

    @Test
    fun `analyzeImage는 OCR 텍스트를 요청에 담고 Answer 증가분을 누적한다`() = runTest {
        val engine = FakeGemmaTutorEngine(
            chunkProvider = {
                listOf(
                    TutorStreamChunk.Answer("첫째 "),
                    TutorStreamChunk.Answer("둘째"),
                    TutorStreamChunk.Completed
                )
            }
        )
        val (vm, _) = createViewModel(this, engine = engine)
        val file = File.createTempFile("img", ".jpg")

        vm.analyzeImage(file, "수학")
        advanceUntilIdle()

        assertEquals(false, vm.state.value.isAnalyzing)
        assertEquals("첫째 둘째", vm.state.value.answer)
        assertEquals("인식된 문제", vm.state.value.ocrText)
        assertEquals("인식된 문제", engine.requests.single().text)
        assertEquals("수학", engine.requests.single().subject)
        assertNull(vm.state.value.analysisStage)
        assertTrue(vm.state.value.tutorState.isCompleted)
        assertTrue(vm.state.value.showFollowUpChoices)
        file.delete()
    }

    @Test
    fun `OCR 결과가 비어 있으면 Gemma를 호출하지 않는다`() = runTest {
        val app = TestApp().apply { recognizedBlocks = emptyList() }
        val engine = FakeGemmaTutorEngine()
        val (vm, _) = createViewModel(
            this,
            app = app,
            engine = engine,
            stringProvider = StringProvider { resId, _ ->
                if (resId == com.donian.myaitutor.R.string.status_ocr_empty) {
                    "글자를 찾지 못했습니다. 다시 촬영해 주세요."
                } else {
                    "테스트"
                }
            }
        )
        val file = File.createTempFile("img", ".jpg")

        vm.analyzeImage(file, "수학")
        advanceUntilIdle()

        assertEquals(0, engine.requests.size)
        assertEquals("글자를 찾지 못했습니다. 다시 촬영해 주세요.", vm.state.value.answer)
        assertEquals(false, vm.state.value.isAnalyzing)
        assertEquals(false, vm.state.value.showFollowUpChoices)
        assertTrue(vm.state.value.tutorState.errorMessage != null)
        file.delete()
    }

    @Test
    fun `isAnalyzing 중에는 중복 analyzeImage가 무시된다`() = runTest {
        val engine = FakeGemmaTutorEngine()
        val (vm, _) = createViewModel(this, engine = engine)
        val file = File.createTempFile("img", ".jpg")

        vm.analyzeImage(file, "수학")
        vm.analyzeImage(file, "수학")
        advanceUntilIdle()

        assertEquals(1, engine.requests.size)
        file.delete()
    }

    @Test
    fun `모델이 없으면 sendQuestion은 상태 메시지를 남기고 실행하지 않는다`() = runTest {
        val engine = FakeGemmaTutorEngine()
        val (vm, _) = createViewModel(this, engine = engine, ready = false)

        vm.sendQuestion("이게 뭐야?")
        advanceUntilIdle()

        assertEquals(0, engine.requests.size)
        assertNotNull(vm.state.value.statusMessage)
    }

    @Test
    fun `중등 이상은 Thinking을 켜고 유아와 초등은 끈다`() = runTest {
        val engine = FakeGemmaTutorEngine()
        val (vm, _) = createViewModel(this, engine = engine)
        val file = File.createTempFile("img", ".jpg")

        vm.selectGrade(TutorGrade.KIDS)
        vm.analyzeImage(file, "수학")
        advanceUntilIdle()
        val kidsOptions = engine.requests.last().options
        assertFalse(kidsOptions.thinkingEnabled)
        assertEquals(0, kidsOptions.thinkingTokenBudget)

        vm.selectGrade(TutorGrade.HIGH)
        vm.analyzeImage(file, "수학")
        advanceUntilIdle()
        val highOptions = engine.requests.last().options
        assertTrue(highOptions.thinkingEnabled)
        assertEquals(1024, highOptions.thinkingTokenBudget)

        file.delete()
    }

    @Test
    fun `후속 질문은 이전 답변 뒤에 이어 붙이고 isFollowUp을 설정한다`() = runTest {
        val engine = FakeGemmaTutorEngine()
        val (vm, _) = createViewModel(this, engine = engine)
        val file = File.createTempFile("img", ".jpg")

        vm.analyzeImage(file, "수학")
        advanceUntilIdle()
        val firstAnswer = vm.state.value.answer

        vm.sendQuestion("왜 그런가요?")
        advanceUntilIdle()

        val expectedPrefix = followUpAnswerPrefix(firstAnswer, "왜 그런가요?")
        assertEquals(expectedPrefix + "테스트 해설", vm.state.value.answer)
        assertTrue(engine.requests.last().options.isFollowUp)
        assertEquals("왜 그런가요?", engine.requests.last().text)
        file.delete()
    }

    @Test
    fun `스트림 오류는 오류 상태로 남고 isAnalyzing을 복구한다`() = runTest {
        val engine = FakeGemmaTutorEngine(
            chunkProvider = { listOf(TutorStreamChunk.Error("추론 실패")) }
        )
        val (vm, _) = createViewModel(this, engine = engine)
        val file = File.createTempFile("img", ".jpg")

        vm.analyzeImage(file, "수학")
        advanceUntilIdle()

        assertEquals("추론 실패", vm.state.value.tutorState.errorMessage)
        assertEquals(false, vm.state.value.isAnalyzing)
        assertEquals(false, vm.state.value.showFollowUpChoices)
        file.delete()
    }

    @Test
    fun `취소 완료가 확인되면 취소 상태로 정리한다`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val engine = FakeGemmaTutorEngine(
            chunkProvider = { listOf(TutorStreamChunk.Answer("늦은 답변"), TutorStreamChunk.Completed) }
        ).apply { this.gate = gate }
        val (vm, _) = createViewModel(this, engine = engine)
        val file = File.createTempFile("img", ".jpg")

        vm.analyzeImage(file, "수학")
        advanceUntilIdle()
        assertTrue(vm.state.value.isAnalyzing)

        vm.cancelGeneration()
        advanceUntilIdle()

        assertEquals(1, engine.cancelCalls)
        assertTrue(vm.state.value.tutorState.isCancelled)
        assertEquals(false, vm.state.value.isAnalyzing)
        assertEquals("", vm.state.value.answer)
        assertFalse(vm.state.value.tutorState.requiresRecovery)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals("", vm.state.value.answer)
        file.delete()
    }

    @Test
    fun `취소 완료를 확인하지 못하면 복구 전까지 새 추론을 막는다`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val engine = FakeGemmaTutorEngine().apply {
            this.gate = gate
            awaitStoppedResult = false
        }
        val (vm, _) = createViewModel(this, engine = engine)
        val file = File.createTempFile("img", ".jpg")

        vm.analyzeImage(file, "수학")
        advanceUntilIdle()

        vm.cancelGeneration()
        advanceUntilIdle()

        assertTrue(vm.state.value.tutorState.requiresRecovery)
        assertFalse(vm.state.value.isAnalyzing)
        assertNotNull(vm.state.value.tutorState.errorMessage)

        val requestCountBefore = engine.requests.size
        vm.analyzeImage(file, "수학")
        advanceUntilIdle()
        assertEquals(requestCountBefore, engine.requests.size)

        engine.gate = null
        vm.recoverTutor()
        advanceUntilIdle()
        assertFalse(vm.state.value.tutorState.requiresRecovery)
        assertEquals(1, engine.resetConversationCalls)

        vm.analyzeImage(file, "수학")
        advanceUntilIdle()
        assertEquals(requestCountBefore + 1, engine.requests.size)
        file.delete()
    }

    @Test
    fun `새 문제를 시작하면 이전 세대의 늦은 결과가 반영되지 않는다`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val engine = FakeGemmaTutorEngine(
            chunkProvider = { listOf(TutorStreamChunk.Answer("이전 답변"), TutorStreamChunk.Completed) }
        ).apply { this.gate = gate }
        val (vm, _) = createViewModel(this, engine = engine)
        val file = File.createTempFile("img", ".jpg")

        vm.analyzeImage(file, "수학")
        advanceUntilIdle()
        assertTrue(vm.state.value.isAnalyzing)

        vm.resetForNewSubject()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals("", vm.state.value.answer)
        assertFalse(vm.state.value.isAnalyzing)
        file.delete()
    }

    @Test
    fun `토큰 조회가 실패해도 UI 상태를 복구한다`() = runTest {
        val engine = FakeGemmaTutorEngine(tokenCountFailure = true)
        val (vm, _) = createViewModel(this, engine = engine)
        val file = File.createTempFile("img", ".jpg")

        vm.analyzeImage(file, "수학")
        advanceUntilIdle()

        assertEquals(false, vm.state.value.isAnalyzing)
        assertEquals(0, vm.state.value.tokenCount)
        assertTrue(vm.state.value.tutorState.isCompleted)
        file.delete()
    }

    @Test
    fun `cancelGeneration은 튜터 엔진의 취소를 호출한다`() = runTest {
        val engine = FakeGemmaTutorEngine()
        val (vm, _) = createViewModel(this, engine = engine)
        val file = File.createTempFile("img", ".jpg")

        vm.analyzeImage(file, "수학")
        advanceUntilIdle()
        // 이미 완료된 상태에서는 취소할 Job이 없으므로 호출되지 않습니다.
        vm.cancelGeneration()
        assertEquals(0, engine.cancelCalls)

        val gate = CompletableDeferred<Unit>()
        engine.gate = gate
        vm.analyzeImage(file, "수학")
        advanceUntilIdle()
        vm.cancelGeneration()
        assertEquals(1, engine.cancelCalls)
        gate.complete(Unit)
        advanceUntilIdle()
        file.delete()
    }

    @Test
    fun `RealGemmaTutorEngine은 OCR 텍스트와 학년 지침을 프롬프트로 구성한다`() = runTest {
        val assistant = FakeAssistant()
        val engine = RealGemmaTutorEngine(assistant)
        val request = TutorRequest(
            text = "2 + 3 = ?",
            subject = "수학",
            grade = TutorGrade.MIDDLE,
            options = inferenceOptionsFor(TutorGrade.MIDDLE, isFollowUp = false)
        )

        engine.stream(request).toList()

        val assistantRequest = requireNotNull(assistant.lastRequest)
        assertTrue(assistantRequest.startsNewProblem)
        assertTrue(assistantRequest.textParts[0].contains("[OCR 인식 텍스트]"))
        assertTrue(assistantRequest.textParts[0].contains("2 + 3 = ?"))
        assertTrue(assistantRequest.textParts[1].contains("수학"))
        assertTrue(assistantRequest.textParts[1].contains("중학생"))
        assertTrue(assistantRequest.options.thinkingEnabled)
        assertEquals(1024, assistantRequest.options.thinkingTokenBudget)
    }

    @Test
    fun `후속 질문 프롬프트는 같은 Conversation을 이어 쓰도록 구성한다`() = runTest {
        val assistant = FakeAssistant()
        val engine = RealGemmaTutorEngine(assistant)
        val request = TutorRequest(
            text = "왜 그런가요?",
            subject = "수학",
            grade = TutorGrade.ELEMENTARY,
            options = inferenceOptionsFor(TutorGrade.ELEMENTARY, isFollowUp = true)
        )

        engine.stream(request).toList()

        val assistantRequest = requireNotNull(assistant.lastRequest)
        assertFalse(assistantRequest.startsNewProblem)
        assertTrue(assistantRequest.textParts.single().contains("왜 그런가요?"))
        assertFalse(assistantRequest.options.thinkingEnabled)
        assertEquals(0, assistantRequest.options.thinkingTokenBudget)
    }

    @Test
    fun `close는 엔진을 한 번만 해제한다`() = runTest {
        val engine = FakeGemmaTutorEngine()
        val (vm, _) = createViewModel(this, engine = engine)
        vm.close()
        vm.close()
        assertEquals(1, engine.releaseCalls)
        advanceUntilIdle()
    }
}
