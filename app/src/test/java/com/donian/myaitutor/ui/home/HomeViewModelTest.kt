package com.donian.myaitutor.ui.home

import android.app.Application
import android.content.Context
import com.donian.myaitutor.ai.Assistant
import com.donian.myaitutor.model.ModelDownloadRepository
import com.donian.myaitutor.model.ModelDownloadSnapshot
import com.donian.myaitutor.model.ModelDownloadStatus
import com.donian.myaitutor.ocr.OcrTextRecognizer
import java.io.File
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeViewModelTest {

    private class TestApp : Application(), OcrTextRecognizer {
        var recognizedLines = listOf("인식된 문제")

        override fun getApplicationContext(): Context = this
        override suspend fun recognize(imageFile: File): List<String> = recognizedLines
    }

    private class FakeAssistant : Assistant {
        var analyzeCalls = 0
        var askFollowUpCalls = 0
        var cancelCalls = 0
        var receivedOcrText: String? = null
        override suspend fun analyzeImage(ocrText: String, subject: String, onTextChanged: suspend (String) -> Unit) {
            analyzeCalls++
            receivedOcrText = ocrText
            onTextChanged("분석 결과")
        }
        override suspend fun askFollowUp(question: String, onTextChanged: suspend (String) -> Unit) {
            askFollowUpCalls++
            onTextChanged("후속 답변")
        }
        override suspend fun backendLabel(): String = "TEST"
        override suspend fun getTokenCount(): Int = 0
        override fun cancelGeneration() { cancelCalls++ }
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

    @Test
    fun `analyzeImage는 완료 후 isAnalyzing을 false로 되돌린다`() = runTest {
        val app = TestApp()
        val assistant = FakeAssistant()
        val vm = HomeViewModel(
            app, FakeRepo(), assistant, this, kotlinx.coroutines.Dispatchers.Unconfined,
            false, ocrLogger = {}, stringProvider = { _, _ -> "테스트" }
        )
        val file = File.createTempFile("img", ".jpg")
        vm.analyzeImage(file, "수학")
        testScheduler.advanceUntilIdle()
        assertEquals(false, vm.state.value.isAnalyzing)
        assertEquals("분석 결과", vm.state.value.answer)
        assertEquals("인식된 문제", assistant.receivedOcrText)
        assertEquals("인식된 문제", vm.state.value.ocrText)
        assertEquals(null, vm.state.value.analysisStage)
        file.delete()
    }

    @Test
    fun `OCR 결과가 비어 있으면 Gemma를 호출하지 않는다`() = runTest {
        val app = TestApp().apply { recognizedLines = emptyList() }
        val assistant = FakeAssistant()
        val vm = HomeViewModel(
            app, FakeRepo(), assistant, this, kotlinx.coroutines.Dispatchers.Unconfined,
            false, ocrLogger = {}, stringProvider = { resId, _ ->
                if (resId == com.donian.myaitutor.R.string.status_ocr_empty) {
                    "글자를 찾지 못했습니다. 다시 촬영해 주세요."
                } else {
                    "테스트"
                }
            }
        )
        val file = File.createTempFile("img", ".jpg")
        vm.analyzeImage(file, "수학")
        testScheduler.advanceUntilIdle()
        assertEquals(0, assistant.analyzeCalls)
        assertEquals("글자를 찾지 못했습니다. 다시 촬영해 주세요.", vm.state.value.answer)
        assertEquals(false, vm.state.value.isAnalyzing)
        assertEquals(false, vm.state.value.showFollowUpChoices)
        file.delete()
    }

    @Test
    fun `isAnalyzing 중에는 중복 analyzeImage가 무시된다`() = runTest {
        val assistant = FakeAssistant()
        val app = TestApp()
        val vm = HomeViewModel(
            app, FakeRepo(), assistant, this, kotlinx.coroutines.Dispatchers.Unconfined,
            false, ocrLogger = {}, stringProvider = { _, _ -> "테스트" }
        )
        val file = File.createTempFile("img", ".jpg")
        vm.analyzeImage(file, "수학")
        vm.analyzeImage(file, "수학")
        testScheduler.advanceUntilIdle()
        assertEquals(1, assistant.analyzeCalls)
        file.delete()
    }

    @Test
    fun `모델이 없으면 sendQuestion은 상태 메시지를 남기고 실행하지 않는다`() = runTest {
        val assistant = FakeAssistant()
        val app = TestApp()
        val vm = HomeViewModel(
            app, FakeRepo(ready = false), assistant, this, kotlinx.coroutines.Dispatchers.Unconfined,
            false, ocrLogger = {}, stringProvider = { _, _ -> "테스트" }
        )
        vm.sendQuestion("이게 뭐야?")
        testScheduler.advanceUntilIdle()
        assertEquals(0, assistant.askFollowUpCalls)
        assertTrue(vm.state.value.statusMessage != null)
    }

    @Test
    fun `cancelGeneration은 assistant cancel을 호출한다`() = runTest {
        val assistant = FakeAssistant()
        val app = TestApp()
        val vm = HomeViewModel(
            app, FakeRepo(), assistant, this, kotlinx.coroutines.Dispatchers.Unconfined,
            false, ocrLogger = {}, stringProvider = { _, _ -> "테스트" }
        )
        vm.cancelGeneration()
        assertEquals(1, assistant.cancelCalls)
    }
}
