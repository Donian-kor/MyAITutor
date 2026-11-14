package com.donian.myaitutor.model

import android.app.DownloadManager
import androidx.core.net.toUri
import androidx.core.content.edit
import android.content.Context
import com.donian.myaitutor.BuildConfig
import com.donian.myaitutor.R
import android.os.Environment
import android.os.StatFs
import java.io.IOException
import java.io.File
import java.util.Locale
import kotlinx.coroutines.withContext

/**
 * Gemma 4 E4B-IT LiteRT-LM model downloader.
 * The model is kept outside the APK in the app-specific downloads directory.
 */
class ModelDownloadManager(context: Context) : ModelDownloadRepository {
    private val appContext = context.applicationContext
    private val downloadManager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override val modelFile: File
        get() {
            val directory = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(appContext.filesDir, "downloads")
            if (!directory.exists()) directory.mkdirs()
            return File(directory, MODEL_FILE_NAME)
        }

    /**
     * 저장된 해시 검증 결과가 지금 파일을 가리키는지 확인한다.
     *
     * 검증 당시의 크기·수정시각을 함께 저장해, 다른 파일이 같은 경로에 놓이면
     * 이전 검증 결과를 재사용하지 않고 다시 검증하도록 한다.
     */
    private fun isHashCacheForCurrentFile(): Boolean {
        val file = modelFile
        if (!file.isFile) return false
        return GemmaModelVerification.isHashCacheForFile(
            cachedLength = preferences.getLong(KEY_HASH_LENGTH, -1L),
            cachedLastModified = preferences.getLong(KEY_HASH_LAST_MODIFIED, -1L),
            fileLength = file.length(),
            fileLastModified = file.lastModified()
        )
    }

    /** 현재 파일과 저장된 해시 검증 상태로 [GemmaModelVerification] 판정을 수행한다. */
    private fun verifyCurrentFile(
        completedPreviously: Boolean,
        hashVerificationRequired: Boolean
    ): GemmaModelVerification.Verdict {
        val cacheValid = isHashCacheForCurrentFile()
        return GemmaModelVerification.decide(
            completedPreviously = completedPreviously,
            fileExists = modelFile.isFile,
            fileLength = if (modelFile.isFile) modelFile.length() else 0L,
            hashVerified = cacheValid && preferences.getBoolean(KEY_HASH_VERIFIED, false),
            hashMismatch = cacheValid && preferences.getBoolean(KEY_HASH_MISMATCH, false),
            hashVerificationRequired = hashVerificationRequired,
            expectedBytes = EXPECTED_MODEL_BYTES
        )
    }

    /**
     * 검증이 일시 중단된 상태인지 여부.
     *
     * I/O 오류가 기록되어 있고 아직 백오프가 끝나지 않았으면 true다. 이 경우 폴링이
     * 전체 파일 재해싱을 멈추고 안내 문구만 갱신한다.
     */
    private fun isHashVerificationPaused(): Boolean {
        val ioError = preferences.getBoolean(KEY_HASH_IO_ERROR, false)
        if (!ioError) return false
        val retryAfter = preferences.getLong(KEY_HASH_RETRY_AFTER, 0L)
        return !GemmaModelVerification.shouldRetryHashVerification(
            ioError = true,
            retryAfterMillis = retryAfter,
            nowMillis = System.currentTimeMillis()
        )
    }

    /** 일시 중단 상태에서 보여줄 안내 문구를 고른다. */
    private fun pausedVerificationMessage(): String {
        val attempts = preferences.getInt(KEY_HASH_IO_ATTEMPTS, 0)
        return if (GemmaModelVerification.isHashVerificationExhausted(attempts)) {
            appContext.getString(R.string.dl_msg_hash_verify_failed)
        } else {
            appContext.getString(R.string.dl_msg_hash_verify_retrying)
        }
    }

