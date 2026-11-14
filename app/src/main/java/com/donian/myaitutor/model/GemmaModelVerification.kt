package com.donian.myaitutor.model

/**
 * Gemma 모델 파일 무결성 판정 규칙 (순수 로직).
 *
 * [ModelDownloadManager.snapshot]이 Android `DownloadManager`·`Context`에 의존하는 반면,
 * 이 객체는 판정 규칙만 담아 JVM 단위 테스트가 가능하게 한다. Qwen의 [QwenModelVerification]과
 * 동일한 구조를 따른다.
 *
 * READY가 되려면 **모든** 조건을 만족해야 한다.
 * 1. 이전 다운로드가 완료로 기록되어 있다.
 * 2. 파일이 존재하고 길이가 정확히 [ModelDownloadManager.EXPECTED_MODEL_BYTES]와 같다.
 * 3. SHA-256이 일치한다 (해시 검증이 필요한 경우).
 *
 * I/O 오류로 검증을 완료하지 못한 경우의 재시도 정책도 함께 담당한다. 검증 실패는
 * [ModelDownloadManager]의 `catch`에서 기록되며, 이 객체는 그 기록을 해석만 한다.
 */
object GemmaModelVerification {

    /** 크기·해시 판정 결과. [ModelDownloadStatus]로 변환할 수 있다. */
    enum class Verdict {
        /** 모든 조건 통과. READY. */
        READY,

        /** 크기는 맞지만 아직 해시 검증을 하지 않았다. */
        NEEDS_HASH_VERIFICATION,

        /** 크기 또는 해시가 승인값과 다르다. 재다운로드가 필요하다. */
        MISMATCH,

        /** 파일이 없거나 크기가 승인값과 다르다. */
        NOT_INSTALLED
    }

    /**
     * 파일 상태를 판정한다.
     *
     * @param completedPreviously DownloadManager가 이전에 완료로 기록했는지 여부.
     * @param fileExists 파일이 실제로 존재하는지 여부.
     * @param fileLength 실제 파일 길이(bytes). [fileExists]가 false면 무시한다.
     * @param hashVerified SHA-256 검증을 통과했는지 여부.
     * @param hashMismatch SHA-256 불일치로 기록된 적이 있는지 여부.
     * @param hashVerificationRequired 앱이 SHA-256 검증을 요구하는지 여부.
     * @param expectedBytes 승인된 정확한 파일 크기(bytes).
     */
    fun decide(
        completedPreviously: Boolean,
        fileExists: Boolean,
        fileLength: Long,
        hashVerified: Boolean,
        hashMismatch: Boolean,
        hashVerificationRequired: Boolean,
        expectedBytes: Long = ModelDownloadManager.EXPECTED_MODEL_BYTES
    ): Verdict {
        // 완료 기록이 없으면 다운로드 자체가 끝나지 않은 것이다.
        if (!completedPreviously) return Verdict.NOT_INSTALLED
        // 파일이 없으면 설치되지 않은 것으로 본다.
        if (!fileExists) return Verdict.NOT_INSTALLED
        // 1바이트라도 다르면 READY가 아니다.
        if (fileLength != expectedBytes) return Verdict.NOT_INSTALLED
        if (!hashVerificationRequired) return Verdict.READY
        // 크기가 같아도 해시가 다르면 READY가 아니다.
        if (hashMismatch) return Verdict.MISMATCH
        if (!hashVerified) return Verdict.NEEDS_HASH_VERIFICATION
        return Verdict.READY
    }

    /**
     * DOWNLOAD_COMPLETE 후 크기만으로 READY로 인정할 수 있는지 여부.
     *
     * 크기는 맞지만 해시 검증이 남아 있으면 false이며, 이 경우 [Verdict.NEEDS_HASH_VERIFICATION]이 된다.
     */
    fun isSizeOnlySufficient(
        fileExists: Boolean,
        fileLength: Long,
        expectedBytes: Long = ModelDownloadManager.EXPECTED_MODEL_BYTES
    ): Boolean = fileExists && fileLength == expectedBytes

    /**
     * 저장된 해시 검증 결과가 지금 파일에 대한 것인지 판정한다.
     *
     * 검증 당시의 크기·수정시각이 현재 파일과 다르면 다른 파일이 같은 경로에 놓인 것이므로
     * 이전 검증 결과를 재사용하지 않는다.
     */
    fun isHashCacheForFile(
        cachedLength: Long,
        cachedLastModified: Long,
        fileLength: Long,
        fileLastModified: Long
    ): Boolean = cachedLength == fileLength && cachedLastModified == fileLastModified

    // ---------------------------------------------------------------------
    // I/O 오류 재시도 정책 (Gemma 전용)
    // ---------------------------------------------------------------------

    /**
     * I/O 오류로 검증에 실패했을 때 다음 시도까지 기다릴 시간(ms).
     *
     * 전체 파일을 다시 읽는 비용이 크므로(약 3.66GB) 폴링마다 재해싱하지 않도록
     * 5초 → 30초 → 5분(상한)으로 늘린다.
     *
     * @param previousAttempts 직전까지 기록된 I/O 실패 횟수. 0이면 첫 실패를 뜻한다.
     */
    fun hashRetryDelayMillis(previousAttempts: Int): Long = when {
        previousAttempts <= 0 -> RETRY_DELAY_FIRST_MS
        previousAttempts == 1 -> RETRY_DELAY_SECOND_MS
        else -> RETRY_DELAY_MAX_MS
    }

    /**
     * 지금 해시 검증을 다시 시도할 수 있는지 여부.
     *
     * I/O 오류 기록이 없으면 항상 true다. 기록이 있으면 [retryAfterMillis]가 지났는지 확인한다.
     *
     * @param nowMillis 현재 시각(epoch millis). 테스트에서 주입한다.
     */
    fun shouldRetryHashVerification(
        ioError: Boolean,
        retryAfterMillis: Long,
        nowMillis: Long
    ): Boolean {
        if (!ioError) return true
        if (retryAfterMillis <= 0L) return true
        return nowMillis >= retryAfterMillis
    }

    /**
     * I/O 오류가 반복되어 폴링 재시도 대신 사용자에게 재다운로드를 안내해야 하는지 여부.
     *
     * @param attempts 기록된 I/O 실패 횟수.
     */
    fun isHashVerificationExhausted(attempts: Int): Boolean = attempts >= MAX_IO_ATTEMPTS_BEFORE_RETRY_PROMPT

    /** 첫 I/O 실패 후 대기 시간(ms). */
    const val RETRY_DELAY_FIRST_MS = 5_000L

    /** 두 번째 I/O 실패 후 대기 시간(ms). */
    const val RETRY_DELAY_SECOND_MS = 30_000L

    /** 세 번째 이후 I/O 실패 시 유지할 최대 대기 시간(ms). */
    const val RETRY_DELAY_MAX_MS = 5 * 60_000L

    /** 이 횟수 이상 실패하면 재시도 대신 재다운로드를 안내한다. */
    const val MAX_IO_ATTEMPTS_BEFORE_RETRY_PROMPT = 5
}