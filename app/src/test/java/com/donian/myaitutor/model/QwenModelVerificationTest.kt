package com.donian.myaitutor.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Qwen 모델 파일 무결성 판정 규칙을 검증한다 (계획서 5.1, 8.1).
 *
 * Android `DownloadManager`·`Context` 없이 순수 판정 로직만 확인한다.
 */
class QwenModelVerificationTest {

    private val bytes = QwenModelDownloadManager.EXPECTED_MODEL_BYTES

    private fun decide(
        completed: Boolean = true,
        exists: Boolean = true,
        length: Long = bytes,
        verified: Boolean = true,
        mismatch: Boolean = false,
        hashRequired: Boolean = true
    ) = QwenModelVerification.decide(
        completedPreviously = completed,
        fileExists = exists,
        fileLength = length,
        hashVerified = verified,
        hashMismatch = mismatch,
        hashVerificationRequired = hashRequired,
        expectedBytes = bytes
    )

    // --- 승인 상수 확인 -----------------------------------------------------

    @Test
    fun `expectedBytes matches approved value`() {
        assertEquals(2_659_057_664L, QwenModelDownloadManager.EXPECTED_MODEL_BYTES)
    }

    @Test
    fun `SHA-256은 승인값과 일치한다`() {
        assertEquals(
            "9e48b165836256f5344d9d044930607b9c47f6ef34e27f82e96881664f3ba2fd",
            QwenModelDownloadManager.EXPECTED_MODEL_SHA256
        )
    }

    @Test
    fun `revision과 파일명은 승인값과 일치한다`() {
        assertEquals(
            "6ee6014fa9193ea4dbb309f3305d6ee0d78db5a0",
            QwenModelDownloadManager.MODEL_REVISION
        )
        assertEquals(
            "qwen3_4b_instruct_2507_mixed_int4.litertlm",
            QwenModelDownloadManager.MODEL_FILE_NAME
        )
    }

    @Test
    fun `다운로드 URL은 고정 revision을 포함한다`() {
        assertTrue(QwenModelDownloadManager.MODEL_DOWNLOAD_URL.contains("6ee6014fa9193ea4dbb309f3305d6ee0d78db5a0"))
        assertTrue(QwenModelDownloadManager.MODEL_DOWNLOAD_URL.endsWith("?download=true"))
    }

    // --- READY 조건 --------------------------------------------------------

    @Test
    fun `모든 조건을 만족하면 READY다`() {
        assertEquals(QwenModelVerification.Verdict.READY, decide())
    }

    @Test
    fun `해시 검증이 필요 없으면 크기만으로 READY다`() {
        assertEquals(
            QwenModelVerification.Verdict.READY,
            decide(verified = false, hashRequired = false)
        )
    }

    // --- 크기 검증 ---------------------------------------------------------

    @Test
    fun `1바이트 짧아도 READY가 아니다`() {
        assertEquals(QwenModelVerification.Verdict.NOT_INSTALLED, decide(length = bytes - 1))
    }

    @Test
    fun `1바이트 길어도 READY가 아니다`() {
        assertEquals(QwenModelVerification.Verdict.NOT_INSTALLED, decide(length = bytes + 1))
    }

    @Test
    fun `크기가 0이면 설치되지 않은 상태다`() {
        assertEquals(QwenModelVerification.Verdict.NOT_INSTALLED, decide(length = 0L))
    }

    @Test
    fun `파일이 없으면 설치되지 않은 상태다`() {
        assertEquals(QwenModelVerification.Verdict.NOT_INSTALLED, decide(exists = false))
    }

    @Test
    fun `다운로드 완료 기록이 없으면 설치되지 않은 상태다`() {
        assertEquals(QwenModelVerification.Verdict.NOT_INSTALLED, decide(completed = false))
    }

    // --- 해시 검증 ---------------------------------------------------------

    @Test
    fun `크기가 같아도 해시가 다르면 MISMATCH다`() {
        assertEquals(QwenModelVerification.Verdict.MISMATCH, decide(mismatch = true))
    }

    @Test
    fun `해시 불일치 기록이 검증 완료보다 우선한다`() {
        assertEquals(
            QwenModelVerification.Verdict.MISMATCH,
            decide(verified = true, mismatch = true)
        )
    }

    @Test
    fun `크기는 맞지만 해시 검증 전이면 NEEDS_HASH_VERIFICATION이다`() {
        assertEquals(
            QwenModelVerification.Verdict.NEEDS_HASH_VERIFICATION,
            decide(verified = false)
        )
    }

    @Test
    fun `크기가 틀리면 해시 검증 전이어도 설치되지 않은 상태다`() {
        assertEquals(
            QwenModelVerification.Verdict.NOT_INSTALLED,
            decide(length = bytes - 1, verified = false)
        )
    }

    // --- 크기 단독 판정 ----------------------------------------------------

    @Test
    fun `크기 단독 판정은 정확한 크기일 때만 true다`() {
        assertTrue(QwenModelVerification.isSizeOnlySufficient(true, bytes))
    }

    @Test
    fun `크기 단독 판정은 파일이 없으면 false다`() {
        assertFalse(QwenModelVerification.isSizeOnlySufficient(false, bytes))
    }

    @Test
    fun `크기 단독 판정은 크기가 다르면 false다`() {
        assertFalse(QwenModelVerification.isSizeOnlySufficient(true, bytes - 1))
    }

    @Test
    fun `저장 공간과 가용 메모리가 충분하면 다운로드를 시작할 수 있다`() {
        assertTrue(
            QwenModelVerification.canStartDownload(
                availableStorageBytes = bytes + QwenModelVerification.DOWNLOAD_STORAGE_MARGIN_BYTES,
                availableMemoryBytes = QwenModelVerification.MIN_DOWNLOAD_AVAIL_BYTES
            )
        )
    }

    @Test
    fun `여유 공간이 정확히 부족하면 다운로드를 시작할 수 없다`() {
        assertFalse(
            QwenModelVerification.canStartDownload(
                availableStorageBytes = bytes + QwenModelVerification.DOWNLOAD_STORAGE_MARGIN_BYTES - 1,
                availableMemoryBytes = QwenModelVerification.MIN_DOWNLOAD_AVAIL_BYTES
            )
        )
    }

    @Test
    fun `가용 메모리가 임계값 미만이면 다운로드를 시작할 수 없다`() {
        assertFalse(
            QwenModelVerification.canStartDownload(
                availableStorageBytes = bytes * 2,
                availableMemoryBytes = QwenModelVerification.MIN_DOWNLOAD_AVAIL_BYTES - 1
            )
        )
    }

    @Test
    fun `검증 캐시는 크기와 수정시각이 모두 같을 때만 유효하다`() {
        assertTrue(QwenModelVerification.isHashCacheForFile(1_000L, 2_000L, 1_000L, 2_000L))
    }

    @Test
    fun `크기가 같은 다른 파일이면 검증 캐시를 무효로 본다`() {
        assertFalse(QwenModelVerification.isHashCacheForFile(1_000L, 2_000L, 1_000L, 9_999L))
    }

    @Test
    fun `수정시각이 같아도 크기가 다르면 검증 캐시를 무효로 본다`() {
        assertFalse(QwenModelVerification.isHashCacheForFile(1_000L, 2_000L, 999L, 2_000L))
    }
}