    /** 완료된 파일에 대한 스냅샷을 만든다. READY가 아니면 실패/대기 스냅샷으로 변환한다. */
    private fun completedFileSnapshot(total: Long): ModelDownloadSnapshot =
        when (verifyCurrentFile(true, EXPECTED_MODEL_SHA256.isNotEmpty())) {
            GemmaModelVerification.Verdict.READY -> ModelDownloadSnapshot(
                status = ModelDownloadStatus.READY,
                downloadedBytes = modelFile.length(),
                totalBytes = total,
                message = appContext.getString(R.string.dl_msg_ready)
            )

            GemmaModelVerification.Verdict.NEEDS_HASH_VERIFICATION ->
                if (isHashVerificationPaused()) {
                    // I/O 오류로 검증을 마치지 못했다. READY는 아니지만 실패도 아니므로
                    // VERIFYING을 유지해 건너뛰기와 자동 재시도가 계속 동작하게 한다.
                    ModelDownloadSnapshot(
                        status = ModelDownloadStatus.VERIFYING,
                        downloadedBytes = modelFile.length(),
                        totalBytes = total,
                        message = pausedVerificationMessage()
                    )
                } else {
                    ModelDownloadSnapshot(
                        status = ModelDownloadStatus.VERIFYING,
                        downloadedBytes = modelFile.length(),
                        totalBytes = total,
                        message = appContext.getString(R.string.dl_msg_verifying)
                    )
                }

            GemmaModelVerification.Verdict.MISMATCH -> failedSnapshot(
                appContext.getString(R.string.dl_msg_hash_mismatch)
            )

            GemmaModelVerification.Verdict.NOT_INSTALLED -> failedSnapshot(
                appContext.getString(R.string.dl_msg_size_too_small)
            )
        }

    override fun snapshot(): ModelDownloadSnapshot {
        val downloadId = preferences.getLong(KEY_DOWNLOAD_ID, -1L)
        val completedPreviously = preferences.getBoolean(KEY_COMPLETED, false)

        // 판정 규칙은 GemmaModelVerification(순수 로직)에 두고 여기서는 상태만 매핑한다.
        if (completedPreviously) {
            when (
                verifyCurrentFile(
                    completedPreviously = true,
                    hashVerificationRequired = EXPECTED_MODEL_SHA256.isNotEmpty()
                )
            ) {
                GemmaModelVerification.Verdict.READY -> return ModelDownloadSnapshot(
                    status = ModelDownloadStatus.READY,
                    downloadedBytes = modelFile.length(),
                    totalBytes = EXPECTED_MODEL_BYTES,
                    message = appContext.getString(R.string.dl_msg_ready)
                )

                GemmaModelVerification.Verdict.NEEDS_HASH_VERIFICATION -> return ModelDownloadSnapshot(
                    status = ModelDownloadStatus.VERIFYING,
                    downloadedBytes = modelFile.length(),
                    totalBytes = EXPECTED_MODEL_BYTES,
                    message = if (isHashVerificationPaused()) {
                        pausedVerificationMessage()
                    } else {
                        appContext.getString(R.string.dl_msg_verifying)
                    }
                )

                GemmaModelVerification.Verdict.MISMATCH -> return failedSnapshot(
                    appContext.getString(R.string.dl_msg_hash_mismatch)
                )

                // NOT_INSTALLED: 아래 다운로드 기록 조회로 계속 진행한다.
                GemmaModelVerification.Verdict.NOT_INSTALLED -> Unit
            }
        }

        if (downloadId < 0L) {
            return ModelDownloadSnapshot(
                status = ModelDownloadStatus.NOT_INSTALLED,
                downloadedBytes = 0L,
                totalBytes = EXPECTED_MODEL_BYTES,
                message = appContext.getString(R.string.dl_msg_not_installed)
            )
        }

        val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
            ?: return failedSnapshot(appContext.getString(R.string.dl_msg_status_unavailable))

        cursor.use {
            if (!it.moveToFirst()) return failedSnapshot(appContext.getString(R.string.dl_msg_record_missing))

            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))

