package com.donian.myaitutor.ui.home

import android.content.Context
import android.util.Log
import com.donian.myaitutor.R
import com.donian.myaitutor.ai.Assistant
import com.donian.myaitutor.ai.GemmaAssistant
import com.donian.myaitutor.camera.ImageCropper
import com.donian.myaitutor.camera.ImageSelection
import com.donian.myaitutor.model.ModelDownloadManager
import com.donian.myaitutor.model.ModelDownloadRepository
import com.donian.myaitutor.model.ModelDownloadSnapshot
import com.donian.myaitutor.model.ModelDownloadStatus
import com.donian.myaitutor.ocr.OcrTextRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 홈 화면의 상태와 비즈니스 로직을 소유합니다.
 * Composable은 상태를 구독하고 이벤트를 위임하기만 합니다.
 *
 * [scope]는 외부에서 주입합니다. 프로덕션에서는 Composable의
 * rememberCoroutineScope()를, 테스트에서는 TestScope를 전달합니다.
 */
class HomeViewModel(
    private val appContext: Context,
    private val modelManager: ModelDownloadRepository = ModelDownloadManager(appContext),
    private val assistant: Assistant = GemmaAssistant(appContext),
    private val scope: CoroutineScope,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    private val enablePolling: Boolean = true,
    private val ocrTextRecognizer: OcrTextRecognizer =
        appContext.applicationContext as OcrTextRecognizer,
    private val ocrLogger: (String) -> Unit = { Log.d(TAG, "OCR 결과:\n$it") },
    private val stringProvider: StringProvider = StringProvider { resId, args -> appContext.getString(resId, *args) }
) {

    private companion object {
        const val TAG = "HomeViewModel"
    }

    private val _state = kotlinx.coroutines.flow.MutableStateFlow(
        HomeUiState(modelSnapshot = modelManager.snapshot())
    )
    val state: kotlinx.coroutines.flow.StateFlow<HomeUiState> = _state

    private var generationJob: Job? = null
    private var generationId = 0

    init {
        if (enablePolling) {
            scope.launch {
                while (isActive) {
                    val snapshot = withContext(ioDispatcher) {
                        if (modelManager.needsHashVerification()) {
                            modelManager.verifyModelHash()
                        }
                        modelManager.snapshot()
                    }
                    _state.value = _state.value.copy(modelSnapshot = snapshot)
                    val busy = snapshot.status == ModelDownloadStatus.DOWNLOADING ||
                        snapshot.status == ModelDownloadStatus.QUEUED ||
                        snapshot.status == ModelDownloadStatus.PAUSED
                    delay(if (busy) 1_000L else 5_000L)
                }
            }
        }
    }

    /**
     * 앱/화면 종료 시 엔진 자원을 해제합니다. scope 자체는 소유자가 관리합니다.
     */
    fun close() {
        generationJob?.cancel()
        scope.launch { runCatching { assistant.release() } }
    }

    fun onInputChange(text: String) {
        _state.value = _state.value.copy(inputText = text)
    }

    fun dismissStatus() {
        _state.value = _state.value.copy(statusMessage = null)
    }

    fun setStatusMessage(message: String?) {
        _state.value = _state.value.copy(statusMessage = message)
    }

    fun resetForNewSubject() {
        _state.value = _state.value.copy(
            capturedImage = null,
            answer = "",
            inputText = "",
            analysisStage = null,
            ocrText = "",
            showFollowUpChoices = false,
            statusMessage = null
        )
    }

    fun confirmCapture(image: File) {
        _state.value = _state.value.copy(
            capturedImage = image,
            cameraCapturedImage = null,
            cameraError = null,
            analysisStage = null,
            ocrText = ""
        )
    }

    fun onCameraError(message: String) {
        _state.value = _state.value.copy(cameraError = message)
    }

    fun setCameraCapturedImage(image: File?) {
        _state.value = _state.value.copy(cameraCapturedImage = image)
    }

    fun clearCameraCapturedImage() {
        _state.value = _state.value.copy(cameraCapturedImage = null, cameraError = null)
    }

    fun analyzeImage(image: File, subject: String, selection: ImageSelection? = null) {
        if (_state.value.isAnalyzing) return
        val currentGeneration = ++generationId
        _state.value = _state.value.copy(
            isAnalyzing = true,
            answer = "",
            analysisStage = ImageAnalysisStage.OCR,
            ocrText = "",
            showFollowUpChoices = false,
            statusMessage = null
        )
        generationJob = scope.launch {
            var completed = false
            var receivedAnswer = false
            var imageToAnalyze = image
            var normalizedImage: File? = null
            try {
                if (selection != null) {
                    normalizedImage = withContext(ioDispatcher) {
                        ImageCropper.crop(image, selection)
                    }
                    imageToAnalyze = normalizedImage
                } else {
                    normalizedImage = withContext(ioDispatcher) {
                        com.donian.myaitutor.camera.ImageNormalizer.normalize(image)
                    }
                    imageToAnalyze = normalizedImage ?: image
                }
                val ocrText = ocrTextRecognizer.recognize(imageToAnalyze).joinToString("\n")
                // TODO: OCR 결과 텍스트의 Logcat 출력을 디버깅 종료 후 제거하세요.
                ocrLogger(ocrText)
                if (ocrText.isBlank()) {
                    if (currentGeneration == generationId) {
                        _state.value = _state.value.copy(
                            answer = stringProvider.get(R.string.status_ocr_empty)
                        )
                    }
                    return@launch
                }
                if (currentGeneration == generationId) {
                    _state.value = _state.value.copy(
                        ocrText = ocrText,
                        analysisStage = ImageAnalysisStage.GEMMA
                    )
                }
                assistant.analyzeImage(ocrText, subject) { newText ->
                    if (currentGeneration == generationId) {
                        _state.value = _state.value.copy(answer = newText)
                        receivedAnswer = newText.isNotBlank()
                    }
                }
                if (currentGeneration == generationId) {
                    val label = assistant.backendLabel()
                    _state.value = _state.value.copy(backendLabel = stringProvider.get(R.string.backend_label, label))
                    completed = true
                    if (!receivedAnswer) {
                        _state.value = _state.value.copy(
                            answer = stringProvider.get(R.string.status_answer_empty_image),
                            statusMessage = stringProvider.get(R.string.status_analysis_empty)
                        )
                    }
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                if (currentGeneration == generationId) {
                    _state.value = _state.value.copy(
                        statusMessage = error.localizedMessage ?: stringProvider.get(R.string.status_analysis_error)
                    )
                }
            } finally {
                runCatching { if (image.exists()) image.delete() }
                if (normalizedImage != null && normalizedImage != image) {
                    runCatching { if (normalizedImage.exists()) normalizedImage.delete() }
                }
                if (imageToAnalyze != image && imageToAnalyze != normalizedImage) {
                    runCatching { if (imageToAnalyze.exists()) imageToAnalyze.delete() }
                }
                if (currentGeneration == generationId) {
                    val tokens = assistant.getTokenCount()
                    _state.value = _state.value.copy(
                        isAnalyzing = false,
                        analysisStage = null,
                        showFollowUpChoices = completed && receivedAnswer,
                        tokenCount = tokens
                    )
                    generationJob = null
                }
            }
        }
    }

    fun sendQuestion(question: String, showChoicesAfterResponse: Boolean = false) {
        if (question.isBlank() || _state.value.isAnalyzing) return
        if (_state.value.modelSnapshot.status != ModelDownloadStatus.READY) {
            _state.value = _state.value.copy(
                statusMessage = stringProvider.get(R.string.status_model_required)
            )
            return
        }
        val currentGeneration = ++generationId
        val previousAnswer = _state.value.answer
        val pendingAnswer = com.donian.myaitutor.ai.followUpAnswerPrefix(previousAnswer, question)
        _state.value = _state.value.copy(
            isAnalyzing = true,
            answer = pendingAnswer,
            showFollowUpChoices = false,
            inputText = "",
            statusMessage = null
        )
        generationJob = scope.launch {
            var completed = false
            var receivedAnswer = false
            try {
                assistant.askFollowUp(question) { newText ->
                    if (currentGeneration == generationId) {
                        _state.value = _state.value.copy(answer = pendingAnswer + newText)
                        receivedAnswer = newText.isNotBlank()
                    }
                }
                if (currentGeneration == generationId) {
                    val label = assistant.backendLabel()
                    _state.value = _state.value.copy(backendLabel = stringProvider.get(R.string.backend_label, label))
                    completed = true
                    if (!receivedAnswer) {
                        _state.value = _state.value.copy(
                            statusMessage = stringProvider.get(R.string.status_answer_empty_followup)
                        )
                    }
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                if (currentGeneration == generationId) {
                    _state.value = _state.value.copy(
                        statusMessage = error.localizedMessage ?: stringProvider.get(R.string.status_question_error)
                    )
                }
            } finally {
                if (currentGeneration == generationId) {
                    val tokens = assistant.getTokenCount()
                    _state.value = _state.value.copy(
                        isAnalyzing = false,
                        showFollowUpChoices = completed && receivedAnswer && showChoicesAfterResponse,
                        tokenCount = tokens
                    )
                    generationJob = null
                }
            }
        }
    }

    fun cancelGeneration() {
        generationJob?.cancel()
        assistant.cancelGeneration()
    }

    fun startDownload() {
        scope.launch {
            runCatching { modelManager.startDownload() }
                .onSuccess {
                    _state.value = _state.value.copy(statusMessage = stringProvider.get(R.string.status_download_started))
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        statusMessage = it.localizedMessage ?: stringProvider.get(R.string.status_download_failed)
                    )
                }
            _state.value = _state.value.copy(modelSnapshot = modelManager.snapshot())
        }
    }

    fun deleteModel() {
        scope.launch {
            assistant.release()
            modelManager.deleteModel()
            _state.value = _state.value.copy(
                modelSnapshot = modelManager.snapshot(),
                backendLabel = stringProvider.get(R.string.status_model_deleted)
            )
        }
    }

    fun preWarmIfNeeded() {
        scope.launch {
            if (_state.value.modelSnapshot.status == ModelDownloadStatus.READY) {
                runCatching { assistant.preWarm() }
                    .onFailure { error ->
                        _state.value = _state.value.copy(
                            statusMessage = error.localizedMessage
                                ?: stringProvider.get(R.string.status_model_prewarm_failed)
                        )
                    }
                _state.value = _state.value.copy(backendLabel = stringProvider.get(R.string.backend_label, assistant.backendLabel()))
            }

        }
    }

    fun clearCache() {
        scope.launch {
            val clearedCount = withContext(ioDispatcher) {
                var count = 0
                val cacheDir = appContext.cacheDir
                cacheDir.listFiles()?.forEach { file ->
                    if (file.isFile && (file.name.startsWith("focus_") || file.name.endsWith(".jpg") || file.name.endsWith(".png"))) {
                        if (file.delete()) count++
                    }
                }
                count
            }
            _state.value = _state.value.copy(
                statusMessage = if (clearedCount > 0) {
                    stringProvider.get(R.string.status_cache_cleared, clearedCount)
                } else {
                    stringProvider.get(R.string.status_cache_empty)
                }
            )
        }
    }
}
