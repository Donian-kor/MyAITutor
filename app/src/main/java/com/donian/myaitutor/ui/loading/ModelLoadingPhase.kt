package com.donian.myaitutor.ui.loading

import com.donian.myaitutor.model.ModelDownloadSnapshot
import com.donian.myaitutor.model.ModelDownloadStatus
import com.donian.myaitutor.ui.home.ModelWarmupState

/**
 * 시작 로딩 화면이 표시하는 단계.
 *
 * 다운로드 상태와 엔진 워밍업 상태를 하나의 화면 단계로 합친 결과이며,
 * 화면은 이 값만 보고 문구/진행 표시/버튼을 결정합니다.
 */
enum class ModelLoadingPhase {
    /** 모델 파일이 없어 사용자가 다운로드를 시작해야 합니다. */
    DOWNLOAD_REQUIRED,

    /** 모델 파일을 내려받는 중입니다. 진행률을 표시할 수 있습니다. */
    DOWNLOADING,

    /** 내려받은 파일의 무결성을 검증하는 중입니다. */
    VERIFYING,

    /** 엔진과 대화를 메모리에 올리는 중입니다. */
    ENGINE_LOADING,

    /** 준비가 끝나 바로 사용할 수 있습니다. */
    READY,

    /** 다운로드 또는 엔진 준비에 실패했습니다. */
    FAILED
}

/**
 * 다운로드 스냅샷과 워밍업 상태를 화면 단계로 변환합니다.
 *
 * 순수 함수라 단위 테스트로 모든 조합을 검증할 수 있습니다.
 * 모델 파일이 준비되지 않았다면 워밍업 상태는 무시합니다.
 */
fun resolveModelLoadingPhase(
    snapshot: ModelDownloadSnapshot,
    warmup: ModelWarmupState
): ModelLoadingPhase = when (snapshot.status) {
    ModelDownloadStatus.READY -> when (warmup) {
        ModelWarmupState.READY -> ModelLoadingPhase.READY
        ModelWarmupState.FAILED -> ModelLoadingPhase.FAILED
        // IDLE은 곧 워밍업이 시작되는 상태이므로 로딩으로 취급합니다.
        ModelWarmupState.IDLE,
        ModelWarmupState.LOADING -> ModelLoadingPhase.ENGINE_LOADING
    }

    ModelDownloadStatus.VERIFYING -> ModelLoadingPhase.VERIFYING
    ModelDownloadStatus.FAILED -> ModelLoadingPhase.FAILED
    ModelDownloadStatus.NOT_INSTALLED -> ModelLoadingPhase.DOWNLOAD_REQUIRED
    ModelDownloadStatus.QUEUED,
    ModelDownloadStatus.DOWNLOADING,
    ModelDownloadStatus.PAUSED -> ModelLoadingPhase.DOWNLOADING
}