            return when (status) {
                DownloadManager.STATUS_PENDING -> ModelDownloadSnapshot(
                    ModelDownloadStatus.QUEUED, downloaded, total,
                    appContext.getString(R.string.dl_msg_queued)
                )
                DownloadManager.STATUS_RUNNING -> ModelDownloadSnapshot(
                    ModelDownloadStatus.DOWNLOADING, downloaded, total,
                    appContext.getString(R.string.dl_msg_downloading)
                )
                DownloadManager.STATUS_PAUSED -> ModelDownloadSnapshot(
                    ModelDownloadStatus.PAUSED, downloaded, total,
                    appContext.getString(R.string.dl_msg_paused)
                )
                DownloadManager.STATUS_SUCCESSFUL -> {
                    preferences.edit { putBoolean(KEY_COMPLETED, true) }
                    completedFileSnapshot(if (total > 0L) total else EXPECTED_MODEL_BYTES)
                }
                DownloadManager.STATUS_FAILED -> {
                    val reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    failedSnapshot(appContext.getString(R.string.dl_msg_failed_code, reason))
                }
                else -> failedSnapshot(appContext.getString(R.string.dl_msg_unknown_status))
            }
        }
    }

    override fun startDownload(): Long {
        val storageDirectory = modelFile.parentFile ?: appContext.filesDir
        val availableBytes = StatFs(storageDirectory.absolutePath).availableBytes
        val requiredBytes = EXPECTED_MODEL_BYTES + 600_000_000L
        if (availableBytes < requiredBytes) {
            throw IOException(appContext.getString(R.string.dl_msg_no_storage))
        }

        val existingId = preferences.getLong(KEY_DOWNLOAD_ID, -1L)
        if (existingId >= 0L) {
            runCatching { downloadManager.remove(existingId) }
        }
        modelFile.delete()
        preferences.edit {
            putBoolean(KEY_COMPLETED, false)
            putBoolean(KEY_HASH_VERIFIED, false)
            putBoolean(KEY_HASH_MISMATCH, false)
            // 새 다운로드는 처음부터 검증해야 하므로 이전 파일 지문과 I/O 오류 기록을 지운다.
            remove(KEY_HASH_LENGTH)
            remove(KEY_HASH_LAST_MODIFIED)
            clearHashIoError()
        }

        val request = DownloadManager.Request(MODEL_DOWNLOAD_URL.toUri())
            .setTitle(appContext.getString(R.string.dl_notification_title))
            .setDescription(appContext.getString(R.string.dl_notification_desc))
            .setMimeType("application/octet-stream")
            .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                appContext,
                Environment.DIRECTORY_DOWNLOADS,
                MODEL_FILE_NAME
            )
            .addRequestHeader("User-Agent", "MyAITutor-Android/${BuildConfig.VERSION_NAME}")

        val id = downloadManager.enqueue(request)
        preferences.edit { putLong(KEY_DOWNLOAD_ID, id) }
        return id
    }

    override fun deleteModel() {
        val id = preferences.getLong(KEY_DOWNLOAD_ID, -1L)
        if (id >= 0L) runCatching { downloadManager.remove(id) }
        modelFile.delete()
        preferences.edit {
            remove(KEY_DOWNLOAD_ID)
            putBoolean(KEY_COMPLETED, false)
            putBoolean(KEY_HASH_VERIFIED, false)
            putBoolean(KEY_HASH_MISMATCH, false)
            remove(KEY_HASH_LENGTH)
            remove(KEY_HASH_LAST_MODIFIED)
            clearHashIoError()
        }
    }

    override fun isModelReady(): Boolean = snapshot().status == ModelDownloadStatus.READY

    /**
     * 해시 검증이 필요한지 여부. EXPECTED_MODEL_SHA256이 비어 있으면 검증을 건너뜁니다.
     * 다운로드 상태가 STATUS_SUCCESSFUL (KEY_COMPLETED == true) 일 때만 검증을 진행합니다.
     *
     * I/O 오류로 실패한 이력이 있고 아직 백오프가 끝나지 않았으면 false를 반환해
     * 폴링이 전체 파일 재해싱을 하지 않게 합니다.
     */
    override fun needsHashVerification(): Boolean {
        if (EXPECTED_MODEL_SHA256.isEmpty()) return false
        if (!modelFile.isFile || modelFile.length() != EXPECTED_MODEL_BYTES) return false
        if (!preferences.getBoolean(KEY_COMPLETED, false)) return false
        // 파일이 바뀌었으면 이전 검증 결과는 무효이며 다시 검증해야 한다.
        if (isHashCacheForCurrentFile()) {
            if (preferences.getBoolean(KEY_HASH_VERIFIED, false)) return false
            if (preferences.getBoolean(KEY_HASH_MISMATCH, false)) return false
        }
        if (isHashVerificationPaused()) return false
        return true
    }

    /**
     * 모델 파일의 SHA-256을 계산해 기대값과 비교합니다.
     * IO 스레드에서 호출해야 합니다. 결과는 SharedPreferences에 캐시됩니다.
     *
     * I/O 오류가 발생하면 성공/불일치와 구분되는 별도 키에 기록하고, 백오프 시각을
     * 함께 저장해 폴링이 3.66GB 전체를 반복해서 읽지 않도록 합니다.
     */
    override suspend fun verifyModelHash(): Boolean = withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (EXPECTED_MODEL_SHA256.isEmpty()) return@withContext true
        if (!modelFile.isFile) return@withContext false
        val length = modelFile.length()
        val lastModified = modelFile.lastModified()
        try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            modelFile.inputStream().use { input ->
                val buffer = ByteArray(1024 * 1024)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            val match = actual.equals(EXPECTED_MODEL_SHA256, ignoreCase = true)
            preferences.edit {
                putBoolean(KEY_HASH_VERIFIED, match)
                putBoolean(KEY_HASH_MISMATCH, !match)
                // 검증 대상 파일의 지문을 함께 기록해 다른 파일의 결과를 재사용하지 않는다.
                putLong(KEY_HASH_LENGTH, length)
                putLong(KEY_HASH_LAST_MODIFIED, lastModified)
                // 검증이 완료되었으므로 I/O 오류 이력을 지운다.
                clearHashIoError()
            }
            match
        } catch (e: Exception) {
            // 파일을 읽지 못했다. 디스크 오류·파일 잠금 등 일시적일 수 있으므로
            // 불일치와 구분해 기록하고 백오프 후 재시도한다.
            recordHashIoError()
            false
        }
    }

    /** I/O 오류 횟수를 늘리고 다음 재시도 시각을 기록합니다. */
    private fun recordHashIoError() {
        val attempts = preferences.getInt(KEY_HASH_IO_ATTEMPTS, 0) + 1
        val delay = GemmaModelVerification.hashRetryDelayMillis(attempts - 1)
        preferences.edit {
            putBoolean(KEY_HASH_IO_ERROR, true)
            putInt(KEY_HASH_IO_ATTEMPTS, attempts)
            putLong(KEY_HASH_RETRY_AFTER, System.currentTimeMillis() + delay)
        }
    }

    /** I/O 오류 기록을 초기화합니다. 성공·삭제·재다운로드 시 호출합니다. */
    private fun clearHashIoError() {
        preferences.edit {
            remove(KEY_HASH_IO_ERROR)
            remove(KEY_HASH_IO_ATTEMPTS)
            remove(KEY_HASH_RETRY_AFTER)
        }
    }

    private fun failedSnapshot(message: String) = ModelDownloadSnapshot(
        status = ModelDownloadStatus.FAILED,
        downloadedBytes = 0L,
        totalBytes = EXPECTED_MODEL_BYTES,
        message = message
    )

    companion object {
        const val MODEL_REVISION = "f254b5ba6e36f7d89cc7892e268e9877bc20f0a0"
        const val MODEL_DOWNLOAD_URL =
            "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/$MODEL_REVISION/gemma-4-E4B-it.litertlm?download=true"
        const val MODEL_FILE_NAME = "gemma-4-E4B-it.litertlm"
        const val EXPECTED_MODEL_BYTES = 3_659_530_240L
        // 모델 파일의 SHA-256 (소문자 hex). 비어 있으면 해시 검증을 건너뜁니다.
        const val EXPECTED_MODEL_SHA256 = "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0"
        /** 설치된 모델 파일로 인정하는 최소 크기. 엔진 초기화 경로에서도 이 값을 참조한다. */
        internal const val MIN_MODEL_BYTES = 3_500_000_000L
        private const val PREFS_NAME = "focus_ai_model_manager"
        private const val KEY_DOWNLOAD_ID = "download_id"
        private const val KEY_COMPLETED = "model_completed"
        private const val KEY_HASH_VERIFIED = "model_hash_verified"
        private const val KEY_HASH_MISMATCH = "model_hash_mismatch"
        /** 검증 당시 파일 크기. 파일 교체 감지용입니다. */
        private const val KEY_HASH_LENGTH = "model_hash_length"
        /** 검증 당시 파일 수정시각. 파일 교체 감지용입니다. */
        private const val KEY_HASH_LAST_MODIFIED = "model_hash_last_modified"
        /** I/O 오류로 검증을 완료하지 못했는지 여부. */
        private const val KEY_HASH_IO_ERROR = "model_hash_io_error"
        /** I/O 오류가 누적된 횟수. 안내 문구 분기에 사용합니다. */
        private const val KEY_HASH_IO_ATTEMPTS = "model_hash_io_attempts"
        /** 다음 해시 재시도 시각(epoch millis). 백오프에 사용합니다. */
        private const val KEY_HASH_RETRY_AFTER = "model_hash_retry_after"
    }
}

enum class ModelDownloadStatus {
    NOT_INSTALLED,
    QUEUED,
    DOWNLOADING,
    PAUSED,
    VERIFYING,
    READY,
    FAILED;

    /** 진행 중이거나 잠시 멈춘 상태인지 여부. 화면이 폴링 간격을 정하는 데 사용한다. */
    fun isTransferring(): Boolean =
        this == QUEUED || this == DOWNLOADING || this == PAUSED || this == VERIFYING
}

data class ModelDownloadSnapshot(
    val status: ModelDownloadStatus,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val message: String
) {
    val progress: Float
        get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

    val downloadedLabel: String
        get() = formatBytes(downloadedBytes)

    val totalLabel: String
        get() = if (totalBytes > 0L) formatBytes(totalBytes) else "알 수 없음"

    internal fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 MB"
        val gb = bytes / 1_000_000_000.0
        val locale = Locale.getDefault()
        return if (gb >= 1.0) {
            String.format(locale, "%.2f GB", gb)
        } else {
            String.format(locale, "%.0f MB", bytes / 1_000_000.0)
        }
    }
}