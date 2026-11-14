package com.donian.myaitutor.ui.home

import android.app.Application
import android.content.Context
import com.donian.myaitutor.ai.Assistant
import com.donian.myaitutor.ai.AssistantRequest
import com.donian.myaitutor.ai.AssistantStreamEvent
import com.donian.myaitutor.ai.ChatSystemInstruction
import com.donian.myaitutor.ai.DEFAULT_MAX_OUTPUT_TOKENS
import com.donian.myaitutor.ai.DEFAULT_THINKING_TOKEN_BUDGET
import com.donian.myaitutor.ai.MAX_CONTEXT_TOKENS
import com.donian.myaitutor.ai.MAX_CONVERSATION_TOKENS
import com.donian.myaitutor.ai.SystemInstruction
import com.donian.myaitutor.ai.TutorGrade
import com.donian.myaitutor.ai.gradeChatTone
import com.donian.myaitutor.model.AppModel
import com.donian.myaitutor.model.ModelDownloadRepository
import com.donian.myaitutor.model.ModelDownloadSnapshot
import com.donian.myaitutor.model.ModelDownloadStatus
import com.donian.myaitutor.model.ModelRuntimeState
import com.donian.myaitutor.model.ModelSelection
import com.donian.myaitutor.model.ModelSelectionRepository
import com.donian.myaitutor.ocr.OcrBlock
import com.donian.myaitutor.ocr.OcrTextRecognizer
import com.donian.myaitutor.tutor.FakeGemmaTutorEngine
import com.donian.myaitutor.tutor.RealGemmaTutorEngine
import com.donian.myaitutor.tutor.TutorMode
import com.donian.myaitutor.tutor.TutorRequest
import com.donian.myaitutor.tutor.TutorStreamChunk
import com.donian.myaitutor.tutor.inferenceOptionsFor
import com.donian.myaitutor.ui.settings.AppSettingsRepository
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

        /** 마지막 [resetConversation]에 전달된 시스템 지시문. 모드 전환 검증에 사용합니다. */
        var lastSystemInstruction: String? = null
            private set

        override fun streamAnswer(request: AssistantRequest): Flow<AssistantStreamEvent> {
            streamCalls++
            lastRequest = request
            return emptyFlow()
        }

        override suspend fun awaitGenerationStopped(timeoutMillis: Long): Boolean {
            awaitStoppedCalls++
            return true
        }

        override suspend fun resetConversation(systemInstruction: String) {
            resetCalls++
            lastSystemInstruction = systemInstruction
        }

        override suspend fun analyzeImage(
            ocrText: String,
            subject: String,
            onTextChanged: suspend (String) -> Unit
        ) {
            streamCalls++
            onTextChanged("분석 결과")
        }

        override suspend fun askFollowUp(
            question: String,
            subject: String,
            onTextChanged: suspend (String) -> Unit
        ) {
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
        var downloadStarts = 0
        private var currentSnapshot = ModelDownloadSnapshot(
            if (ready) ModelDownloadStatus.READY else ModelDownloadStatus.NOT_INSTALLED,
            0L, 0L, ""
        )

        override fun snapshot() = currentSnapshot
        override fun startDownload(): Long {
            downloadStarts++
            currentSnapshot = ModelDownloadSnapshot(
                ModelDownloadStatus.DOWNLOADING,
                100L,
                1_000L,
                "Gemma 다운로드 중"
            )
            return 1L
        }
        override fun deleteModel() {}
        override fun isModelReady(): Boolean = ready
        override fun needsHashVerification(): Boolean = false
        override suspend fun verifyModelHash(): Boolean = true
    }

    /** Qwen 다운로드 상태를 테스트에서 주입할 수 있는 관리자 대역. */
    private class FakeQwenRepo(private val ready: Boolean = true) : ModelDownloadRepository {
        override val modelFile: File = File("fake_qwen.litertlm")
        var downloadStarts = 0
        private var currentSnapshot = ModelDownloadSnapshot(
            if (ready) ModelDownloadStatus.READY else ModelDownloadStatus.NOT_INSTALLED,
            0L, 0L, ""
        )

        override fun snapshot() = currentSnapshot
        override fun startDownload(): Long {
            downloadStarts++
            currentSnapshot = ModelDownloadSnapshot(
                ModelDownloadStatus.DOWNLOADING,
                200L,
                2_000L,
                "Qwen 다운로드 중"
            )
            return 1L
        }
        override fun deleteModel() {}
        override fun isModelReady(): Boolean = ready
        override fun needsHashVerification(): Boolean = false
        override suspend fun verifyModelHash(): Boolean = true
    }

    /** SharedPreferences를 쓰지 않는 설정 저장소 대역. */
    private class FakeAppSettings(override var thinkingMode: Boolean = false) :
        AppSettingsRepository

    /** SharedPreferences를 쓰지 않는 모델 선택 저장소 대역. */
    private class FakeSelectionRepository(
        model: AppModel = AppModel.GEMMA,
        chatMode: Boolean = false
    ) : ModelSelectionRepository {
        var selection = ModelSelection(model, chatMode)
            private set

        override fun get(): ModelSelection = selection

        override fun getSelectedModel(): AppModel = selection.model

        override fun isFreeChatEnabled(): Boolean = selection.chatMode

        override fun setSelectedModel(model: AppModel) {
            selection = selection.copy(
                model = model,
                chatMode = if (model == AppModel.QWEN3) false else selection.chatMode
            )
        }

        override fun setChatMode(enabled: Boolean) {
            if (enabled && selection.model == AppModel.QWEN3) return
            selection = selection.copy(chatMode = enabled)
        }
    }

    private fun createViewModel(
        scope: kotlinx.coroutines.CoroutineScope,
        app: TestApp = TestApp(),
        assistant: FakeAssistant = FakeAssistant(),
        engine: FakeGemmaTutorEngine = FakeGemmaTutorEngine(),
        ready: Boolean = true,
        appSettings: AppSettingsRepository = FakeAppSettings(),
        stringProvider: StringProvider = StringProvider { _, _ -> "테스트" },
        selectionRepository: ModelSelectionRepository = FakeSelectionRepository(),
        qwenModelManager: ModelDownloadRepository = FakeQwenRepo()
    ): Pair<HomeViewModel, FakeGemmaTutorEngine> {
        val vm = HomeViewModel(
            app,
            FakeRepo(ready),
            scope,
            kotlinx.coroutines.Dispatchers.Unconfined,
            enablePolling = false,
            ocrLogger = {},
            stringProvider = stringProvider,
            tutorEngine = engine,
            appSettings = appSettings,
            selectionRepository = selectionRepository,
            qwenModelManager = qwenModelManager
        )
        return vm to engine
    }

    @Test
    fun `Qwen 다운로드는 Qwen 상태만 갱신하고 Gemma 다운로드는 Gemma 상태만 갱신한다`() = runTest {
        val gemmaRepo = FakeRepo()
        val qwenRepo = FakeQwenRepo()
        val vm = HomeViewModel(
            TestApp(),
            gemmaRepo,
            this,
            kotlinx.coroutines.Dispatchers.Unconfined,
            enablePolling = false,
            ocrLogger = {},
            stringProvider = StringProvider { _, _ -> "테스트" },
            tutorEngine = FakeGemmaTutorEngine(),
            appSettings = FakeAppSettings(),
            selectionRepository = FakeSelectionRepository(),
            qwenModelManager = qwenRepo
        )

        vm.startModelDownload(AppModel.QWEN3)

        assertEquals(0, gemmaRepo.downloadStarts)
        assertEquals(1, qwenRepo.downloadStarts)
        assertEquals(ModelDownloadStatus.READY, vm.state.value.modelSnapshot.status)
        assertEquals(ModelDownloadStatus.DOWNLOADING, vm.state.value.qwenSnapshot.status)
        assertEquals("Qwen 다운로드 중", vm.state.value.qwenSnapshot.message)
        assertEquals(ModelDownloadStatus.READY, vm.state.value.selectedModelSnapshot.status)

        vm.startModelDownload(AppModel.GEMMA)

        assertEquals(1, gemmaRepo.downloadStarts)
        assertEquals(1, qwenRepo.downloadStarts)
        assertEquals("Gemma 다운로드 중", vm.state.value.modelSnapshot.message)
        assertEquals("Qwen 다운로드 중", vm.state.value.qwenSnapshot.message)
        assertEquals(ModelDownloadStatus.DOWNLOADING, vm.state.value.selectedModelSnapshot.status)
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
    fun `Thinking은 학년과 무관하게 기본값이 꺼짐 상태다`() = runTest {
        val engine = FakeGemmaTutorEngine()
        val (vm, _) = createViewModel(this, engine = engine)
        val file = File.createTempFile("img", ".jpg")

        // 학년을 바꿔도 Thinking이 자동으로 켜지지 않습니다.
        vm.selectGrade(TutorGrade.ELEMENTARY)
        vm.analyzeImage(file, "수학")
        advanceUntilIdle()
        val elementaryOptions = engine.requests.last().options
        assertFalse(elementaryOptions.thinkingEnabled)
        assertEquals(0, elementaryOptions.thinkingTokenBudget)

        vm.selectGrade(TutorGrade.HIGH)
        vm.analyzeImage(file, "수학")
        advanceUntilIdle()
        val highOptions = engine.requests.last().options
        assertFalse(highOptions.thinkingEnabled)
        assertEquals(0, highOptions.thinkingTokenBudget)

        file.delete()
    }

    @Test
    fun `설정에서 Thinking을 켜면 요청에 반영된다`() = runTest {
        val engine = FakeGemmaTutorEngine()
        val (vm, _) = createViewModel(this, engine = engine)
        val file = File.createTempFile("img", ".jpg")

        vm.setThinkingMode(true)
        assertTrue(vm.state.value.thinkingMode)

        vm.analyzeImage(file, "수학")
        advanceUntilIdle()
        val options = engine.requests.last().options
        assertTrue(options.thinkingEnabled)
        assertEquals(DEFAULT_THINKING_TOKEN_BUDGET, options.thinkingTokenBudget)

        file.delete()
    }

    @Test
    fun `설정에서 Thinking을 끄면 요청에서 제외된다`() = runTest {
        val engine = FakeGemmaTutorEngine()
        val (vm, _) = createViewModel(this, engine = engine)
        val file = File.createTempFile("img", ".jpg")

        vm.setThinkingMode(true)
        vm.analyzeImage(file, "수학")
        advanceUntilIdle()
        assertTrue(engine.requests.last().options.thinkingEnabled)

        vm.setThinkingMode(false)
        assertFalse(vm.state.value.thinkingMode)
        vm.analyzeImage(file, "수학")
        advanceUntilIdle()
        val options = engine.requests.last().options
        assertFalse(options.thinkingEnabled)
        assertEquals(0, options.thinkingTokenBudget)

        file.delete()
    }

    @Test
    fun `Thinking 설정은 저장소에 기록되고 시작 시 복원된다`() = runTest {
        val settings = FakeAppSettings(thinkingMode = true)
        val (vm, _) = createViewModel(this, appSettings = settings)

        // 저장된 값이 초기 상태로 복원됩니다.
        assertTrue(vm.state.value.thinkingMode)

        vm.setThinkingMode(false)
        assertFalse(settings.thinkingMode)

        vm.setThinkingMode(true)
        assertTrue(settings.thinkingMode)
    }

    @Test
    fun `후속 질문은 이전 답변을 이어 붙이지 않고 새 답변만 표시한다`() = runTest {
        val engine = FakeGemmaTutorEngine()
        val (vm, _) = createViewModel(this, engine = engine)
        val file = File.createTempFile("img", ".jpg")

        vm.analyzeImage(file, "수학")
        advanceUntilIdle()
        assertTrue(vm.state.value.answer.isNotBlank())

        vm.sendQuestion("왜 그런가요?")
        advanceUntilIdle()

        // 채팅은 messages 목록의 개별 버블로 표시하므로 answer에는 새 답변만 담깁니다.
        assertEquals("테스트 해설", vm.state.value.answer)
        assertTrue(
            vm.state.value.messages.any {
                it.role == ChatRole.STUDENT && it.text == "왜 그런가요?"
            }
        )
        assertTrue(engine.requests.last().options.isFollowUp)
        assertEquals("왜 그런가요?", engine.requests.last().text)
        file.delete()
    }

    @Test
    fun `과목을 바꾼 뒤 입력한 질문은 새 과목의 독립 질문으로 보낸다`() = runTest {
        val engine = FakeGemmaTutorEngine()
        val (vm, _) = createViewModel(this, engine = engine)
        val file = File.createTempFile("img", ".jpg")

        vm.analyzeImage(file, "수학")
        advanceUntilIdle()
        vm.resetForNewSubject()
        vm.sendQuestion("조선 시대 세종의 업적을 알려줘", subject = "역사")
        advanceUntilIdle()

        val request = engine.requests.last()
        assertEquals("역사", request.subject)
        assertTrue(request.isStandaloneQuestion)
        assertEquals("조선 시대 세종의 업적을 알려줘", request.text)
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
    fun `생성 중지 후 새 질문을 보내면 AI가 응답한다`() = runTest {
        val engine = FakeGemmaTutorEngine().apply {
            gate = CompletableDeferred()
        }
        val (vm, _) = createViewModel(this, engine = engine)
        val file = File.createTempFile("img", ".jpg")

        vm.analyzeImage(file, "수학")
        advanceUntilIdle()
        vm.cancelGeneration()
        advanceUntilIdle()

        assertFalse(vm.state.value.isAnalyzing)
        engine.gate = null
        vm.sendQuestion("이 문제에서 핵심 개념은 무엇인가요?")
        advanceUntilIdle()

        assertEquals(2, engine.requests.size)
        assertTrue(vm.state.value.answer.contains("테스트 해설"))
        assertFalse(vm.state.value.isAnalyzing)
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
    fun `답변 중 취소한 뒤 입력한 질문은 다시 추론을 시작한다`() = runTest {
        val engine = FakeGemmaTutorEngine()
        val (vm, _) = createViewModel(this, engine = engine)
        val file = File.createTempFile("img", ".jpg")

        val gate = CompletableDeferred<Unit>()
        engine.gate = gate
        vm.analyzeImage(file, "수학")
        advanceUntilIdle()
        assertEquals(1, engine.requests.size)

        vm.cancelGeneration()
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()
        engine.gate = null
        assertFalse(vm.state.value.isAnalyzing)

        vm.sendQuestion("왜 그런가요?")
        advanceUntilIdle()

        assertEquals(2, engine.requests.size)
        assertTrue(vm.state.value.messages.any { it.text == "테스트 해설" })
        file.delete()
    }

    @Test
    fun `취소가 확인되면 대화 세션을 새로 만든다`() = runTest {
        val engine = FakeGemmaTutorEngine()
        val (vm, _) = createViewModel(this, engine = engine)
        val file = File.createTempFile("img", ".jpg")

        val gate = CompletableDeferred<Unit>()
        engine.gate = gate
        vm.analyzeImage(file, "수학")
        advanceUntilIdle()
        assertEquals(0, engine.resetConversationCalls)

        vm.cancelGeneration()
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, engine.resetConversationCalls)
        file.delete()
    }

    @Test
    fun `취소 확인 중에 입력한 질문은 취소가 끝나면 자동 전송된다`() = runTest {
        val engine = FakeGemmaTutorEngine()
        val (vm, _) = createViewModel(this, engine = engine)
        val file = File.createTempFile("img", ".jpg")

        val streamGate = CompletableDeferred<Unit>()
        val stopGate = CompletableDeferred<Unit>()
        engine.gate = streamGate
        engine.stopGate = stopGate
        vm.analyzeImage(file, "수학")
        advanceUntilIdle()
        assertEquals(1, engine.requests.size)

        vm.cancelGeneration()
        advanceUntilIdle()
        // 아직 취소가 확인되지 않아 추론을 시작할 수 없습니다.
        vm.sendQuestion("왜 그런가요?")
        advanceUntilIdle()
        assertEquals(1, engine.requests.size)

        stopGate.complete(Unit)
        streamGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(2, engine.requests.size)
        assertEquals("왜 그런가요?", engine.requests.last().text)
        file.delete()
    }

    // -----------------------------------------------------------------
    // 모델 선택 및 0.6 모드 정합
    // -----------------------------------------------------------------

    @Test
    fun `Qwen 파일이 없으면 Qwen 선택은 차단되고 안내가 표시된다`() = runTest {
        val (vm, _) = createViewModel(
            this,
            qwenModelManager = FakeQwenRepo(ready = false)
        )

        vm.setSelectedModel(AppModel.QWEN3)
        advanceUntilIdle()

        // 전환하지 않고 GEMMA를 유지한다.
        assertEquals(AppModel.GEMMA, vm.state.value.selectedModel)
        assertFalse(vm.state.value.statusMessage.isNullOrBlank())
    }

    @Test
    fun `Qwen 파일이 준비되면 Qwen으로 전환된다`() = runTest {
        val selection = FakeSelectionRepository()
        val (vm, _) = createViewModel(
            this,
            selectionRepository = selection,
            qwenModelManager = FakeQwenRepo(ready = true)
        )

        vm.setSelectedModel(AppModel.QWEN3)
        advanceUntilIdle()

        assertEquals(AppModel.QWEN3, vm.state.value.selectedModel)
        assertEquals(ModelRuntimeState.ACTIVE, vm.state.value.runtimeState)
    }

    @Test
    fun `Qwen 활성 상태에서 자유 대화를 켜면 차단된다`() = runTest {
        val (vm, _) = createViewModel(
            this,
            qwenModelManager = FakeQwenRepo(ready = true)
        )
        vm.setSelectedModel(AppModel.QWEN3)
        advanceUntilIdle()
        assertFalse(vm.state.value.chatMode)

        vm.setChatMode(true)
        advanceUntilIdle()

        // 차단되어 chatMode는 꺼진 상태로 남는다.
        assertFalse(vm.state.value.chatMode)
        assertFalse(vm.state.value.freeChatBlockedReason.isNullOrBlank())
        assertFalse(vm.state.value.isFreeChatAvailable)
    }

    @Test
    fun `자유 대화 사용 중에는 Qwen 선택이 보류된다`() = runTest {
        val (vm, _) = createViewModel(
            this,
            qwenModelManager = FakeQwenRepo(ready = true)
        )
        vm.setChatMode(true)
        advanceUntilIdle()
        assertTrue(vm.state.value.chatMode)

        vm.setSelectedModel(AppModel.QWEN3)
        advanceUntilIdle()

        assertFalse(vm.state.value.modelSelectionBlockedReason.isNullOrBlank())
    }

    @Test
    fun `저장값이 Qwen과 자유 대화면 정규화하면 자유 대화가 꺼진다`() = runTest {
        // 어긋난 저장값: QWEN3 + chatMode=true
        val normalized = ModelSelection(
            AppModel.QWEN3,
            chatMode = true
        ).normalized()

        assertFalse(normalized.chatMode)
        assertEquals(AppModel.QWEN3, normalized.model)
        assertTrue(normalized.isConsistent)
    }

    @Test
    fun `Gemma 기본값으로 시작하면 자유 대화를 사용할 수 있다`() = runTest {
        val (vm, _) = createViewModel(this)

        assertTrue(vm.state.value.isFreeChatAvailable)
        assertEquals(null, vm.state.value.freeChatBlockedReason)
    }

    @Test
    fun `자유 대화 모드로 전환하면 대화 지시문으로 Conversation을 교체한다`() = runTest {
        val engine = FakeGemmaTutorEngine()
        val (vm, _) = createViewModel(this, engine = engine)

        vm.setChatMode(true)
        advanceUntilIdle()

        assertEquals(1, engine.resetConversationCalls)
        assertEquals(ChatSystemInstruction, engine.lastSystemInstruction)
        assertTrue(vm.state.value.chatMode)
    }

    @Test
    fun `학습 모드로 돌아오면 학습 지시문으로 Conversation을 교체한다`() = runTest {
        val engine = FakeGemmaTutorEngine()
        val (vm, _) = createViewModel(this, engine = engine)

        vm.setChatMode(true)
        advanceUntilIdle()
        vm.setChatMode(false)
        advanceUntilIdle()

        assertEquals(2, engine.resetConversationCalls)
        assertEquals(SystemInstruction, engine.lastSystemInstruction)
        assertFalse(vm.state.value.chatMode)
    }

    @Test
    fun `모드 전환은 진행 중인 대화와 촬영 이미지를 초기화한다`() = runTest {
        val engine = FakeGemmaTutorEngine()
        val (vm, _) = createViewModel(this, engine = engine)
        val file = File.createTempFile("img", ".jpg")

        vm.analyzeImage(file, "수학")
        advanceUntilIdle()
        assertTrue(vm.state.value.messages.isNotEmpty())

        vm.setChatMode(true)
        advanceUntilIdle()

        assertTrue(vm.state.value.messages.isEmpty())
        assertNull(vm.state.value.capturedImage)
        assertEquals("", vm.state.value.answer)
        file.delete()
    }

    @Test
    fun `자유 대화 질문은 새 문제를 시작하지 않고 대화 맥락을 이어간다`() = runTest {
        val assistant = FakeAssistant()
        val engine = RealGemmaTutorEngine(assistant)
        val request = TutorRequest(
            text = "오늘 친구랑 크게 싸웠어",
            subject = "기타",
            grade = TutorGrade.MIDDLE,
            options = inferenceOptionsFor(isFollowUp = true, thinkingEnabled = false),
            mode = TutorMode.FREE_CHAT
        )

        engine.stream(request).toList()

        val assistantRequest = requireNotNull(assistant.lastRequest)
        assertFalse(assistantRequest.startsNewProblem)
        val prompt = assistantRequest.textParts.single()
        assertTrue(prompt.contains("오늘 친구랑 크게 싸웠어"))
        assertTrue(prompt.contains("중학생과 이야기하고 있어"))
        assertFalse(prompt.contains("선택 과목"))
    }

    @Test
    fun `자유 대화 모드의 질문은 과목 이동과 무관하게 대화를 이어간다`() = runTest {
        val engine = FakeGemmaTutorEngine()
        val (vm, _) = createViewModel(this, engine = engine)
        val file = File.createTempFile("img", ".jpg")

        vm.analyzeImage(file, "수학")
        advanceUntilIdle()
        vm.setChatMode(true)
        advanceUntilIdle()

        vm.sendQuestion("오늘 기분이 안 좋아")
        advanceUntilIdle()
        vm.sendQuestion("그냥 아무한테도 말 못 하겠어")
        advanceUntilIdle()

        val last = engine.requests.last()
        assertEquals(TutorMode.FREE_CHAT, last.mode)
        assertFalse(last.isStandaloneQuestion)
        // 모드를 한 번만 바꿨으므로 추가 재설정은 없습니다.
        assertEquals(1, engine.resetConversationCalls)
        file.delete()
    }

    @Test
    fun `안전 규칙은 모드별로 분리되어 각 지시문에 포함된다`() = runTest {
        // 공통 필수 항목은 두 모드 모두에 들어 있습니다.
        listOf(SystemInstruction, ChatSystemInstruction).forEach { prompt ->
            assertTrue(prompt.contains("안전 규칙"))
            assertTrue(prompt.contains("욕설"))
            assertTrue(prompt.contains("인신공격"))
            assertTrue(prompt.contains("청소년상담전화 1388"))
            assertTrue(prompt.contains("무효화되지 않습니다"))
        }

        // 학습 모드 전용: 문제 풀이 맥락에 맞는 항목만.
        assertTrue(SystemInstruction.contains("학대·괴롭힘 신호가 보이면"))
        assertFalse(SystemInstruction.contains("당신은 AI이며 실제 사람이 아닙니다"))
        assertFalse(SystemInstruction.contains("랜덤채팅"))

        // 자유 대화 모드 전용: 대화 맥락의 위험까지 포함.
        assertTrue(ChatSystemInstruction.contains("당신은 AI이며 실제 사람이 아닙니다"))
        assertTrue(ChatSystemInstruction.contains("만남을 제안받으면 정중히 거절"))
        assertTrue(ChatSystemInstruction.contains("비밀 유지 약속"))
        assertTrue(ChatSystemInstruction.contains("따돌림·복수·보복"))
        assertTrue(ChatSystemInstruction.contains(" 랜덤채팅"))
        assertTrue(ChatSystemInstruction.contains("자해·자살의 방법·수단·도구"))

        // 자유 대화 지시문에는 학습 모드의 추천 질문 형식 규칙이 없어야 합니다.
        assertFalse(ChatSystemInstruction.contains("맞춤 추천 질문"))
        // 자유 대화는 친근한 반말체이며 존댓말 유지를 지시하지 않습니다.
        assertTrue(ChatSystemInstruction.contains("친근한 반말로 말하세요"))
        assertFalse(ChatSystemInstruction.contains("존댓말을 유지"))
        // 학년별 말투는 모두 반말 기준입니다.
        assertTrue(gradeChatTone(TutorGrade.ELEMENTARY).contains("반말"))
        assertTrue(gradeChatTone(TutorGrade.MIDDLE).contains("반말"))
        assertTrue(gradeChatTone(TutorGrade.HIGH).contains("반말"))
    }

    @Test
    fun `컨텍스트 예산은 앱 예산 상수 하나에서 계산된다`() = runTest {
        // 앱 예산은 실측(8,192에서 KV Graphics가 약 1.98GB 증가)에 근거한 값입니다.
        // 모델 자체 상한(32k)이 아닙니다.
        assertEquals(4_096, MAX_CONTEXT_TOKENS)
        // 리셋 임계값은 출력 예약분만 제외합니다. getTokenCount()에 시스템 지시문이 이미
        // 포함되어 있으므로 시스템 지시문을 한 번 더 빼면 대화가 거의 매 턴 초기화됩니다.
        assertEquals(MAX_CONTEXT_TOKENS - DEFAULT_MAX_OUTPUT_TOKENS, MAX_CONVERSATION_TOKENS)
        assertEquals(3_072, MAX_CONVERSATION_TOKENS)
        // 초기화 직후 응답을 출력 상한만큼 생성해도 전체 예산을 넘지 않아야 합니다.
        assertTrue(MAX_CONVERSATION_TOKENS + DEFAULT_MAX_OUTPUT_TOKENS <= MAX_CONTEXT_TOKENS)
        // Thinking 예산은 출력 상한 안에 포함되므로 출력 상한보다 작아야 합니다.
        assertTrue(DEFAULT_THINKING_TOKEN_BUDGET < DEFAULT_MAX_OUTPUT_TOKENS)
    }

    @Test
    fun `압축된 안전 규칙은 이전보다 짧다`() = runTest {
        // 자유 대화 시스템 지시문이 4,400자 이하로 유지되는지 확인합니다.
        assertTrue(ChatSystemInstruction.length < 4_400)
        // 학습 모드 지시문은 자유 대화보다 짧아야 합니다.
        assertTrue(SystemInstruction.length < ChatSystemInstruction.length)
    }

    @Test
    fun `RealGemmaTutorEngine은 OCR 텍스트와 학년 지침을 프롬프트로 구성한다`() = runTest {
        val assistant = FakeAssistant()
        val engine = RealGemmaTutorEngine(assistant)
        val request = TutorRequest(
            text = "2 + 3 = ?",
            subject = "수학",
            grade = TutorGrade.MIDDLE,
            options = inferenceOptionsFor(isFollowUp = false, thinkingEnabled = true)
        )

        engine.stream(request).toList()

        val assistantRequest = requireNotNull(assistant.lastRequest)
        assertTrue(assistantRequest.startsNewProblem)
        assertTrue(assistantRequest.textParts[0].contains("[OCR 인식 텍스트]"))
        assertTrue(assistantRequest.textParts[0].contains("2 + 3 = ?"))
        assertTrue(assistantRequest.textParts[1].contains("수학"))
        assertTrue(assistantRequest.textParts[1].contains("중학생"))
        assertTrue(assistantRequest.options.thinkingEnabled)
        assertEquals(DEFAULT_THINKING_TOKEN_BUDGET, assistantRequest.options.thinkingTokenBudget)
    }

    @Test
    fun `후속 질문 프롬프트는 같은 Conversation을 이어 쓰도록 구성한다`() = runTest {
        val assistant = FakeAssistant()
        val engine = RealGemmaTutorEngine(assistant)
        val request = TutorRequest(
            text = "왜 그런가요?",
            subject = "수학",
            grade = TutorGrade.ELEMENTARY,
            options = inferenceOptionsFor(isFollowUp = true, thinkingEnabled = false)
        )

        engine.stream(request).toList()

        val assistantRequest = requireNotNull(assistant.lastRequest)
        assertFalse(assistantRequest.startsNewProblem)
        assertTrue(assistantRequest.textParts.single().contains("왜 그런가요?"))
        assertFalse(assistantRequest.options.thinkingEnabled)
        assertEquals(0, assistantRequest.options.thinkingTokenBudget)
    }

    @Test
    fun `새 과목의 자유 질문은 이전 Conversation을 초기화하고 과목 지침을 포함한다`() = runTest {
        val assistant = FakeAssistant()
        val engine = RealGemmaTutorEngine(assistant)
        val request = TutorRequest(
            text = "조선 시대 세종의 업적을 알려줘",
            subject = "역사",
            grade = TutorGrade.MIDDLE,
            options = inferenceOptionsFor(isFollowUp = true, thinkingEnabled = false),
            isStandaloneQuestion = true
        )

        engine.stream(request).toList()

        val assistantRequest = requireNotNull(assistant.lastRequest)
        assertTrue(assistantRequest.startsNewProblem)
        assertTrue(assistantRequest.textParts.single().contains("선택 과목: 역사"))
        assertTrue(assistantRequest.textParts.single().contains("조선 시대 세종의 업적을 알려줘"))
    }

    @Test
    fun `수학 문제 분석 프롬프트는 수학 지침과 LaTeX 표기 규칙을 포함한다`() = runTest {
        val assistant = FakeAssistant()
        val engine = RealGemmaTutorEngine(assistant)
        val request = TutorRequest(
            text = "이차방정식을 풀어라",
            subject = "수학",
            grade = TutorGrade.MIDDLE,
            options = inferenceOptionsFor(isFollowUp = false, thinkingEnabled = true)
        )

        engine.stream(request).toList()

        val prompt = requireNotNull(assistant.lastRequest).textParts.last()
        assertTrue(prompt.contains("선택 과목: 수학"))
        assertTrue(prompt.contains("풀이 단계마다 근거를 밝히고, 마지막에 답을 검산"))
        assertTrue(prompt.contains("[수식 표기]"))
        assertTrue(prompt.contains("\\frac{a}{b}"))
    }

    @Test
    fun `사회 문제 분석 프롬프트에는 LaTeX 표기 규칙이 없다`() = runTest {
        val assistant = FakeAssistant()
        val engine = RealGemmaTutorEngine(assistant)
        val request = TutorRequest(
            text = "조선 후기 신분제 변화를 설명해라",
            subject = "사회",
            grade = TutorGrade.MIDDLE,
            options = inferenceOptionsFor(isFollowUp = false, thinkingEnabled = true)
        )

        engine.stream(request).toList()

        val prompt = requireNotNull(assistant.lastRequest).textParts.last()
        assertTrue(prompt.contains("선택 과목: 사회"))
        assertTrue(prompt.contains("배경과 인과 관계"))
        assertFalse(prompt.contains("[수식 표기]"))
        assertFalse(prompt.contains("\\frac{a}{b}"))
    }

    @Test
    fun `후속 질문 프롬프트는 선택 과목 지침을 포함한다`() = runTest {
        val assistant = FakeAssistant()
        val engine = RealGemmaTutorEngine(assistant)
        val request = TutorRequest(
            text = "왜 그런가요?",
            subject = "영어",
            grade = TutorGrade.MIDDLE,
            options = inferenceOptionsFor(isFollowUp = true, thinkingEnabled = false)
        )

        engine.stream(request).toList()

        val prompt = requireNotNull(assistant.lastRequest).textParts.single()
        assertTrue(prompt.contains("선택 과목: 영어"))
        assertTrue(prompt.contains("문법·어휘 근거"))
        assertFalse(assistant.lastRequest?.startsNewProblem == true)
    }

    @Test
    fun `목록에 없는 과목은 기본 과목 지침을 사용한다`() = runTest {
        val assistant = FakeAssistant()
        val engine = RealGemmaTutorEngine(assistant)
        val request = TutorRequest(
            text = "한자 급수 시험 범위를 알려줘",
            subject = "한자",
            grade = TutorGrade.MIDDLE,
            options = inferenceOptionsFor(isFollowUp = true, thinkingEnabled = false),
            isStandaloneQuestion = true
        )

        engine.stream(request).toList()

        val prompt = requireNotNull(assistant.lastRequest).textParts.single()
        assertTrue(prompt.contains("선택 과목: 한자"))
        assertTrue(prompt.contains("선택 과목에 맞춰 설명의 초점을 정하고"))
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
