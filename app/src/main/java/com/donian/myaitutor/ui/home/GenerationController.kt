package com.donian.myaitutor.ui.home

import com.donian.myaitutor.R
import com.donian.myaitutor.camera.ImageCropper
import com.donian.myaitutor.camera.ImageSelection
import com.donian.myaitutor.model.ModelDownloadStatus
import com.donian.myaitutor.ocr.OcrTextRecognizer
import com.donian.myaitutor.tutor.LlmInferenceEngine
import com.donian.myaitutor.tutor.TutorMode
import com.donian.myaitutor.tutor.TutorParseResult
import com.donian.myaitutor.tutor.TutorRequest
import com.donian.myaitutor.tutor.TutorStreamChunk
import com.donian.myaitutor.tutor.TutorStreamParser
import com.donian.myaitutor.tutor.TutorUiState
import com.donian.myaitutor.tutor.inferenceOptionsFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 홈 화면의 추론 요청 생성·취소·복구를 담당합니다.
 *
 * [HomeViewModel]이 [HomeUiState] 흐름을 소유하고, 이 컨트롤러가 세대 관리(generationId·generationJob),
 * 취소 보관 질문(queuedQuestion), 마지막 과목(lastSubject), 스트림 파서(tutorParser)를 소유합니다.
 * [state]는 ViewModel의 상태 흐름과 동일한 인스턴스를 주입받아 원자적으로 갱신합니다.
 */
