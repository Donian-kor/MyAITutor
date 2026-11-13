package com.donian.myaitutor.ui.home

import android.content.Context
import android.util.Log
import com.donian.myaitutor.R
import com.donian.myaitutor.ai.Assistant
import com.donian.myaitutor.ai.GemmaAssistant
import com.donian.myaitutor.ai.TutorGrade
import com.donian.myaitutor.ai.followUpAnswerPrefix
import com.donian.myaitutor.camera.ImageCropper
import com.donian.myaitutor.camera.ImageSelection
import com.donian.myaitutor.model.ModelDownloadManager
import com.donian.myaitutor.model.ModelDownloadRepository
import com.donian.myaitutor.model.ModelDownloadStatus
import com.donian.myaitutor.ocr.OcrBlock
import com.donian.myaitutor.ocr.OcrTextRecognizer
import com.donian.myaitutor.tutor.LlmInferenceEngine
import com.donian.myaitutor.tutor.RealGemmaTutorEngine
import com.donian.myaitutor.tutor.TutorParseResult
import com.donian.myaitutor.tutor.TutorRequest
import com.donian.myaitutor.tutor.TutorStreamChunk
import com.donian.myaitutor.tutor.TutorStreamParser
import com.donian.myaitutor.tutor.TutorUiState
import com.donian.myaitutor.tutor.inferenceOptionsFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * 홈 화면의 상태와 비즈니스 로직을 소유합니다.
 * Composable은 상태를 구독하고 이벤트를 위임하기만 합니다.
 *
 * 추론 요청 생성, 활성 Job, generationId, 취소 처리는 모두 이 ViewModel이 소유합니다.
 * [scope]는 외부에서 주입합니다. 프로덕션에서는 ViewModelScope를,
 * 테스트에서는 TestScope를 전달합니다.
 *
 * [tutorEngine]은 기존 [GemmaAssistant] 인스턴스를 감싼 어댑터이며,
 * Engine/Conversation을 새로 만들지 않습니다.
 */
