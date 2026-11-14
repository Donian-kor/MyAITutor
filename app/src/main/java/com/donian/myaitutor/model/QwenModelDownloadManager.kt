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
 * Qwen3-4B-Instruct-2507 LiteRT-LM 모델 다운로더.
 * Gemma 다운로드 관리자와 완전히 분리된 전용 인스턴스/저장 키를 사용합니다.
 */
class QwenModelDownloadManager(context: Context) : ModelDownloadRepository {
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

    /** 현재 파일과 저장된 해시 검증 상태로 [QwenModelVerification] 판정을 수행한다. */
    private fun verifyCurrentFile(
        completedPreviously: Boolean,
        hashVerificationRequired: Boolean
    ): QwenModelVerification.Verdict = QwenModelVerification.decide(
        completedPreviously = completedPreviously,
        fileExists = modelFile.isFile,
        fileLength = if (modelFile.isFile) modelFile.length() else 0L,
        hashVerified = preferences.getBoolean(KEY_HASH_VERIFIED, false),
        hashMismatch = preferences.getBoolean(KEY_HASH_MISMATCH, false),
        hashVerificationRequired = hashVerificationRequired,
        expectedBytes = EXPECTED_MODEL_BYTES
    )

    /** 완료된 파일에 대한 스냅샷을 만든다. READY가 아니면 실패 스냅샷으로 변환한다. */
    private fun completedFileSnapshot(total: Long): ModelDownloadSnapshot =
        when (verifyCurrentFile(true, EXPECTED_MODEL_SHA256.isNotEmpty())) {
            QwenModelVerification.Verdict.READY -> ModelDownloadSnapshot(
                status = ModelDownloadStatus.READY,
                downloadedBytes = modelFile.length(),
                totalBytes = total,
                message = appContext.getString(R.string.dl_msg_ready)
            )

            QwenModelVerification.Verdict.NEEDS_HASH_VERIFICATION -> ModelDownloadSnapshot(
                status = ModelDownloadStatus.VERIFYING,
                downloadedBytes = modelFile.length(),
                totalBytes = total,
                message = appContext.getString(R.string.dl_msg_verifying)
            )

            QwenModelVerification.Verdict.MISMATCH -> failedSnapshot(
                appContext.getString(R.string.dl_msg_hash_mismatch)
            )

            QwenModelVerification.Verdict.NOT_INSTALLED -> failedSnapshot(
                appContext.getString(R.string.dl_msg_size_too_small)
            )
        }

    override fun snapshot(): ModelDownloadSnapshot {
        val downloadId = preferences.getLong(KEY_DOWNLOAD_ID, -1L)
        val completedPreviously = preferences.getBoolean(KEY_COMPLETED, false)

        // 판정 규칙은 QwenModelVerification(순수 로직)에 두고 여기서는 상태만 매핑한다.
        if (completedPreviously) {
            when (
                verifyCurrentFile(
                    completedPreviously = true,
                    hashVerificationRequired = EXPECTED_MODEL_SHA256.isNotEmpty()
                )
            ) {
                QwenModelVerification.Verdict.READY -> return ModelDownloadSnapshot(
                    status = ModelDownloadStatus.READY,
                    downloadedBytes = modelFile.length(),
                    totalBytes = EXPECTED_MODEL_BYTES,
                    message = appContext.getString(R.string.dl_msg_ready)
                )

                QwenModelVerification.Verdict.NEEDS_HASH_VERIFICATION -> return ModelDownloadSnapshot(
                    status = ModelDownloadStatus.VERIFYING,
                    downloadedBytes = modelFile.length(),
                    totalBytes = EXPECTED_MODEL_BYTES,
                    message = appContext.getString(R.string.dl_msg_verifying)
                )

                QwenModelVerification.Verdict.MISMATCH -> return failedSnapshot(
                    appContext.getString(R.string.dl_msg_hash_mismatch)
                )

                // NOT_INSTALLED: 아래 다운로드 기록 조회로 계속 진행한다.
                QwenModelVerification.Verdict.NOT_INSTALLED -> Unit
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
        }
    }

    override fun isModelReady(): Boolean = snapshot().status == ModelDownloadStatus.READY

    override fun needsHashVerification(): Boolean {
        if (EXPECTED_MODEL_SHA256.isEmpty()) return false
        if (!modelFile.isFile || modelFile.length() != EXPECTED_MODEL_BYTES) return false
        if (!preferences.getBoolean(KEY_COMPLETED, false)) return false
        if (preferences.getBoolean(KEY_HASH_VERIFIED, false)) return false
        if (preferences.getBoolean(KEY_HASH_MISMATCH, false)) return false
        return true
    }

    override suspend fun verifyModelHash(): Boolean = withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (EXPECTED_MODEL_SHA256.isEmpty()) return@withContext true
        if (!modelFile.isFile) return@withContext false
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
            }
            match
        } catch (e: Exception) {
            false
        }
    }

    private fun failedSnapshot(message: String) = ModelDownloadSnapshot(
        status = ModelDownloadStatus.FAILED,
        downloadedBytes = 0L,
        totalBytes = EXPECTED_MODEL_BYTES,
        message = message
    )

    companion object {
        const val MODEL_DOWNLOAD_URL =
            "https://huggingface.co/litert-community/Qwen3-4B-Instruct-2507/resolve/6ee6014fa9193ea4dbb309f3305d6ee0d78db5a0/qwen3_4b_instruct_2507_mixed_int4.litertlm?download=true"
        const val MODEL_FILE_NAME = "qwen3_4b_instruct_2507_mixed_int4.litertlm"
        const val EXPECTED_MODEL_BYTES = 2_659_057_664L
        const val EXPECTED_MODEL_SHA256 = "9e48b165836256f5344d9d044930607b9c47f6ef34e27f82e96881664f3ba2fd"
        const val MODEL_REVISION = "6ee6014fa9193ea4dbb309f3305d6ee0d78db5a0"
        private const val PREFS_NAME = "focus_ai_qwen_model_manager"
        private const val KEY_DOWNLOAD_ID = "download_id"
        private const val KEY_COMPLETED = "model_completed"
        private const val KEY_HASH_VERIFIED = "model_hash_verified"
        private const val KEY_HASH_MISMATCH = "model_hash_mismatch"
    }
}