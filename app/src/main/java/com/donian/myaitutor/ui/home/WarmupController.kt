package com.donian.myaitutor.ui.home

import com.donian.myaitutor.R
import com.donian.myaitutor.model.ModelDownloadStatus
import com.donian.myaitutor.tutor.LlmInferenceEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 모델 엔진/대화를 미리 메모리에 올리는 워밍업과 캐시 정리를 담당합니다.
 *
 * 시작 로딩 화면이 [HomeUiState.warmup]을 구독해 진행 상태를 그립니다.
 * 중복 호출되어도 Job은 하나만 실행되며, 이미 준비된 경우 아무 것도 하지 않습니다.
 */
internal class WarmupController(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val state: MutableStateFlow<HomeUiState>,
    private val tutorEngine: LlmInferenceEngine,
    private val appContext: android.content.Context,
    private val stringProvider: StringProvider
) {
    /** 엔진 워밍업 중복 실행을 막는 Job. */
    private var warmupJob: Job? = null

    /** 활성 모델 삭제 등 외부 요청 시 워밍업을 중단합니다. */
    fun abort() {
        warmupJob?.cancel()
        warmupJob = null
    }

    fun preWarmIfNeeded() {
        val current = state.value
        if (current.warmup == ModelWarmupState.LOADING || current.warmup == ModelWarmupState.READY) return
        if (warmupJob?.isActive == true) return
        // 선택 모델 기준으로 준비 여부를 판정한다. Gemma와 Qwen은 각각 독립 관리된다.
        if (current.selectedModelSnapshot.status != ModelDownloadStatus.READY) return

        state.value = state.value.copy(warmup = ModelWarmupState.LOADING)
        warmupJob = scope.launch {
            try {
                tutorEngine.preWarm()
                state.value = state.value.copy(
                    warmup = ModelWarmupState.READY,
                    backendLabel = stringProvider.get(
                        R.string.backend_label,
                        tutorEngine.backendLabel()
                    )
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                state.value = state.value.copy(
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
        state.value = state.value.copy(warmup = ModelWarmupState.IDLE)
        preWarmIfNeeded()
    }

    fun clearCache() {
        scope.launch {
            val clearedCount = withContext(ioDispatcher) {
                var count = 0
                val cacheDir = appContext.cacheDir
                cacheDir.listFiles()?.forEach { file ->
                    if (file.isFile && (file.name.startsWith("focus_") ||
                                file.name.endsWith(".jpg") ||
                                file.name.endsWith(".png"))
                    ) {
                        if (file.delete()) count++
                    }
                }
                count
            }
            state.value = state.value.copy(
                preprocessedImage = null,
                statusMessage = if (clearedCount > 0) {
                    stringProvider.get(R.string.status_cache_cleared, clearedCount)
                } else {
                    stringProvider.get(R.string.status_cache_empty)
                }
            )
        }
    }
}