class HomeViewModel(
    private val appContext: Context,
    private val modelManager: ModelDownloadRepository = ModelDownloadManager(appContext),
    assistant: Assistant = GemmaAssistant(appContext),
    private val scope: CoroutineScope,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    private val enablePolling: Boolean = true,
    private val ocrTextRecognizer: OcrTextRecognizer =
        appContext.applicationContext as OcrTextRecognizer,
    private val ocrLogger: (String) -> Unit = { Log.d(TAG, "OCR 결과:\n$it") },
    private val stringProvider: StringProvider = StringProvider { resId, args ->
        appContext.getString(resId, *args)
    },
    private val tutorEngine: LlmInferenceEngine = RealGemmaTutorEngine(assistant)
) {

    private companion object {
        const val TAG = "HomeViewModel"

        /** 네이티브 취소가 끝났다는 확인을 기다리는 최대 시간. */
        const val CANCEL_CONFIRM_TIMEOUT_MS = 15_000L

        /** 종료 시 엔진 해제를 기다리는 최대 시간. */
        const val ENGINE_RELEASE_WAIT_MS = 3_000L
    }

    private val _state = MutableStateFlow(
        HomeUiState(modelSnapshot = modelManager.snapshot())
    )
    val state: StateFlow<HomeUiState> = _state

    private var generationJob: Job? = null
    private var generationId = 0

    /** 엔진 워밍업 중복 실행을 막는 Job. 시작 로딩 화면이 이 상태를 구독합니다. */
    private var warmupJob: Job? = null

    /** 종료 시 엔진이 한 번만 해제되도록 보장합니다. */
    private var isClosed = false

    /** 사용자가 중단을 요청한 generationId. 정상 오류와 취소를 구분합니다. */
    private var userCancelledGeneration: Int? = null

    /** 후속 질문 요청에 사용할 마지막 과목. 과목 상태 자체는 화면이 소유합니다. */
    private var lastSubject: String = ""

    private val tutorParser = TutorStreamParser()

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
                        snapshot.status == ModelDownloadStatus.PAUSED ||
                        snapshot.status == ModelDownloadStatus.VERIFYING
                    delay(if (busy) 1_000L else 5_000L)
                }
            }
        }
    }

    /**
     * 앱 종료 시 엔진 자원을 해제합니다. scope 자체는 소유자가 관리합니다.
     *
     * [onCleared]와 [MainActivity.onDestroy]에서 모두 호출될 수 있으므로
     * [isClosed]로 한 번만 해제하도록 보장합니다.
     *
     * 종료 시점에는 viewModelScope가 이미 취소된 상태일 수 있으므로
     * scope.launch 대신 동기적으로 release()를 끝까지 실행합니다.
     * 진행 중인 추론이 있으면 짧게 대기한 뒤 엔진을 해제합니다.
     */
    fun close() {
        if (isClosed) return
        isClosed = true
        generationId++
        userCancelledGeneration = null
        tutorEngine.cancelGeneration()
        warmupJob?.cancel()
        warmupJob = null
        generationJob?.cancel()
        generationJob = null
        runCatching {
            runBlocking {
                withTimeoutOrNull(ENGINE_RELEASE_WAIT_MS) {
                    tutorEngine.awaitGenerationStopped(ENGINE_RELEASE_WAIT_MS)
                }
                tutorEngine.release()
            }
        }
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

    /** 학년 변경은 기존 HomeViewModel 메서드로 처리하고 HomeUiState에 단일 저장합니다. */
    fun selectGrade(grade: TutorGrade) {
        _state.value = _state.value.copy(selectedGrade = grade)
    }

    fun resetForNewSubject() {
        // 진행 중인 추론이 있으면 네이티브 취소까지 요청합니다.
        if (_state.value.isAnalyzing) {
            tutorEngine.cancelGeneration()
        }
        generationJob?.cancel()
        generationJob = null
        generationId++
        userCancelledGeneration = null
        tutorParser.reset()
        // 이전 캡처 이미지는 삭제 (과목 변경 시)
        _state.value.capturedImage?.let { file ->
            runCatching { if (file.exists()) file.delete() }
        }
        _state.value = _state.value.copy(
            capturedImage = null,
            cameraCapturedImage = null,
            preprocessedImage = null,
            answer = "",
            inputText = "",
            analysisStage = null,
            ocrText = "",
            ocrBlocks = emptyList(),
            showFollowUpChoices = false,
            statusMessage = null,
            isAnalyzing = false,
            tutorState = TutorUiState()
        )
    }

    fun confirmCapture(image: File) {
        if (_state.value.isAnalyzing) {
            tutorEngine.cancelGeneration()
            generationJob?.cancel()
            generationJob = null
            generationId++
            userCancelledGeneration = null
        }
        tutorParser.reset()
        // 이전 캡처 이미지가 있으면 삭제 (새 사진으로 교체)
        _state.value.capturedImage?.let { oldImage ->
            if (oldImage != image) {
                runCatching { if (oldImage.exists()) oldImage.delete() }
            }
        }
        _state.value = _state.value.copy(
            capturedImage = image,
            cameraCapturedImage = null,
            cameraError = null,
            preprocessedImage = null,
            analysisStage = null,
            ocrText = "",
            ocrBlocks = emptyList(),
            answer = "",
            showFollowUpChoices = false,
            tutorState = TutorUiState()
        )
    }

    fun onCameraError(message: String) {
        _state.value = _state.value.copy(cameraError = message)
    }

    fun setCameraCapturedImage(image: File?) {
        _state.value = _state.value.copy(cameraCapturedImage = image)
    }

    fun clearCameraCapturedImage() {
        _state.value.cameraCapturedImage?.let { file ->
            runCatching { if (file.exists()) file.delete() }
        }
        _state.value.preprocessedImage?.let { file ->
            runCatching { if (file.exists()) file.delete() }
        }
        _state.value = _state.value.copy(cameraCapturedImage = null, cameraError = null, preprocessedImage = null)
    }

    fun analyzeImage(image: File, subject: String, selection: ImageSelection? = null) {
        if (_state.value.isAnalyzing) return
        if (_state.value.tutorState.requiresRecovery) {
            _state.value = _state.value.copy(
                statusMessage = _state.value.tutorState.errorMessage
            )
            return
        }

        lastSubject = subject
        val currentGeneration = ++generationId
        userCancelledGeneration = null
        tutorParser.reset()
        val initialTutorState = tutorParser.startOcr()

        _state.value = _state.value.copy(
            isAnalyzing = true,
            answer = "",
            analysisStage = ImageAnalysisStage.OCR,
            ocrText = "",
            ocrBlocks = emptyList(),
            showFollowUpChoices = false,
            statusMessage = null,
            tutorState = initialTutorState
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

                // 크롭·전처리가 끝난 이미지를 그대로 보존해 "OCR 전달 이미지"로 노출합니다.
                // 별도 복사를 만들지 않고, 이 시점부터 이 파일의 소유권은 preprocessedImage가 가집니다.
                if (currentGeneration == generationId) {
                    val previousPreview = _state.value.preprocessedImage
                    if (previousPreview != null &&
                        previousPreview != image &&
                        previousPreview != normalizedImage
                    ) {
                        runCatching { if (previousPreview.exists()) previousPreview.delete() }
                    }
                    _state.value = _state.value.copy(preprocessedImage = imageToAnalyze)
                }

                val ocrBlocks = ocrTextRecognizer.recognize(imageToAnalyze)
                // TODO: OCR 결과 텍스트의 Logcat 출력을 디버깅 종료 후 제거하세요.
                val ocrText = formatOcrBlocksForPrompt(ocrBlocks)
                ocrLogger(ocrText)
                if (ocrText.isBlank()) {
                    if (currentGeneration == generationId) {
                        val message = stringProvider.get(R.string.status_ocr_empty)
                        val tutorState = tutorParser.onChunk(TutorStreamChunk.Error(message)).state
                        _state.value = _state.value.copy(
                            answer = message,
                            tutorState = tutorState
                        )
                    }
                    return@launch
                }

                // OCR 신뢰도 탐지: 평균 신뢰도가 낮거나(0.6 미만) 매우 낮은 블록(0.4 미만)이 존재하는 경우 안내 메시지 설정
                val validConfidences = ocrBlocks.mapNotNull { it.confidence }
                val avgConfidence = if (validConfidences.isNotEmpty()) validConfidences.average() else 1.0
                val isLowConfidence =
                    validConfidences.isNotEmpty() && (avgConfidence < 0.6 || validConfidences.any { it < 0.4f })

                if (currentGeneration == generationId) {
                    _state.value = _state.value.copy(
                        ocrText = ocrText,
                        ocrBlocks = ocrBlocks,
                        analysisStage = ImageAnalysisStage.GEMMA,
                        statusMessage = if (isLowConfidence) {
                            stringProvider.get(R.string.status_ocr_low_confidence)
                        } else null,
                        tutorState = tutorParser.onOcrCompleted()
                    )
                }

                val grade = _state.value.selectedGrade
                val request = TutorRequest(
                    text = ocrText,
                    subject = subject,
                    grade = grade,
                    options = inferenceOptionsFor(grade, isFollowUp = false)
                )
                if (currentGeneration == generationId) {
                    _state.value = _state.value.copy(tutorState = tutorParser.onAnalysisStarted())
                }
                receivedAnswer = collectTutorStream(request, currentGeneration)

                if (currentGeneration == generationId) {
                    val label = tutorEngine.backendLabel()
                    _state.value = _state.value.copy(
                        backendLabel = stringProvider.get(R.string.backend_label, label)
                    )
                    val tutorState = _state.value.tutorState
                    completed = tutorState.isCompleted && receivedAnswer
                    if (!receivedAnswer && !tutorState.isCancelled && tutorState.errorMessage == null) {
                        _state.value = _state.value.copy(
                            answer = stringProvider.get(R.string.status_answer_empty_image),
                            statusMessage = stringProvider.get(R.string.status_analysis_empty)
                        )
                    }
                }
            } catch (error: CancellationException) {
                if (currentGeneration == generationId) {
                    val tutorState = if (userCancelledGeneration == currentGeneration) {
                        tutorParser.onCancellationConfirmed()
                    } else {
                        _state.value.tutorState
                    }
                    _state.value = _state.value.copy(tutorState = tutorState)
                }
                throw error
            } catch (error: Exception) {
                if (currentGeneration == generationId) {
                    val message = error.localizedMessage
                        ?: stringProvider.get(R.string.status_analysis_error)
                    _state.value = _state.value.copy(
                        tutorState = tutorParser.onChunk(TutorStreamChunk.Error(message)).state
                    )
                }
            } finally {
                // 1) 원본 촬영 이미지는 재분석을 위해 보존
                // 2) 임시 파일만 정리 (전처리, 크롭 결과 - preprocessedImage가 가리키는 파일은 보존)
                if (normalizedImage != null && normalizedImage != image && normalizedImage != _state.value.preprocessedImage) {
                    runCatching { if (normalizedImage.exists()) normalizedImage.delete() }
                }
                if (imageToAnalyze != image && imageToAnalyze != _state.value.preprocessedImage) {
                    runCatching { if (imageToAnalyze.exists()) imageToAnalyze.delete() }
                }
                // 3) UI 상태 복구를 부가 작업보다 먼저 보장
                if (currentGeneration == generationId) {
                    recoverUiAfterGeneration(
                        completed = completed,
                        receivedAnswer = receivedAnswer,
                        showChoicesAfterResponse = true
                    )
                }
            }
        }
    }

    fun sendQuestion(question: String, showChoicesAfterResponse: Boolean = false) {
        if (question.isBlank() || _state.value.isAnalyzing) return
        if (_state.value.tutorState.requiresRecovery) {
            _state.value = _state.value.copy(
                statusMessage = _state.value.tutorState.errorMessage
            )
            return
        }
        if (_state.value.modelSnapshot.status != ModelDownloadStatus.READY) {
            _state.value = _state.value.copy(
                statusMessage = stringProvider.get(R.string.status_model_required)
            )
            return
        }

        val currentGeneration = ++generationId
        userCancelledGeneration = null
        tutorParser.reset()
        val previousAnswer = _state.value.answer
        val pendingAnswer = followUpAnswerPrefix(previousAnswer, question)
        _state.value = _state.value.copy(
            isAnalyzing = true,
            answer = pendingAnswer,
            showFollowUpChoices = false,
            inputText = "",
            statusMessage = null,
            analysisStage = null,
            tutorState = tutorParser.startFollowUp()
        )
        generationJob = scope.launch {
            var completed = false
            var receivedAnswer = false
            try {
                val grade = _state.value.selectedGrade
                val request = TutorRequest(
                    text = question,
                    subject = lastSubject.ifBlank { "기타" },
                    grade = grade,
                    options = inferenceOptionsFor(grade, isFollowUp = true)
                )
                _state.value = _state.value.copy(tutorState = tutorParser.onAnalysisStarted())
                receivedAnswer = collectTutorStream(request, currentGeneration)

                if (currentGeneration == generationId) {
                    val label = tutorEngine.backendLabel()
                    _state.value = _state.value.copy(
                        backendLabel = stringProvider.get(R.string.backend_label, label)
                    )
                    val tutorState = _state.value.tutorState
                    completed = tutorState.isCompleted && receivedAnswer
                    if (!receivedAnswer && !tutorState.isCancelled && tutorState.errorMessage == null) {
                        _state.value = _state.value.copy(
                            statusMessage = stringProvider.get(R.string.status_answer_empty_followup)
                        )
                    }
                }
            } catch (error: CancellationException) {
                if (currentGeneration == generationId) {
                    val tutorState = if (userCancelledGeneration == currentGeneration) {
                        tutorParser.onCancellationConfirmed()
                    } else {
                        _state.value.tutorState
                    }
                    _state.value = _state.value.copy(tutorState = tutorState)
                }
                throw error
            } catch (error: Exception) {
                if (currentGeneration == generationId) {
                    val message = error.localizedMessage
                        ?: stringProvider.get(R.string.status_question_error)
                    _state.value = _state.value.copy(
                        tutorState = tutorParser.onChunk(TutorStreamChunk.Error(message)).state
                    )
                }
            } finally {
                if (currentGeneration == generationId) {
                    recoverUiAfterGeneration(
                        completed = completed,
                        receivedAnswer = receivedAnswer,
                        showChoicesAfterResponse = showChoicesAfterResponse
                    )
                }
            }
        }
    }

    /**
     * 튜터 스트림을 수집합니다. 답변 증가분은 즉시 HomeUiState.answer에만 이어 붙입니다.
     * @return 실제 답변 조각을 하나라도 받았는지 여부
     */
    private suspend fun collectTutorStream(
        request: TutorRequest,
        currentGeneration: Int
    ): Boolean {
        var receivedAnswer = false
        tutorEngine.stream(request).collect { chunk ->
            if (currentGeneration != generationId) return@collect
            val result = tutorParser.onChunk(chunk)
            applyTutorResult(result)
            if (!result.answerTextDelta.isNullOrEmpty()) {
                receivedAnswer = true
            }
        }
        if (currentGeneration == generationId && !tutorParser.state().isTerminal) {
            val endResult = tutorParser.onStreamEnded()
            applyTutorResult(endResult)
            if (!endResult.answerTextDelta.isNullOrEmpty()) receivedAnswer = true
        }
        return receivedAnswer
    }

    /** 파서 결과를 HomeUiState.answer와 tutorState에 원자적으로 합성합니다. */
    private fun applyTutorResult(result: TutorParseResult) {
        val current = _state.value
        val delta = result.answerTextDelta
        _state.value = current.copy(
            answer = if (delta.isNullOrEmpty()) current.answer else current.answer + delta,
            tutorState = result.state
        )
    }

    /** 생성 종료 후 UI 복구. 토큰 조회 실패가 상태 복구를 막지 않습니다. */
    private suspend fun recoverUiAfterGeneration(
        completed: Boolean,
        receivedAnswer: Boolean,
        showChoicesAfterResponse: Boolean
    ) {
        val tokens = try {
            tutorEngine.getTokenCount()
        } catch (_: Exception) {
            0
        }
        var tutorState = _state.value.tutorState
        tutorState = when {
            userCancelledGeneration == generationId && !tutorState.isCancelled ->
                tutorParser.onCancellationConfirmed()
            tutorState.isGenerating -> tutorParser.onStreamEnded().state
            else -> tutorState
        }
        _state.value = _state.value.copy(
            isAnalyzing = false,
            analysisStage = null,
            showFollowUpChoices = completed && receivedAnswer && showChoicesAfterResponse,
            tokenCount = tokens,
            tutorState = tutorState
        )
        generationJob = null
    }

    /**
     * 사용자 중단. 네이티브 취소 완료를 확인한 뒤에만 수집 Job을 정리합니다.
     * 완료를 확인하지 못하면 새 추론을 금지하고 복구 상태로 전환합니다.
     */
    fun cancelGeneration() {
        val job = generationJob ?: return
        if (!_state.value.isAnalyzing) return
        val currentGeneration = generationId
        if (userCancelledGeneration == currentGeneration) return

        userCancelledGeneration = currentGeneration
        _state.value = _state.value.copy(
            tutorState = tutorParser.onCancellationRequested()
        )
        tutorEngine.cancelGeneration()

        scope.launch {
            val confirmed = tutorEngine.awaitGenerationStopped(CANCEL_CONFIRM_TIMEOUT_MS)
            if (currentGeneration != generationId) return@launch
            if (confirmed) {
                // finally가 취소 상태와 isAnalyzing=false를 복구합니다.
                job.cancel()
            } else {
                // 안전하지 않은 상태에서는 다음 추론을 시작하지 않습니다.
                generationId++
                job.cancel()
                _state.value = _state.value.copy(
                    isAnalyzing = false,
                    analysisStage = null,
                    statusMessage = null,
                    tutorState = tutorParser.onCancellationUnconfirmed()
                )
                generationJob = null
            }
        }
    }

    /** 취소 완료를 확인하지 못한 상태에서 Conversation만 안전하게 교체합니다. */
    fun recoverTutor() {
        scope.launch {
            if (_state.value.isAnalyzing) {
                tutorEngine.cancelGeneration()
                generationJob?.cancel()
                generationJob = null
            }
            generationId++
            userCancelledGeneration = null
            var failureMessage: String? = null
            try {
                tutorEngine.resetConversation()
            } catch (error: Exception) {
                failureMessage = error.localizedMessage
            }
            tutorParser.reset()
            _state.value = _state.value.copy(
                isAnalyzing = false,
                analysisStage = null,
                statusMessage = failureMessage,
                tutorState = TutorUiState()
            )
        }
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
            warmupJob?.cancel()
            warmupJob = null
            tutorEngine.release()
            modelManager.deleteModel()
            _state.value = _state.value.copy(
                modelSnapshot = modelManager.snapshot(),
                backendLabel = stringProvider.get(R.string.status_model_deleted),
                warmup = ModelWarmupState.IDLE
            )
        }
    }

    /**
     * 모델 파일이 준비된 경우 엔진/대화를 미리 메모리에 올립니다.
     *
     * 시작 로딩 화면이 [HomeUiState.warmup]을 구독해 진행 상태를 그립니다.
     * 중복 호출되어도 Job은 하나만 실행되며, 이미 준비된 경우 아무 것도 하지 않습니다.
     */
    fun preWarmIfNeeded() {
        val current = _state.value
        if (current.warmup == ModelWarmupState.LOADING || current.warmup == ModelWarmupState.READY) return
        if (warmupJob?.isActive == true) return
        if (current.modelSnapshot.status != ModelDownloadStatus.READY) return

        _state.value = _state.value.copy(warmup = ModelWarmupState.LOADING)
        warmupJob = scope.launch {
            try {
                tutorEngine.preWarm()
                _state.value = _state.value.copy(
                    warmup = ModelWarmupState.READY,
                    backendLabel = stringProvider.get(
                        R.string.backend_label,
                        tutorEngine.backendLabel()
                    )
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    warmup = ModelWarmupState.FAILED,
                    statusMessage = error.localizedMessage
                        ?: stringProvider.get(R.string.status_model_prewarm_failed)
                )
            } finally {
                warmupJob = null
            }
        }
    }

    /** 로딩 화면의 "다시 시도" 버튼. 실패 상태를 초기화하고 워밍업을 다시 시작합니다. */
    fun retryModelWarmup() {
        warmupJob?.cancel()
        warmupJob = null
        _state.value = _state.value.copy(warmup = ModelWarmupState.IDLE)
        preWarmIfNeeded()
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
                preprocessedImage = null,
                statusMessage = if (clearedCount > 0) {
                    stringProvider.get(R.string.status_cache_cleared, clearedCount)
                } else {
                    stringProvider.get(R.string.status_cache_empty)
                }
            )
        }
    }

    /**
     * OCR 블록을 Gemma에 전달할 텍스트로 포맷팅합니다.
     * 블록의 위치, 신뢰도, 유형 정보를 활용해 구조화된 텍스트를 만듭니다.
     */
    private fun formatOcrBlocksForPrompt(blocks: List<OcrBlock>): String {
        if (blocks.isEmpty()) return ""
        return blocks.map { block ->
            when (block.type) {
                OcrBlock.OcrBlockType.FORMULA -> {
                    // 수식은 별도 표시로 구분
                    "[수식] ${block.text.trim()}"
                }
                OcrBlock.OcrBlockType.TABLE -> {
                    // 표 구조 보존을 위해 [표] 태그 부여
                    val text = block.text.trim()
                    if (text.startsWith("[표]")) text else "[표]\n$text"
                }
                OcrBlock.OcrBlockType.IMAGE -> {
                    // 이미지 설명은 [이미지] 태그로 표시
                    "[이미지] ${block.text.trim()}"
                }
                OcrBlock.OcrBlockType.TEXT -> {
                    // 일반 텍스트는 그대로
                    block.text.trim()
                }
                else -> {
                    // 알 수 없거나 다른 유형은 그대로
                    block.text.trim()
                }
            }
        }.joinToString("\n")
    }
}