internal class GenerationController(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val ocrTextRecognizer: OcrTextRecognizer,
    private val ocrLogger: (String) -> Unit,
    private val stringProvider: StringProvider,
    private val tutorEngine: LlmInferenceEngine,
    private val state: MutableStateFlow<HomeUiState>
) {
    private var generationJob: Job? = null
    private var generationId: Int = 0

    /** 사용자가 중단을 요청한 generationId. 정상 오류와 취소를 구분합니다. */
    private var userCancelledGeneration: Int? = null

    /** 후속 질문 요청에 사용할 마지막 과목. 과목 상태 자체는 화면이 소유합니다. */
    internal var lastSubject: String = ""
        private set

    /**
     * 취소 확인 중에는 새 추론을 시작할 수 없어 보관해 둔 질문.
     * 취소가 확정되면 자동으로 전송합니다.
     */
    private var queuedQuestion: String? = null

    private val tutorParser = TutorStreamParser()

    /**
     * 진행 중인 생성을 무효화합니다. 세대를 증가시켜 이전 세대의 늦은 결과가 반영되지 않게 하고
     * 활성 잡을 취소합니다. [HomeViewModel.close]와 [HomeViewModel.confirmCapture]가 사용합니다.
     */
    fun invalidateGeneration() {
        generationJob?.cancel()
        generationJob = null
        generationId++
        userCancelledGeneration = null
    }

    /** 스트림 파서만 초기화합니다. */
    fun resetParser() {
        tutorParser.reset()
    }

    /** 대화 상태(마지막 과목·보관 질문·파서)까지 함께 초기화합니다. 모드 전환·과목 변경에 사용합니다. */
    fun abortForConversationReset() {
        invalidateGeneration()
        lastSubject = ""
        queuedQuestion = null
        tutorParser.reset()
    }

    fun analyzeImage(image: File, subject: String, selection: ImageSelection? = null) {
        if (state.value.isAnalyzing) return
        if (state.value.tutorState.requiresRecovery) {
            state.value = state.value.copy(
                statusMessage = state.value.tutorState.errorMessage
            )
            return
        }

        lastSubject = subject
        val currentGeneration = ++generationId
        userCancelledGeneration = null
        tutorParser.reset()
        val initialTutorState = tutorParser.startOcr()

        state.value = state.value.copy(
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
                    val previousPreview = state.value.preprocessedImage
                    if (previousPreview != null &&
                        previousPreview != image &&
                        previousPreview != normalizedImage
                    ) {
                        runCatching { if (previousPreview.exists()) previousPreview.delete() }
                    }
                    state.value = state.value.copy(preprocessedImage = imageToAnalyze)
                }

                val ocrBlocks = ocrTextRecognizer.recognize(imageToAnalyze)
                val ocrText = formatOcrBlocksForPrompt(ocrBlocks)
                // ocrLogger 기본값은 DEBUG 빌드에서만 Logcat에 기록한다(개인정보 노출 방지).
                ocrLogger(ocrText)
                if (ocrText.isBlank()) {
                    if (currentGeneration == generationId) {
                        val message = stringProvider.get(R.string.status_ocr_empty)
                        val tutorState = tutorParser.onChunk(TutorStreamChunk.Error(message)).state
                        state.value = state.value.copy(
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
                    state.value = state.value.copy(
                        ocrText = ocrText,
                        ocrBlocks = ocrBlocks,
                        analysisStage = ImageAnalysisStage.GEMMA,
                        statusMessage = if (isLowConfidence) {
                            stringProvider.get(R.string.status_ocr_low_confidence)
                        } else null,
                        tutorState = tutorParser.onOcrCompleted()
                    )
                }

                val grade = state.value.selectedGrade
                val request = TutorRequest(
                    text = ocrText,
                    subject = subject,
                    grade = grade,
                    options = inferenceOptionsFor(
                        isFollowUp = false,
                        thinkingEnabled = state.value.thinkingMode
                    )
                )
                if (currentGeneration == generationId) {
                    state.value = state.value.copy(tutorState = tutorParser.onAnalysisStarted())
                }
                receivedAnswer = collectTutorStream(request, currentGeneration)

                if (currentGeneration == generationId) {
                    val label = tutorEngine.backendLabel()
                    state.value = state.value.copy(
                        backendLabel = stringProvider.get(R.string.backend_label, label)
                    )
                    val tutorState = state.value.tutorState
                    completed = tutorState.isCompleted && receivedAnswer
                    if (!receivedAnswer && !tutorState.isCancelled && tutorState.errorMessage == null) {
                        state.value = state.value.copy(
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
                        state.value.tutorState
                    }
                    state.value = state.value.copy(tutorState = tutorState)
                }
                throw error
            } catch (error: Exception) {
                if (currentGeneration == generationId) {
                    val message = error.localizedMessage
                        ?: stringProvider.get(R.string.status_analysis_error)
                    state.value = state.value.copy(
                        tutorState = tutorParser.onChunk(TutorStreamChunk.Error(message)).state
                    )
                }
            } finally {
                // 1) 원본 촬영 이미지는 재분석을 위해 보존
                // 2) 임시 파일만 정리 (전처리, 크롭 결과 - preprocessedImage가 가리키는 파일은 보존)
                deleteTempAnalysisImage(normalizedImage, image, state.value.preprocessedImage)
                deleteTempAnalysisImage(imageToAnalyze, image, state.value.preprocessedImage)
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

    fun sendQuestion(
        question: String,
        subject: String = lastSubject.ifBlank { "기타" },
        showChoicesAfterResponse: Boolean = false
    ) {
        if (question.isBlank()) return
        if (state.value.isAnalyzing) {
            // 취소가 확인되지 않은 동안은 추론을 시작할 수 없으므로 보관해 두었다가 자동으로 전송합니다.
            if (state.value.tutorState.isCancelling) queuedQuestion = question
            return
        }
        if (state.value.tutorState.requiresRecovery) {
            state.value = state.value.copy(
                statusMessage = state.value.tutorState.errorMessage
            )
            return
        }
        if (state.value.selectedModelSnapshot.status != ModelDownloadStatus.READY) {
            state.value = state.value.copy(
                statusMessage = stringProvider.get(R.string.status_model_required)
            )
            return
        }

        // 자유 대화 모드는 과목 구분이 없으므로 항상 같은 대화를 이어갑니다.
        val isStandaloneQuestion = !state.value.chatMode && lastSubject != subject
        lastSubject = subject
        val currentGeneration = ++generationId
        userCancelledGeneration = null
        tutorParser.reset()
        // 학생 질문을 개별 메시지로 추가
        val studentMessage = ChatMessage(role = ChatRole.STUDENT, text = question)
        val currentMessages = state.value.messages + studentMessage
        state.value = state.value.copy(
            isAnalyzing = true,
            answer = "",
            showFollowUpChoices = false,
            inputText = "",
            statusMessage = null,
            analysisStage = null,
            messages = currentMessages,
            tutorState = tutorParser.startFollowUp()
        )
        generationJob = scope.launch {
            var completed = false
            var receivedAnswer = false
            try {
                val grade = state.value.selectedGrade
                val request = TutorRequest(
                    text = question,
                    subject = subject,
                    grade = grade,
                    options = inferenceOptionsFor(
                        isFollowUp = true,
                        thinkingEnabled = state.value.thinkingMode
                    ),
                    isStandaloneQuestion = isStandaloneQuestion,
                    mode = if (state.value.chatMode) TutorMode.FREE_CHAT else TutorMode.LEARNING
                )
                state.value = state.value.copy(tutorState = tutorParser.onAnalysisStarted())
                receivedAnswer = collectTutorStream(request, currentGeneration)

                if (currentGeneration == generationId) {
                    val label = tutorEngine.backendLabel()
                    state.value = state.value.copy(
                        backendLabel = stringProvider.get(R.string.backend_label, label)
                    )
                    val tutorState = state.value.tutorState
                    completed = tutorState.isCompleted && receivedAnswer
                    if (!receivedAnswer && !tutorState.isCancelled && tutorState.errorMessage == null) {
                        state.value = state.value.copy(
                            statusMessage = stringProvider.get(R.string.status_answer_empty_followup)
                        )
                    }
                }
            } catch (error: CancellationException) {
                if (currentGeneration == generationId) {
                    val tutorState = if (userCancelledGeneration == currentGeneration) {
                        tutorParser.onCancellationConfirmed()
                    } else {
                        state.value.tutorState
                    }
                    state.value = state.value.copy(tutorState = tutorState)
                }
                throw error
            } catch (error: Exception) {
                if (currentGeneration == generationId) {
                    val message = error.localizedMessage
                        ?: stringProvider.get(R.string.status_question_error)
                    state.value = state.value.copy(
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
        val current = state.value
        val delta = result.answerTextDelta
        state.value = current.copy(
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
        var tutorState = state.value.tutorState
        tutorState = when {
            userCancelledGeneration == generationId && !tutorState.isCancelled ->
                tutorParser.onCancellationConfirmed()
            tutorState.isGenerating -> tutorParser.onStreamEnded().state
            else -> tutorState
        }
        // AI 답변이 있으면 messages에 추가하여 학생 질문과 분리 표시
        val answerText = state.value.answer
        val updatedMessages = if (answerText.isNotBlank()) {
            state.value.messages + ChatMessage(role = ChatRole.TEACHER, text = answerText)
        } else {
            state.value.messages
        }
        state.value = state.value.copy(
            isAnalyzing = false,
            analysisStage = null,
            showFollowUpChoices = completed && receivedAnswer && showChoicesAfterResponse,
            tokenCount = tokens,
            messages = updatedMessages,
            tutorState = tutorState
        )
        generationJob = null
    }

    /**
     * 사용자 중단. 네이티브 취소를 요청하고, 종료 확인 즉시 이전 세대를 무효화합니다.
     * 완료를 확인하지 못하면 새 추론을 금지하고 복구 상태로 전환합니다.
     */
    fun cancelGeneration() {
        val job = generationJob ?: return
        if (!state.value.isAnalyzing) return
        val currentGeneration = generationId
        if (userCancelledGeneration == currentGeneration) return

        userCancelledGeneration = currentGeneration
        state.value = state.value.copy(
            tutorState = tutorParser.onCancellationRequested()
        )
        tutorEngine.cancelGeneration()

        scope.launch {
            val confirmed = tutorEngine.awaitGenerationStopped(CANCEL_CONFIRM_TIMEOUT_MS)
            if (currentGeneration != generationId) return@launch
            if (confirmed) {
                generationId++
                userCancelledGeneration = null
                job.cancel()
                // 취소된 네이티브 Conversation은 후속 요청에 답하지 않으므로 새로 만듭니다.
                runCatching { tutorEngine.resetConversation() }
                state.value = state.value.copy(
                    isAnalyzing = false,
                    analysisStage = null,
                    tutorState = tutorParser.onCancellationConfirmed()
                )
                generationJob = null
                // 취소 대기 중에 입력된 질문을 이어서 처리합니다.
                val queued = queuedQuestion
                queuedQuestion = null
                if (queued != null) sendQuestion(queued)
            } else {
                // 안전하지 않은 상태에서는 다음 추론을 시작하지 않습니다.
                queuedQuestion = null
                generationId++
                job.cancel()
                state.value = state.value.copy(
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
            if (state.value.isAnalyzing) {
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
            state.value = state.value.copy(
                isAnalyzing = false,
                analysisStage = null,
                statusMessage = failureMessage,
                tutorState = TutorUiState()
            )
        }
    }

    private companion object {
        /** 네이티브 취소가 끝났다는 확인을 기다리는 최대 시간. */
        const val CANCEL_CONFIRM_TIMEOUT_MS = 15_000L
    }
}
