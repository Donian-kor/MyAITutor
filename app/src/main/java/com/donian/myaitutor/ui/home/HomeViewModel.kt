package com.donian.myaitutor.ui.home

import android.content.Context
import android.util.Log
import com.donian.myaitutor.BuildConfig
import com.donian.myaitutor.R
import com.donian.myaitutor.ai.Assistant
import com.donian.myaitutor.ai.CpuFallbackBlockedException
import com.donian.myaitutor.ai.GemmaAssistant
import com.donian.myaitutor.ai.TutorGrade
import com.donian.myaitutor.camera.ImageCropper
import com.donian.myaitutor.camera.ImageSelection
import com.donian.myaitutor.model.AppModel
import com.donian.myaitutor.model.EngineRuntime
import com.donian.myaitutor.model.ModelDownloadManager
import com.donian.myaitutor.model.ModelDownloadRepository
import com.donian.myaitutor.model.ModelDownloadStatus
import com.donian.myaitutor.model.ModelDownloadSnapshot
import com.donian.myaitutor.model.ModelRuntimeState
import com.donian.myaitutor.model.ModelSelectionRepository
import com.donian.myaitutor.model.ModelTransitionException
import com.donian.myaitutor.model.PrefsModelSelectionRepository
import com.donian.myaitutor.model.QwenModelDownloadManager
import com.donian.myaitutor.ocr.OcrBlock
import com.donian.myaitutor.ocr.OcrTextRecognizer
import com.donian.myaitutor.tutor.LlmInferenceEngine
import com.donian.myaitutor.tutor.RealGemmaTutorEngine
import com.donian.myaitutor.tutor.TutorMode
import com.donian.myaitutor.tutor.TutorParseResult
import com.donian.myaitutor.tutor.TutorRequest
import com.donian.myaitutor.tutor.TutorStreamChunk
import com.donian.myaitutor.tutor.TutorStreamParser
import com.donian.myaitutor.tutor.TutorUiState
import com.donian.myaitutor.tutor.inferenceOptionsFor
import com.donian.myaitutor.ui.settings.AppSettingsRepository
import com.donian.myaitutor.ui.settings.PrefsAppSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.Executors

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
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val enablePolling: Boolean = true,
    private val ocrTextRecognizer: OcrTextRecognizer =
        appContext.applicationContext as OcrTextRecognizer,
    /**
     * OCR 원문 전달기. 문제 전문은 개인정보를 포함할 수 있으므로 DEBUG 빌드에서만 Logcat에 남긴다.
     * 테스트에서는 이 인자로 출력 내용을 검증할 수 있습니다.
     */
    private val ocrLogger: (String) -> Unit = {
        if (BuildConfig.DEBUG) Log.d(TAG, "OCR 결과:\n$it")
    },
    private val stringProvider: StringProvider = StringProvider { resId, args ->
        appContext.getString(resId, *args)
    },
    private val tutorEngine: LlmInferenceEngine = RealGemmaTutorEngine(GemmaAssistant(appContext)),
    private val appSettings: AppSettingsRepository = PrefsAppSettingsRepository(appContext),
    private val selectionRepository: ModelSelectionRepository =
        PrefsModelSelectionRepository(appContext),
    /** 모델 전환을 담당하는 조정자. [tutorEngine]이 SwitchableTutorEngine일 때 함께 주입합니다. */
    private val runtimeCoordinator: EngineRuntime? = null,
    /** Qwen 모델 다운로드 상태 조회. Gemma [modelManager]와 완전히 분리됩니다. */
    private val qwenModelManager: ModelDownloadRepository = QwenModelDownloadManager(appContext),
    /**
     * 종료 시 엔진 해제를 끝까지 보장하는 스코프.
     * viewModelScope와 분리되어 있어 scope가 이미 취소된 시점의 [close]에서도 동작한다.
     * 테스트에서는 테스트 스코프를 주입해 해제 완료를 결정적으로 검증한다.
     */
    private val shutdownScope: CoroutineScope = CoroutineScope(
        SupervisorJob() + createShutdownDispatcher()
    )
) {

    private companion object {
        const val TAG = "HomeViewModel"

        /** 네이티브 취소가 끝났다는 확인을 기다리는 최대 시간. */
        const val CANCEL_CONFIRM_TIMEOUT_MS = 15_000L

        /** 종료 시 엔진 해제를 기다리는 최대 시간. */
        const val ENGINE_RELEASE_WAIT_MS = 3_000L

        /**
         * 종료 해제 전용 daemon 스레드를 가진 디스패처를 만듭니다.
         * 호출 스레드(UI 스레드)를 점유하지 않고 JVM 종료도 막지 않습니다.
         */
        private fun createShutdownDispatcher(): CoroutineDispatcher =
            Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "focus-ai-engine-shutdown").apply { isDaemon = true }
            }.asCoroutineDispatcher()
    }

    private val _state = MutableStateFlow(
        HomeUiState(
            modelSnapshot = modelManager.snapshot(),
            thinkingMode = appSettings.thinkingMode,
            selectedModel = selectionRepository.getSelectedModel(),
            selectedModelSnapshot = selectionRepository.getSelectedModel().let { model ->
                if (model == AppModel.QWEN3) qwenModelManager.snapshot() else modelManager.snapshot()
            },
            qwenSnapshot = qwenModelManager.snapshot(),
            chatMode = selectionRepository.isFreeChatEnabled()
        )
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

    /**
     * 취소 확인 중에는 새 추론을 시작할 수 없어 보관해 둔 질문.
     * 취소가 확정되면 자동으로 전송합니다.
     */
    private var queuedQuestion: String? = null

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
                    val qwenSnapshot = withContext(ioDispatcher) {
                        if (qwenModelManager.needsHashVerification()) {
                            qwenModelManager.verifyModelHash()
                        }
                        qwenModelManager.snapshot()
                    }
                    val selected = _state.value.selectedModel
                    _state.value = _state.value.copy(
                        modelSnapshot = snapshot,
                        selectedModelSnapshot = if (selected == AppModel.QWEN3) qwenSnapshot else snapshot,
                        qwenSnapshot = qwenSnapshot,
                        runtimeState = runtimeCoordinator?.status?.value?.state
                            ?: _state.value.runtimeState,
                        freeChatBlockedReason = freeChatBlockedReasonFor(selected),
                        modelSelectionBlockedReason = modelSelectionBlockedReasonFor(selected)
                    )
                    // 두 모델 중 하나라도 전송 중이면 더 자주 갱신한다.
                    val busy = snapshot.status.isTransferring() ||
                        qwenSnapshot.status.isTransferring()
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
     * 해제는 [shutdownScope]에서 비동기로 마무리합니다. 호출 스레드(주로 UI 스레드)를
     * 최대 [ENGINE_RELEASE_WAIT_MS] 동안 막지 않아 ANR 위험을 없앴습니다.
     * [shutdownScope]는 viewModelScope와 분리되어 있어 scope 취소와 무관하게
     * 해제를 끝까지 보장합니다.
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
            shutdownScope.launch {
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

    /**
     * 설정 화면의 추론(Thinking) 모드 토글.
     * 학년과 무관하게 사용자가 직접 켜고 끄며, 값은 저장되어 다음 실행에도 유지됩니다.
     */
    fun setThinkingMode(enabled: Boolean) {
        if (_state.value.thinkingMode == enabled) return
        appSettings.thinkingMode = enabled
        _state.value = _state.value.copy(thinkingMode = enabled)
    }

    // ---------------------------------------------------------------------
    // 모델 선택 (Gemma ↔ Qwen) 및 0.6 모드 정합
    // ---------------------------------------------------------------------

    /**
     * 현재 선택된 모델이 자유 대화를 지원하지 않을 때의 안내 문구.
     * Qwen 활성 시에만 null이 아니며, 자동으로 Gemma를 로드하지 않는다(계획서 0.6).
     */
    private fun freeChatBlockedReasonFor(model: AppModel): String? =
        if (model == AppModel.QWEN3) {
            stringProvider.get(R.string.model_free_chat_blocked_by_qwen)
        } else {
            null
        }

    /**
     * 자유 대화 사용 중에는 Qwen 선택을 보류한다는 안내 문구.
     * 사용자가 자유 대화를 끄면 선택할 수 있다.
     */
    private fun modelSelectionBlockedReasonFor(model: AppModel): String? =
        if (model == AppModel.GEMMA && _state.value.chatMode) {
            stringProvider.get(R.string.model_selection_blocked_by_free_chat)
        } else {
            null
        }

    /**
     * 선택 모델을 [target]으로 전환합니다.
     *
     * - 자유 대화 사용 중이면 Qwen 선택을 보류하고 안내를 표시합니다.
     * - 전환은 [EngineRuntime]가 직렬화하며, 이전 모델 자원 해제 후 새 모델을 로드합니다.
     * - 실패 시 자동 복구된 경우/NO_MODEL_LOADED 상태를 안내합니다.
     */
    fun setSelectedModel(target: AppModel) {
        if (_state.value.selectedModel == target) return
        // 0.6: 자유 대화 사용 중에는 Qwen 선택을 보류한다.
        // 안내만 표시하고 전환하지 않으며, 사용자가 자유 대화를 끄면 선택할 수 있다.
        if (target == AppModel.QWEN3 && _state.value.chatMode) {
            _state.value = _state.value.copy(
                modelSelectionBlockedReason = stringProvider.get(
                    R.string.model_selection_blocked_by_free_chat
                )
            )
            return
        }
        // 대상 모델 파일이 준비되지 않았으면 전환하지 않는다 (계획서 4.1 0단계).
        // 다운로드는 설정 화면의 선택 모델 다운로드 영역에서 진행한다.
        if (snapshotFor(target).status != ModelDownloadStatus.READY) {
            _state.value = _state.value.copy(
                statusMessage = stringProvider.get(R.string.model_download_required_notice)
            )
            return
        }
        if (_state.value.isAnalyzing) {
            tutorEngine.cancelGeneration()
        }

        scope.launch {
            _state.value = _state.value.copy(runtimeState = ModelRuntimeState.TRANSITIONING)
            val coordinator = runtimeCoordinator
            val result = if (coordinator != null) {
                coordinator.switchTo(target)
            } else {
                // 조정자가 없으면 선택값만 저장하고 런타임 전환은 건너뛴다(테스트 경로).
                selectionRepository.setSelectedModel(target)
                Result.success(tutorEngine)
            }

            result.fold(
                onSuccess = { _ ->
                    selectionRepository.setSelectedModel(target)
                    _state.value = _state.value.copy(
                        selectedModel = target,
                        selectedModelSnapshot = if (target == AppModel.QWEN3) {
                            qwenModelManager.snapshot()
                        } else {
                            modelManager.snapshot()
                        },
                        runtimeState = ModelRuntimeState.ACTIVE,
                        transitionFailure = null,
                        freeChatBlockedReason = freeChatBlockedReasonFor(target),
                        modelSelectionBlockedReason = null
                    )
                    // Qwen 선택 시 자유 대화는 자동으로 꺼진다.
                    if (target == AppModel.QWEN3 && _state.value.chatMode) {
                        selectionRepository.setChatMode(false)
                        _state.value = _state.value.copy(chatMode = false)
                    }
                },
                onFailure = { error ->
                    val recovered = error is ModelTransitionException && error.recoveredToPrevious
                    val restoredModel = if (recovered) selectionRepository.getSelectedModel() else target
                    _state.value = _state.value.copy(
                        selectedModel = restoredModel,
                        runtimeState = if (recovered) ModelRuntimeState.ACTIVE else ModelRuntimeState.NO_MODEL_LOADED,
                        transitionFailure = transitionFailureMessage(
                            error,
                            R.string.error_model_transition_failed
                        ),
                        freeChatBlockedReason = freeChatBlockedReasonFor(restoredModel),
                        modelSelectionBlockedReason = modelSelectionBlockedReasonFor(restoredModel)
                    )
                    if (recovered) {
                        setStatusMessage(stringProvider.get(R.string.status_qwen_switch_failed_to_gemma))
                    }
                }
            )
        }
    }

    // ---------------------------------------------------------------------
    // 모델 복구 (계획서 0.4 2단계)
    // ---------------------------------------------------------------------

    /**
     * "다시 시도": 마지막으로 선택했던 모델을 다시 로드합니다.
     *
     * [setSelectedModel]과 달리 대상 모델이 현재 선택값과 같아도 강제로 재시도한다.
     */
    fun retryModelLoad() {
        val target = _state.value.selectedModel
        _state.value = _state.value.copy(
            isNoModelDismissed = false,
            transitionFailure = null
        )
        val coordinator = runtimeCoordinator
        scope.launch {
            _state.value = _state.value.copy(runtimeState = ModelRuntimeState.TRANSITIONING)
            val result = if (coordinator != null) {
                coordinator.switchTo(target)
            } else {
                Result.success(tutorEngine)
            }
            result.fold(
                onSuccess = { _ ->
                    selectionRepository.setSelectedModel(target)
                    _state.value = _state.value.copy(
                        selectedModelSnapshot = snapshotFor(target),
                        runtimeState = ModelRuntimeState.ACTIVE,
                        transitionFailure = null,
                        isNoModelDismissed = false,
                        freeChatBlockedReason = freeChatBlockedReasonFor(target)
                    )
                },
                onFailure = { error ->
                    _state.value = _state.value.copy(
                        runtimeState = ModelRuntimeState.NO_MODEL_LOADED,
                        transitionFailure = transitionFailureMessage(
                            error,
                            R.string.error_model_transition_failed
                        ),
                        isNoModelDismissed = false
                    )
                }
            )
        }
    }

    /**
     * "Gemma로 돌아가기": Gemma 파일이 유효하면 Gemma를 다시 활성화합니다.
     *
     * Gemma 파일도 유효하지 않으면 복구할 수 없으므로 상태 사유만 갱신합니다.
     */
    fun fallbackToGemma() {
        scope.launch {
            _state.value = _state.value.copy(runtimeState = ModelRuntimeState.TRANSITIONING)
            val coordinator = runtimeCoordinator
            if (coordinator == null) {
                // 조정자가 없는 테스트 경로에서는 선택값만 되돌린다.
                selectionRepository.setSelectedModel(AppModel.GEMMA)
                _state.value = _state.value.copy(
                    selectedModel = AppModel.GEMMA,
                    selectedModelSnapshot = modelManager.snapshot(),
                    runtimeState = ModelRuntimeState.ACTIVE,
                    transitionFailure = null,
                    isNoModelDismissed = false,
                    freeChatBlockedReason = null,
                    modelSelectionBlockedReason = modelSelectionBlockedReasonFor(AppModel.GEMMA)
                )
                return@launch
            }

            if (modelManager.snapshot().status != ModelDownloadStatus.READY) {
                _state.value = _state.value.copy(
                    runtimeState = ModelRuntimeState.NO_MODEL_LOADED,
                    transitionFailure = stringProvider.get(R.string.error_no_model_loaded)
                )
                return@launch
            }

            coordinator.switchTo(AppModel.GEMMA).fold(
                onSuccess = { _ ->
                    selectionRepository.setSelectedModel(AppModel.GEMMA)
                    _state.value = _state.value.copy(
                        selectedModel = AppModel.GEMMA,
                        selectedModelSnapshot = modelManager.snapshot(),
                        runtimeState = ModelRuntimeState.ACTIVE,
                        transitionFailure = null,
                        isNoModelDismissed = false,
                        freeChatBlockedReason = null,
                        modelSelectionBlockedReason = modelSelectionBlockedReasonFor(AppModel.GEMMA)
                    )
                },
                onFailure = { error ->
                    _state.value = _state.value.copy(
                        runtimeState = ModelRuntimeState.NO_MODEL_LOADED,
                        transitionFailure = transitionFailureMessage(error, R.string.error_no_model_loaded)
                    )
                }
            )
        }
    }

    /** "나중에": 모델 없이 앱을 계속 사용합니다(AI 기능 비활성). */
    fun dismissNoModelRecovery() {
        _state.value = _state.value.copy(
            isNoModelDismissed = true,
            transitionFailure = null
        )
    }

    private fun snapshotFor(model: AppModel): ModelDownloadSnapshot =
        if (model == AppModel.QWEN3) qwenModelManager.snapshot() else modelManager.snapshot()

    /**
     * 모델 로드 실패 문구를 고릅니다.
     *
     * CPU 폴백 차단은 준비된 안내 문구([R.string.error_cpu_fallback_blocked])로 알리고,
     * 그 외에는 예외 메시지를 그대로 사용합니다. [ModelTransitionException]이 원인을 cause로
     * 감싸므로 원인 체인을 따라 확인합니다.
     */
    private fun transitionFailureMessage(error: Throwable, fallbackStringRes: Int): String {
        var current: Throwable? = error
        while (current != null) {
            if (current is CpuFallbackBlockedException) {
                return stringProvider.get(R.string.error_cpu_fallback_blocked)
            }
            current = current.cause
        }
        return error.message ?: stringProvider.get(fallbackStringRes)
    }

    /** 지정한 모델의 다운로드를 시작한다. Gemma와 Qwen은 각각 독립 관리된다. */
    fun startModelDownload(model: AppModel) {
        val manager = managerFor(model)
        runCatching { manager.startDownload() }
            .onSuccess {
                setStatusMessage(stringProvider.get(R.string.status_download_started))
                refreshModelSnapshot()
            }
            .onFailure { setStatusMessage(stringProvider.get(R.string.status_download_failed)) }
    }

    /** 지정한 모델의 파일을 삭제한다. 활성 모델이면 먼저 엔진을 해제한다. */
    fun deleteModelFile(model: AppModel) {
        val manager = managerFor(model)
        scope.launch {
            // 활성 모델을 삭제하는 경우 활성 엔진을 먼저 해제한다.
            if (_state.value.selectedModel == model) {
                runCatching {
                    withContext(ioDispatcher) {
                        tutorEngine.awaitGenerationStopped(ENGINE_RELEASE_WAIT_MS)
                        tutorEngine.release()
                    }
                }
                warmupJob?.cancel()
                warmupJob = null
            }
            runCatching {
                withContext(ioDispatcher) { manager.deleteModel() }
            }
            refreshModelSnapshot()
            setStatusMessage(stringProvider.get(R.string.status_model_deleted))
        }
    }

    /** 모델별 다운로드 관리자를 반환한다. */
    private fun managerFor(model: AppModel): ModelDownloadRepository =
        if (model == AppModel.QWEN3) qwenModelManager else modelManager

    private fun refreshModelSnapshot() {
        val gemmaSnapshot = modelManager.snapshot()
        val qwenSnapshot = qwenModelManager.snapshot()
        val selectedSnapshot =
            if (_state.value.selectedModel == AppModel.QWEN3) qwenSnapshot else gemmaSnapshot
        _state.value = _state.value.copy(
            modelSnapshot = gemmaSnapshot,
            qwenSnapshot = qwenSnapshot,
            selectedModelSnapshot = selectedSnapshot
        )
    }

    /**
     * 학습 모드와 자유 대화 모드를 전환합니다.
     * 시스템 지시문은 Conversation 생성 시 고정되므로 대화를 새로 만들어 교체합니다.
     */
    fun setChatMode(chatMode: Boolean) {
        if (_state.value.chatMode == chatMode) return
        // 0.6: Qwen 활성 상태에서 자유 대화를 켜려는 요청은 차단한다.
        // 자동으로 Gemma를 로드하지 않으며, 안내 문구만 표시한다.
        if (chatMode && _state.value.selectedModel == AppModel.QWEN3) {
            _state.value = _state.value.copy(
                freeChatBlockedReason = freeChatBlockedReasonFor(AppModel.QWEN3)
            )
            return
        }
        if (_state.value.isAnalyzing) {
            tutorEngine.cancelGeneration()
        }
        generationJob?.cancel()
        generationJob = null
        generationId++
        userCancelledGeneration = null
        lastSubject = ""
        queuedQuestion = null
        tutorParser.reset()
        _state.value = _state.value.copy(
            chatMode = chatMode,
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
            messages = emptyList(),
            tutorState = TutorUiState()
        )
        val mode = if (chatMode) TutorMode.FREE_CHAT else TutorMode.LEARNING
        // 모드가 바뀌면 저장값도 함께 갱신해 (모델, chatMode) 쌍의 정합을 유지한다.
        selectionRepository.setChatMode(chatMode)
        scope.launch {
            runCatching { tutorEngine.resetConversation(mode.systemInstruction) }
        }
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
        lastSubject = ""
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
            messages = emptyList(),
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
            messages = emptyList(),
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
                val ocrText = formatOcrBlocksForPrompt(ocrBlocks)
                // ocrLogger 기본값은 DEBUG 빌드에서만 Logcat에 기록한다(개인정보 노출 방지).
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
                    options = inferenceOptionsFor(
                        isFollowUp = false,
                        thinkingEnabled = _state.value.thinkingMode
                    )
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

    fun sendQuestion(
        question: String,
        subject: String = lastSubject.ifBlank { "기타" },
        showChoicesAfterResponse: Boolean = false
    ) {
        if (question.isBlank()) return
        if (_state.value.isAnalyzing) {
            // 취소가 확인되지 않은 동안은 추론을 시작할 수 없으므로 보관해 두었다가 자동 전송합니다.
            if (_state.value.tutorState.isCancelling) queuedQuestion = question
            return
        }
        if (_state.value.tutorState.requiresRecovery) {
            _state.value = _state.value.copy(
                statusMessage = _state.value.tutorState.errorMessage
            )
            return
        }
        if (_state.value.selectedModelSnapshot.status != ModelDownloadStatus.READY) {
            _state.value = _state.value.copy(
                statusMessage = stringProvider.get(R.string.status_model_required)
            )
            return
        }

        // 자유 대화 모드는 과목 구분이 없으므로 항상 같은 대화를 이어갑니다.
        val isStandaloneQuestion = !_state.value.chatMode && lastSubject != subject
        lastSubject = subject
        val currentGeneration = ++generationId
        userCancelledGeneration = null
        tutorParser.reset()
        // 학생 질문을 개별 메시지로 추가
        val studentMessage = ChatMessage(role = ChatRole.STUDENT, text = question)
        val currentMessages = _state.value.messages + studentMessage
        _state.value = _state.value.copy(
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
                val grade = _state.value.selectedGrade
                val request = TutorRequest(
                    text = question,
                    subject = subject,
                    grade = grade,
                    options = inferenceOptionsFor(
                        isFollowUp = true,
                        thinkingEnabled = _state.value.thinkingMode
                    ),
                    isStandaloneQuestion = isStandaloneQuestion,
                    mode = if (_state.value.chatMode) TutorMode.FREE_CHAT else TutorMode.LEARNING
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
        // AI 답변이 있으면 messages에 추가하여 학생 질문과 분리 표시
        val answerText = _state.value.answer
        val updatedMessages = if (answerText.isNotBlank()) {
            _state.value.messages + ChatMessage(role = ChatRole.TEACHER, text = answerText)
        } else {
            _state.value.messages
        }
        _state.value = _state.value.copy(
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
                generationId++
                userCancelledGeneration = null
                job.cancel()
                // 취소된 네이티브 Conversation은 후속 요청에 답하지 않으므로 새로 만듭니다.
                runCatching { tutorEngine.resetConversation() }
                _state.value = _state.value.copy(
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
        // 선택 모델 기준으로 준비 여부를 판정한다. Gemma와 Qwen은 각각 독립 관리된다.
        if (current.selectedModelSnapshot.status != ModelDownloadStatus.READY) return

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
