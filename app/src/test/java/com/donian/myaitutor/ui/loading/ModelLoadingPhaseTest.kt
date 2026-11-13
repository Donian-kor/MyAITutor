package com.donian.myaitutor.ui.loading

import com.donian.myaitutor.model.ModelDownloadSnapshot
import com.donian.myaitutor.model.ModelDownloadStatus
import com.donian.myaitutor.ui.home.ModelWarmupState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 시작 로딩 화면의 단계 계산 규칙을 검증합니다.
 * 다운로드 진행 상황과 엔진 워밍업 상태의 조합이 화면 단계로 올바르게 변환되어야 합니다.
 */
class ModelLoadingPhaseTest {

    private fun snapshot(
        status: ModelDownloadStatus,
        downloadedBytes: Long = 0L,
        totalBytes: Long = 3_659_530_240L
    ) = ModelDownloadSnapshot(
        status = status,
        downloadedBytes = downloadedBytes,
        totalBytes = totalBytes,
        message = "test"
    )

    @Test
    fun `모델이 없으면 다운로드 필요 단계`() {
        assertEquals(
            ModelLoadingPhase.DOWNLOAD_REQUIRED,
            resolveModelLoadingPhase(
                snapshot(ModelDownloadStatus.NOT_INSTALLED),
                ModelWarmupState.IDLE
            )
        )
    }

    @Test
    fun `큐와 진행과 일시중지는 다운로드 단계`() {
        for (status in listOf(
            ModelDownloadStatus.QUEUED,
            ModelDownloadStatus.DOWNLOADING,
            ModelDownloadStatus.PAUSED
        )) {
            assertEquals(
                status.name,
                ModelLoadingPhase.DOWNLOADING,
                resolveModelLoadingPhase(snapshot(status), ModelWarmupState.IDLE)
            )
        }
    }

    @Test
    fun `해시 검증 중에는 검증 단계`() {
        assertEquals(
            ModelLoadingPhase.VERIFYING,
            resolveModelLoadingPhase(
                snapshot(ModelDownloadStatus.VERIFYING),
                ModelWarmupState.IDLE
            )
        )
    }

    @Test
    fun `다운로드 실패는 실패 단계`() {
        assertEquals(
            ModelLoadingPhase.FAILED,
            resolveModelLoadingPhase(snapshot(ModelDownloadStatus.FAILED), ModelWarmupState.IDLE)
        )
    }

    @Test
    fun `모델 준비 완료 후 워밍업 전에는 엔진 로딩 단계`() {
        assertEquals(
            ModelLoadingPhase.ENGINE_LOADING,
            resolveModelLoadingPhase(snapshot(ModelDownloadStatus.READY), ModelWarmupState.IDLE)
        )
    }

    @Test
    fun `워밍업 중에는 엔진 로딩 단계`() {
        assertEquals(
            ModelLoadingPhase.ENGINE_LOADING,
            resolveModelLoadingPhase(snapshot(ModelDownloadStatus.READY), ModelWarmupState.LOADING)
        )
    }

    @Test
    fun `워밍업 완료는 준비 완료 단계`() {
        assertEquals(
            ModelLoadingPhase.READY,
            resolveModelLoadingPhase(snapshot(ModelDownloadStatus.READY), ModelWarmupState.READY)
        )
    }

    @Test
    fun `워밍업 실패는 실패 단계`() {
        assertEquals(
            ModelLoadingPhase.FAILED,
            resolveModelLoadingPhase(snapshot(ModelDownloadStatus.READY), ModelWarmupState.FAILED)
        )
    }

    @Test
    fun `모델 파일이 준비되지 않으면 워밍업 상태를 무시한다`() {
        // 다운로드 중인데 이전 워밍업 상태가 남아 있어도 다운로드 단계를 유지해야 합니다.
        assertEquals(
            ModelLoadingPhase.DOWNLOADING,
            resolveModelLoadingPhase(
                snapshot(ModelDownloadStatus.DOWNLOADING),
                ModelWarmupState.READY
            )
        )
        assertEquals(
            ModelLoadingPhase.DOWNLOAD_REQUIRED,
            resolveModelLoadingPhase(
                snapshot(ModelDownloadStatus.NOT_INSTALLED),
                ModelWarmupState.FAILED
            )
        )
    }

    @Test
    fun `진행률은 0과 1 사이로 제한된다`() {
        val half = snapshot(
            ModelDownloadStatus.DOWNLOADING,
            downloadedBytes = 1_829_765_120L,
            totalBytes = 3_659_530_240L
        )
        assertEquals(0.5f, half.progress, 0.01f)
    }
}
