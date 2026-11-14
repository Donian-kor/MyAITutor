package com.donian.myaitutor.ui.home

import android.content.Context
import android.util.Log
import com.donian.myaitutor.BuildConfig
import com.donian.myaitutor.ai.GemmaAssistant
import com.donian.myaitutor.ai.TutorGrade
import com.donian.myaitutor.camera.ImageSelection
import com.donian.myaitutor.model.AppModel
import com.donian.myaitutor.model.EngineRuntime
import com.donian.myaitutor.model.ModelDownloadManager
import com.donian.myaitutor.model.ModelDownloadRepository
import com.donian.myaitutor.model.ModelSelectionRepository
import com.donian.myaitutor.model.PrefsModelSelectionRepository
import com.donian.myaitutor.model.QwenModelDownloadManager
import com.donian.myaitutor.ocr.OcrTextRecognizer
import com.donian.myaitutor.tutor.LlmInferenceEngine
import com.donian.myaitutor.tutor.RealGemmaTutorEngine
import com.donian.myaitutor.tutor.TutorMode
import com.donian.myaitutor.tutor.TutorUiState
import com.donian.myaitutor.ui.settings.AppSettingsRepository
import com.donian.myaitutor.ui.settings.PrefsAppSettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * 홈 화면의 상태([HomeUiState])를 소유하고 컨트롤러에 위임합니다.
 * Composable은 상태를 구독하고 이벤트를 위임하기만 합니다.
 *
 * 추론 생성·취소·복구는 [GenerationController]가, 모델 전환·복구는 [ModelTransitionController]가,
 * 파일 다운로드·삭제는 [ModelFileController]가, 워밍업·캐시 정리는 [WarmupController]가 담당합니다.
 * 이 ViewModel은 상태·폴링·[close]·카메라 이미지 생명주기와 컨트롤러 배선만 남습니다.
 *
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

        /** 종료 시 엔진 해제를 기다리는 최대 시간. */
        const val ENGINE_RELEASE_WAIT_MS = 3_000L
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

    /** 추론 요청 생성·취소·복구를 담당하는 컨트롤러. 세대 관리와 파서 상태를 소유합니다. */
    private val generation = GenerationController(
        scope = scope,
        ioDispatcher = ioDispatcher,
        ocrTextRecognizer = ocrTextRecognizer,
        ocrLogger = ocrLogger,
        stringProvider = stringProvider,
        tutorEngine = tutorEngine,
        state = _state
    )

    /** 엔진 워밍업과 캐시 정리. */
    private val warmup = WarmupController(
        scope = scope,
        ioDispatcher = ioDispatcher,
        state = _state,
        tutorEngine = tutorEngine,
        appContext = appContext,
        stringProvider = stringProvider
    )

    /** 모델 파일 다운로드·삭제와 스냅샷 갱신. 활성 모델 삭제 시 워밍업을 중단한다. */
    private val modelFiles = ModelFileController(
        scope = scope,
        ioDispatcher = ioDispatcher,
        state = _state,
        tutorEngine = tutorEngine,
        modelManager = modelManager,
        qwenModelManager = qwenModelManager,
        stringProvider = stringProvider,
        engineReleaseWaitMs = ENGINE_RELEASE_WAIT_MS,
        onActiveModelDeleted = { warmup.abort() }
    )

    /** 선택 모델 전환·복구 로직. [EngineRuntime]이 전환을 직렬화합니다. */
    private val modelTransitions = ModelTransitionController(
        scope = scope,
        state = _state,
        selectionRepository = selectionRepository,
        runtimeCoordinator = runtimeCoordinator,
        tutorEngine = tutorEngine,
        modelManager = modelManager,
        qwenModelManager = qwenModelManager,
        stringProvider = stringProvider
    )

    /** 종료 시 엔진이 한 번만 해제되도록 보장합니다. */
    private var isClosed = false

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
                        freeChatBlockedReason = freeChatBlockedReasonFor(selected, stringProvider),
                        modelSelectionBlockedReason = modelSelectionBlockedReasonFor(
                            selected,
                            _state.value.chatMode,
                            stringProvider
                        )
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
        generation.invalidateGeneration()
        tutorEngine.cancelGeneration()
        warmup.abort()
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

    /** 선택 모델을 [target]으로 전환합니다. */
    fun setSelectedModel(target: AppModel) {
        modelTransitions.setSelectedModel(target)
    }

    /** "다시 시도": 마지막으로 선택했던 모델을 다시 로드합니다. */
    fun retryModelLoad() {
        modelTransitions.retryModelLoad()
    }

    /** "Gemma로 돌아가기": Gemma 파일이 유효하면 Gemma를 다시 활성화합니다. */
    fun fallbackToGemma() {
        modelTransitions.fallbackToGemma()
    }

    /** "나중에": 모델 없이 앱을 계속 사용합니다(AI 기능 비활성). */
    fun dismissNoModelRecovery() {
        modelTransitions.dismissNoModelRecovery()
    }

    /** 지정한 모델의 다운로드를 시작한다. Gemma와 Qwen은 각각 독립 관리된다. */
    fun startModelDownload(model: AppModel) {
        modelFiles.startModelDownload(model)
    }

    /** 지정한 모델의 파일을 삭제한다. 활성 모델이면 먼저 엔진을 해제한다. */
    fun deleteModelFile(model: AppModel) {
        modelFiles.deleteModelFile(model)
    }

    /** Gemma 전용 다운로드 시작(레거시 경로). */
    fun startDownload() {
        modelFiles.startDownload()
    }

    /** Gemma 전용 모델 삭제(레거시 경로). */
    fun deleteModel() {
        modelFiles.deleteModel()
    }

    /** 모델 파일이 준비된 경우 엔진/대화를 미리 메모리에 올린다. */
    fun preWarmIfNeeded() {
        warmup.preWarmIfNeeded()
    }

    /** 로딩 화면의 "다시 시도" 버튼. 실패 상태를 초기화하고 워밍업을 다시 시작합니다. */
    fun retryModelWarmup() {
        warmup.retryModelWarmup()
    }

    /** 캐시 디렉터리에서 임시 파일을 정리한다. */
    fun clearCache() {
        warmup.clearCache()
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
                freeChatBlockedReason = freeChatBlockedReasonFor(AppModel.QWEN3, stringProvider)
            )
            return
        }
        if (_state.value.isAnalyzing) {
            tutorEngine.cancelGeneration()
        }
        generation.abortForConversationReset()
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
        generation.abortForConversationReset()
        // 이전 캡처 이미지는 삭제 (과목 변경 시)
        _state.value.capturedImage?.deleteQuietly()
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
            generation.invalidateGeneration()
        }
        generation.resetParser()
        // 이전 캡처 이미지가 있으면 삭제 (새 사진으로 교체)
        _state.value.capturedImage?.takeIf { it != image }?.deleteQuietly()
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
        _state.value.cameraCapturedImage?.deleteQuietly()
        _state.value.preprocessedImage?.deleteQuietly()
        _state.value = _state.value.copy(cameraCapturedImage = null, cameraError = null, preprocessedImage = null)
    }

    fun analyzeImage(image: File, subject: String, selection: ImageSelection? = null) {
        generation.analyzeImage(image, subject, selection)
    }

    fun sendQuestion(
        question: String,
        subject: String = generation.lastSubject.ifBlank { "기타" },
        showChoicesAfterResponse: Boolean = false
    ) {
        generation.sendQuestion(question, subject, showChoicesAfterResponse)
    }

    /**
     * 사용자 중단. [GenerationController.cancelGeneration]에 위임합니다.
     */
    fun cancelGeneration() {
        generation.cancelGeneration()
    }

    /** 취소 완료를 확인하지 못한 상태에서 Conversation만 안전하게 교체합니다. */
    fun recoverTutor() {
        generation.recoverTutor()
    }
}
