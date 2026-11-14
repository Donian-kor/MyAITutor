package com.donian.myaitutor.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gemma 모델 파일 무결성 판정 규칙과 I/O 오류 재시도 정책을 검증한다.
 *
 * Android `DownloadManager`·`Context` 없이 순수 판정 로직만 확인한다.
 */
class GemmaModelVerificationTest {

    private val bytes = ModelDownloadManager.EXPECTED_MODEL_BYTES

    private fun decide(
        completed: Boolean = true,
        exists: Boolean = true,
        length: Long = bytes,
        verified: Boolean = true,
        mismatch: Boolean = false,
        hashRequired: Boolean = true
    ) = GemmaModelVerification.decide(
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
    fun `expectedBytes가 승인값과 일치한다`() {
        assertEquals(3_659_530_240L, ModelDownloadManager.EXPECTED_MODEL_BYTES)
    }

    @Test
    fun `SHA-256은 승인값과 일치한다`() {
        assertEquals(
            "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0",
            ModelDownloadManager.EXPECTED_MODEL_SHA256
        )
    }

    @Test
    fun `revision과 파일명은 승인값과 일치한다`() {
        assertEquals(
            "f254b5ba6e36f7d89cc7892e268e9877bc20f0a0",
            ModelDownloadManager.MODEL_REVISION
        )
        assertEquals("gemma-4-E4B-it.litertlm", ModelDownloadManager.MODEL_FILE_NAME)
    }

    @Test
    fun `최소 크기 기준은 승인 크기보다 작다`() {
        assertTrue(ModelDownloadManager.MIN_MODEL_BYTES < ModelDownloadManager.EXPECTED_MODEL_BYTES)
    }

    @Test
    fun `다운로드 URL은 고정 revision을 포함한다`() {
        assertTrue(
            ModelDownloadManager.MODEL_DOWNLOAD_URL.contains("f254b5ba6e36f7d89cc7892e268e9877bc20f0a0")
        )
        assertTrue(ModelDownloadManager.MODEL_DOWNLOAD_URL.endsWith("?download=true"))
    }

    // --- READY 조건 --------------------------------------------------------

    @Test
    fun `모든 조건을 만족하면 READY다`() {
        assertEquals(GemmaModelVerification.Verdict.READY, decide())
    }

    @Test
    fun `해시 검증이 필요 없으면 크기만으로 READY다`() {
        assertEquals(
            GemmaModelVerification.Verdict.READY,
            decide(verified = false, hashRequired = false)
        )
    }

    @Test
    fun `해시 검증을 통과하지 못하면 NEEDS_HASH_VERIFICATION이다`() {
        assertEquals(
            GemmaModelVerification.Verdict.NEEDS_HASH_VERIFICATION,
            decide(verified = false)
        )
    }

    // --- MISMATCH ---------------------------------------------------------

    @Test
    fun `해시 불일치가 기록되면 MISMATCH다`() {
        assertEquals(GemmaModelVerification.Verdict.MISMATCH, decide(mismatch = true))
    }

    @Test
    fun `해시 불일치는 검증 완료 여부와 무관하게 MISMATCH다`() {
        assertEquals(
            GemmaModelVerification.Verdict.MISMATCH,
            decide(verified = false, mismatch = true)
        )
    }

    // --- NOT_INSTALLED ----------------------------------------------------

    @Test
    fun `다운로드 완료 기록이 없으면 NOT_INSTALLED다`() {
        assertEquals(GemmaModelVerification.Verdict.NOT_INSTALLED, decide(completed = false))
    }

    @Test
    fun `파일이 없으면 NOT_INSTALLED다`() {
        assertEquals(GemmaModelVerification.Verdict.NOT_INSTALLED, decide(exists = false))
    }

    @Test
    fun `크기가 승인값보다 1바이트 작아도 NOT_INSTALLED다`() {
        assertEquals(GemmaModelVerification.Verdict.NOT_INSTALLED, decide(length = bytes - 1))
    }

    @Test
    fun `크기가 승인값보다 1바이트 커도 NOT_INSTALLED다`() {
        assertEquals(GemmaModelVerification.Verdict.NOT_INSTALLED, decide(length = bytes + 1))
    }

    @Test
    fun `최소 크기는 통과하지만 승인 크기에는 미달하면 NOT_INSTALLED다`() {
        // 3.5GB 이상이지만 정확 일치에는 미달하는 불완전 다운로드
        val partial = ModelDownloadManager.MIN_MODEL_BYTES + 1L
        assertTrue(partial < bytes)
        assertEquals(GemmaModelVerification.Verdict.NOT_INSTALLED, decide(length = partial))
    }

    // --- isSizeOnlySufficient ---------------------------------------------

    @Test
    fun `크기가 정확히 맞고 파일이 있으면 크기만으로 충분하다`() {
        assertTrue(GemmaModelVerification.isSizeOnlySufficient(fileExists = true, fileLength = bytes))
    }

    @Test
    fun `파일이 없으면 크기만으로 충분하지 않다`() {
        assertFalse(GemmaModelVerification.isSizeOnlySufficient(fileExists = false, fileLength = bytes))
    }

    @Test
    fun `크기가 다르면 크기만으로 충분하지 않다`() {
        assertFalse(GemmaModelVerification.isSizeOnlySufficient(fileExists = true, fileLength = bytes - 1))
    }

    // --- isHashCacheForFile (파일 지문) ------------------------------------

    @Test
    fun `크기와 수정시각이 모두 같으면 캐시가 현재 파일의 것이다`() {
        assertTrue(
            GemmaModelVerification.isHashCacheForFile(
                cachedLength = 1_000L,
                cachedLastModified = 2_000L,
                fileLength = 1_000L,
                fileLastModified = 2_000L
            )
        )
    }

    @Test
    fun `수정시각이 다르면 다른 파일로 보고 캐시를 무효화한다`() {
        assertFalse(
            GemmaModelVerification.isHashCacheForFile(
                cachedLength = 1_000L,
                cachedLastModified = 2_000L,
                fileLength = 1_000L,
                fileLastModified = 9_999L
            )
        )
    }

    @Test
    fun `크기가 다르면 다른 파일로 보고 캐시를 무효화한다`() {
        assertFalse(
            GemmaModelVerification.isHashCacheForFile(
                cachedLength = 1_000L,
                cachedLastModified = 2_000L,
                fileLength = 500L,
                fileLastModified = 2_000L
            )
        )
    }

    // --- hashRetryDelayMillis (백오프) -------------------------------------

    @Test
    fun `첫 실패 후에는 5초를 기다린다`() {
        assertEquals(5_000L, GemmaModelVerification.hashRetryDelayMillis(previousAttempts = 0))
    }

    @Test
    fun `두 번째 실패 후에는 30초를 기다린다`() {
        assertEquals(30_000L, GemmaModelVerification.hashRetryDelayMillis(previousAttempts = 1))
    }

    @Test
    fun `세 번째 실패부터는 5분을 기다린다`() {
        assertEquals(300_000L, GemmaModelVerification.hashRetryDelayMillis(previousAttempts = 2))
    }

    @Test
    fun `실패 횟수가 많아도 5분을 넘지 않는다`() {
        assertEquals(300_000L, GemmaModelVerification.hashRetryDelayMillis(previousAttempts = 99))
    }

    // --- shouldRetryHashVerification ---------------------------------------

    @Test
    fun `IO 오류 기록이 없으면 즉시 재시도한다`() {
        assertTrue(
            GemmaModelVerification.shouldRetryHashVerification(
                ioError = false,
                retryAfterMillis = Long.MAX_VALUE,
                nowMillis = 0L
            )
        )
    }

    @Test
    fun `백오프가 끝나지 않았으면 재시도하지 않는다`() {
        assertFalse(
            GemmaModelVerification.shouldRetryHashVerification(
                ioError = true,
                retryAfterMillis = 10_000L,
                nowMillis = 9_999L
            )
        )
    }

    @Test
    fun `백오프 시각이 지나면 재시도한다`() {
        assertTrue(
            GemmaModelVerification.shouldRetryHashVerification(
                ioError = true,
                retryAfterMillis = 10_000L,
                nowMillis = 10_000L
            )
        )
    }

    @Test
    fun `재시도 시각이 기록되지 않았으면 재시도한다`() {
        assertTrue(
            GemmaModelVerification.shouldRetryHashVerification(
                ioError = true,
                retryAfterMillis = 0L,
                nowMillis = 0L
            )
        )
    }

    // --- isHashVerificationExhausted ---------------------------------------

    @Test
    fun `실패 4회까지는 재시도로 안내한다`() {
        assertFalse(GemmaModelVerification.isHashVerificationExhausted(attempts = 4))
    }

    @Test
    fun `실패 5회 이상이면 재다운로드를 안내한다`() {
        assertTrue(GemmaModelVerification.isHashVerificationExhausted(attempts = 5))
        assertTrue(GemmaModelVerification.isHashVerificationExhausted(attempts = 10))
    }
}