package com.donian.myaitutor.model

/**
 * Qwen 모델 파일 무결성 판정 규칙 (순수 로직).
 *
 * [QwenModelDownloadManager.snapshot]이 Android `DownloadManager`·`Context`에 의존하는
 * 반면, 이 객체는 판정 규칙만 담아 JVM 단위 테스트가 가능하게 한다.
 *
 * READY가 되려면 **모든** 조건을 만족해야 한다 (계획서 5.1):
 * 1. 이전 다운로드가 완료로 기록되어 있다.
 * 2. 파일이 존재하고 길이가 정확히 [QwenModelDownloadManager.EXPECTED_MODEL_BYTES]와 같다.
 * 3. SHA-256이 일치한다 (해시 검증이 필요한 경우).
 */
object QwenModelVerification {

    /** 크기·해시 판정 결과. [ModelDownloadStatus]로 변환할 수 있다. */
    enum class Verdict {
        /** 모든 조건 통과. READY. */
        READY,

        /** 크기는 맞지만 아직 해시 검증을 하지 않았다. */
        NEEDS_HASH_VERIFICATION,

        /** 크기 또는 해시가 승인값과 다르다. 재다운로드가 필요하다. */
        MISMATCH,

        /** 파일이 없거나 크기가 승인값보다 작다. */
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
        expectedBytes: Long = QwenModelDownloadManager.EXPECTED_MODEL_BYTES
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
        expectedBytes: Long = QwenModelDownloadManager.EXPECTED_MODEL_BYTES
    ): Boolean = fileExists && fileLength == expectedBytes

    /**
     * 다운로드를 시작할 수 있는지 여부.
     *
     * Wi-Fi 연결은 호출부가 확인하며, 여기서는 저장 공간과 가용 메모리를 판정한다.
     * 모델 파일이 2.6GB 이상이라 여유 공간이 부족하면 중간에 실패하고,
     * 가용 메모리가 [MIN_DOWNLOAD_AVAIL_BYTES] 미만이면 시스템이 앱을 종료할 수 있어 시작하지 않는다.
     */
    fun canStartDownload(
        availableStorageBytes: Long,
        availableMemoryBytes: Long,
        modelBytes: Long = QwenModelDownloadManager.EXPECTED_MODEL_BYTES
    ): Boolean =
        availableStorageBytes >= modelBytes + DOWNLOAD_STORAGE_MARGIN_BYTES &&
            availableMemoryBytes >= MIN_DOWNLOAD_AVAIL_BYTES

    /** 다운로드 시작 시 추가로 확보해야 하는 저장 공간 여유분(bytes). */
    const val DOWNLOAD_STORAGE_MARGIN_BYTES: Long = 600_000_000L

    /** 다운로드를 시작하기 위해 필요한 최소 가용 메모리(bytes). */
    const val MIN_DOWNLOAD_AVAIL_BYTES: Long = 3_000_000_000L
}