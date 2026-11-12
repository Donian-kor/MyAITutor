package com.donian.myaitutor.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.donian.myaitutor.ai.GemmaAssistant
import com.donian.myaitutor.model.ModelDownloadManager
import com.donian.myaitutor.model.ModelDownloadSnapshot
import com.donian.myaitutor.model.ModelDownloadStatus
import kotlinx.coroutines.CancellationException
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
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val modelManager = ModelDownloadManager(appContext)
    private val assistant = GemmaAssistant(appContext)

    private val _state = kotlinx.coroutines.flow.MutableStateFlow(
        HomeUiState(modelSnapshot = modelManager.snapshot())
    )
    val state: kotlinx.coroutines.flow.StateFlow<HomeUiState> = _state

    private var generationJob: Job? = null
    private var generationId = 0

    init {
        // 다운로드 상태를 주기적으로 갱신합니다. (C-01)
        viewModelScope.launch {
            while (isActive) {
                val snapshot = withContext(Dispatchers.IO) {
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

    fun resetForNewSubject() {
        _state.value = _state.value.copy(
            capturedImage = null,
            answer = "",
            inputText = "",
            showFollowUpChoices = false,
            statusMessage = null
        )
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

    fun confirmCapture(image: File) {
        _state.value = _state.value.copy(
            capturedImage = image,
            cameraCapturedImage = null,
            cameraError = null
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

    fun analyzeImage(image: File, subject: String) {
        if (_state.value.isAnalyzing) return
        val currentGeneration = ++generationId
        _state.value = _state.value.copy(
            isAnalyzing = true,
            answer = "촬영한 문제를 분석하고 있습니다.",
            showFollowUpChoices = false,
            statusMessage = null
        )
        generationJob = viewModelScope.launch {
            var completed = false
            var receivedAnswer = false
            val imageToAnalyze = withContext(Dispatchers.IO) {
                com.donian.myaitutor.camera.ImageNormalizer.normalize(image) ?: image
            }
            try {
                assistant.analyzeImage(imageToAnalyze, subject) { newText ->
                    if (currentGeneration == generationId) {
                        _state.value = _state.value.copy(answer = newText)
                        receivedAnswer = newText.isNotBlank()
                    }
                }
                if (currentGeneration == generationId) {
                    val label = assistant.backendLabel()
                    _state.value = _state.value.copy(backendLabel = "실행 백엔드: $label")
                    completed = true
                    if (!receivedAnswer) {
                        _state.value = _state.value.copy(
                            answer = "AI 답변을 받지 못했습니다. 다시 촬영해 분석을 시도해 주세요.",
                            statusMessage = "문제 분석 결과가 비어 있어 선택지를 표시하지 않았습니다."
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (currentGeneration == generationId) {
                    _state.value = _state.value.copy(
                        statusMessage = error.localizedMessage ?: "AI 분석 중 오류가 발생했습니다."
                    )
                }
            } finally {
                runCatching { if (image.exists()) image.delete() }
                if (imageToAnalyze != image) runCatching { if (imageToAnalyze.exists()) imageToAnalyze.delete() }
                if (currentGeneration == generationId) {
                    _state.value = _state.value.copy(
                        isAnalyzing = false,
                        showFollowUpChoices = completed && receivedAnswer
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
                statusMessage = "AI 모델을 먼저 설치해 주세요."
            )
            return
        }
        val currentGeneration = ++generationId
        val previousAnswer = _state.value.answer
        val pendingAnswer = if (previousAnswer.isBlank()) {
            "학생: $question\n\nAI 답변: "
        } else {
            "$previousAnswer\n\n학생: $question\n\nAI 답변: "
        }
        _state.value = _state.value.copy(
            isAnalyzing = true,
            answer = pendingAnswer,
            showFollowUpChoices = false,
            inputText = "",
            statusMessage = null
        )
        generationJob = viewModelScope.launch {
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
                    _state.value = _state.value.copy(backendLabel = "실행 백엔드: $label")
                    completed = true
                    if (!receivedAnswer) {
                        _state.value = _state.value.copy(
                            statusMessage = "AI 답변을 받지 못했습니다. 다시 질문해 주세요."
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (currentGeneration == generationId) {
                    _state.value = _state.value.copy(
                        statusMessage = error.localizedMessage ?: "질문 처리 중 오류가 발생했습니다."
                    )
                }
            } finally {
                if (currentGeneration == generationId) {
                    _state.value = _state.value.copy(
                        isAnalyzing = false,
                        showFollowUpChoices = completed && receivedAnswer && showChoicesAfterResponse
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
        viewModelScope.launch {
            runCatching { modelManager.startDownload() }
                .onSuccess {
                    _state.value = _state.value.copy(statusMessage = "Wi-Fi에서 AI 모델 다운로드가 시작됩니다.")
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        statusMessage = it.localizedMessage ?: "다운로드를 시작하지 못했습니다."
                    )
                }
            _state.value = _state.value.copy(modelSnapshot = modelManager.snapshot())
        }
    }

    fun deleteModel() {
        viewModelScope.launch {
            assistant.release()
            modelManager.deleteModel()
            _state.value = _state.value.copy(
                modelSnapshot = modelManager.snapshot(),
                backendLabel = "모델이 모두 삭제되었습니다."
            )
        }
    }

    fun preWarmIfNeeded() {
        viewModelScope.launch {
            if (_state.value.modelSnapshot.status == ModelDownloadStatus.READY) {
                runCatching { assistant.preWarm() }
                _state.value = _state.value.copy(backendLabel = "실행 백엔드: ${assistant.backendLabel()}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        generationJob?.cancel()
        viewModelScope.launch { runCatching { assistant.release() } }
    }
}
