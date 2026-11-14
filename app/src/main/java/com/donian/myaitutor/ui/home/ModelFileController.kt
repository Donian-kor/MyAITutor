package com.donian.myaitutor.ui.home

import com.donian.myaitutor.R
import com.donian.myaitutor.model.AppModel
import com.donian.myaitutor.model.ModelDownloadRepository
import com.donian.myaitutor.tutor.LlmInferenceEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 모델 파일 다운로드·삭제와 스냅샷 갱신을 담당합니다.
 *
 * Gemma와 Qwen은 각각 독립된 [ModelDownloadRepository]로 관리되며, 활성 모델을 삭제할 때는
 * 엔진을 먼저 해제한 뒤 파일을 지웁니다.
 */
internal class ModelFileController(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val state: MutableStateFlow<HomeUiState>,
    private val tutorEngine: LlmInferenceEngine,
    private val modelManager: ModelDownloadRepository,
    private val qwenModelManager: ModelDownloadRepository,
    private val stringProvider: StringProvider,
    private val engineReleaseWaitMs: Long,
    /** 활성 모델 삭제 시 워밍업 Job을 취소하기 위한 콜백. */
    private val onActiveModelDeleted: () -> Unit
) {
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
            if (state.value.selectedModel == model) {
                runCatching {
                    withContext(ioDispatcher) {
                        tutorEngine.awaitGenerationStopped(engineReleaseWaitMs)
                        tutorEngine.release()
                    }
                }
                onActiveModelDeleted()
            }
            runCatching {
                withContext(ioDispatcher) { manager.deleteModel() }
            }
            refreshModelSnapshot()
            setStatusMessage(stringProvider.get(R.string.status_model_deleted))
        }
    }

    /** 모델별 다운로드 관리자를 반환한다. */
    fun managerFor(model: AppModel): ModelDownloadRepository =
        if (model == AppModel.QWEN3) qwenModelManager else modelManager

    fun refreshModelSnapshot() {
        val gemmaSnapshot = modelManager.snapshot()
        val qwenSnapshot = qwenModelManager.snapshot()
        val selectedSnapshot =
            if (state.value.selectedModel == AppModel.QWEN3) qwenSnapshot else gemmaSnapshot
        state.value = state.value.copy(
            modelSnapshot = gemmaSnapshot,
            qwenSnapshot = qwenSnapshot,
            selectedModelSnapshot = selectedSnapshot
        )
    }

    /** Gemma 전용 다운로드 시작(레거시 경로). */
    fun startDownload() {
        scope.launch {
            runCatching { modelManager.startDownload() }
                .onSuccess {
                    state.value = state.value.copy(
                        statusMessage = stringProvider.get(R.string.status_download_started)
                    )
                }
                .onFailure {
                    state.value = state.value.copy(
                        statusMessage = it.localizedMessage
                            ?: stringProvider.get(R.string.status_download_failed)
                    )
                }
            state.value = state.value.copy(modelSnapshot = modelManager.snapshot())
        }
    }

    /** Gemma 전용 모델 삭제(레거시 경로). */
    fun deleteModel() {
        scope.launch {
            onActiveModelDeleted()
            tutorEngine.release()
            modelManager.deleteModel()
            state.value = state.value.copy(
                modelSnapshot = modelManager.snapshot(),
                backendLabel = stringProvider.get(R.string.status_model_deleted),
                warmup = ModelWarmupState.IDLE
            )
        }
    }

    private fun setStatusMessage(message: String?) {
        state.value = state.value.copy(statusMessage = message)
    }
}