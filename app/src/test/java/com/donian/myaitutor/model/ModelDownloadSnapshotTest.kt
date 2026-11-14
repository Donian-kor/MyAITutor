package com.donian.myaitutor.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelDownloadSnapshotTest {

    @Test
    fun `progress는 0에서 1 사이로 제한된다`() {
        val s = ModelDownloadSnapshot(ModelDownloadStatus.DOWNLOADING, 0L, 100L, "")
        assertEquals(0f, s.progress, 0.001f)
        val full = ModelDownloadSnapshot(ModelDownloadStatus.READY, 100L, 100L, "")
        assertEquals(1f, full.progress, 0.001f)
        val over = ModelDownloadSnapshot(ModelDownloadStatus.DOWNLOADING, 200L, 100L, "")
        assertEquals(1f, over.progress, 0.001f)
    }

    @Test
    fun `totalBytes가 0이면 progress는 0이다`() {
        val s = ModelDownloadSnapshot(ModelDownloadStatus.NOT_INSTALLED, 0L, 0L, "")
        assertEquals(0f, s.progress, 0.001f)
    }

    @Test
    fun `formatBytes는 GB와 MB를 구분한다`() {
        val snap = ModelDownloadSnapshot(ModelDownloadStatus.READY, 0L, 0L, "")
        assertEquals("0 MB", snap.formatBytes(0L))
        assertEquals("500 MB", snap.formatBytes(500_000_000L))
        assertEquals("1.50 GB", snap.formatBytes(1_500_000_000L))
    }

    @Test
    fun `totalLabel은 0일 때 알 수 없음이다`() {
        val snap = ModelDownloadSnapshot(ModelDownloadStatus.NOT_INSTALLED, 0L, 0L, "")
        assertEquals("알 수 없음", snap.totalLabel)
    }

    @Test
    fun `Gemma 다운로드 URL은 파일이 존재하는 고정 리비전을 사용한다`() {
        assertTrue(ModelDownloadManager.MODEL_DOWNLOAD_URL.contains(ModelDownloadManager.MODEL_REVISION))
        assertTrue(ModelDownloadManager.MODEL_DOWNLOAD_URL.endsWith("/${ModelDownloadManager.MODEL_FILE_NAME}?download=true"))
    }
}
