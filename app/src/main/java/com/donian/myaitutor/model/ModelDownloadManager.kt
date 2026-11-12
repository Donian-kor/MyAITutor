package com.donian.myaitutor.model

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import java.io.IOException
import java.io.File
import kotlinx.coroutines.withContext

/**
 * Gemma 4 E4B-IT LiteRT-LM model downloader.
 * The model is kept outside the APK in the app-specific downloads directory.
 */
class ModelDownloadManager(context: Context) {
    private val appContext = context.applicationContext
    private val downloadManager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val modelFile: File
        get() {
            val directory = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(appContext.filesDir, "downloads")
            if (!directory.exists()) directory.mkdirs()
            return File(directory, MODEL_FILE_NAME)
        }

    fun snapshot(): ModelDownloadSnapshot {
        val downloadId = preferences.getLong(KEY_DOWNLOAD_ID, -1L)
        val completedPreviously = preferences.getBoolean(KEY_COMPLETED, false)

        if (completedPreviously && modelFile.isFile && modelFile.length() >= MIN_MODEL_BYTES) {
            if (EXPECTED_MODEL_SHA256.isNotEmpty() && preferences.getBoolean(KEY_HASH_MISMATCH, false)) {
                return failedSnapshot("모델 파일 무결성 검증에 실패했습니다. 모델을 다시 다운로드해 주세요.")
            }
            return ModelDownloadSnapshot(
                status = ModelDownloadStatus.READY,
                downloadedBytes = modelFile.length(),
                totalBytes = EXPECTED_MODEL_BYTES,
                message = "AI 모델 설치 완료"
            )
        }

        if (downloadId < 0L) {
            return ModelDownloadSnapshot(
                status = ModelDownloadStatus.NOT_INSTALLED,
                downloadedBytes = 0L,
                totalBytes = EXPECTED_MODEL_BYTES,
                message = "Gemma 4 E4B 모델이 설치되지 않았습니다."
            )
        }

        val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
            ?: return failedSnapshot("다운로드 상태를 확인할 수 없습니다.")

        cursor.use {
            if (!it.moveToFirst()) return failedSnapshot("다운로드 기록을 찾을 수 없습니다.")

            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))

            return when (status) {
                DownloadManager.STATUS_PENDING -> ModelDownloadSnapshot(
                    ModelDownloadStatus.QUEUED, downloaded, total,
                    "다운로드 대기 중 · Wi-Fi 연결을 확인해 주세요."
                )
                DownloadManager.STATUS_RUNNING -> ModelDownloadSnapshot(
                    ModelDownloadStatus.DOWNLOADING, downloaded, total,
                    "Gemma 4 E4B 모델 다운로드 중"
                )
                DownloadManager.STATUS_PAUSED -> ModelDownloadSnapshot(
                    ModelDownloadStatus.PAUSED, downloaded, total,
                    "다운로드 일시 중지 · Wi-Fi 연결을 확인해 주세요."
                )
                DownloadManager.STATUS_SUCCESSFUL -> {
                    if (modelFile.isFile && modelFile.length() >= MIN_MODEL_BYTES) {
                        if (EXPECTED_MODEL_SHA256.isNotEmpty() && preferences.getBoolean(KEY_HASH_MISMATCH, false)) {
                            return failedSnapshot("모델 파일 무결성 검증에 실패했습니다. 모델을 다시 다운로드해 주세요.")
                        }
                        preferences.edit().putBoolean(KEY_COMPLETED, true).apply()
                        ModelDownloadSnapshot(
                            ModelDownloadStatus.READY,
                            modelFile.length(),
                            if (total > 0L) total else EXPECTED_MODEL_BYTES,
                            "AI 모델 설치 완료"
                        )
                    } else {
                        failedSnapshot("다운로드 파일 크기가 예상보다 작습니다. 모델을 다시 다운로드해 주세요.")
                    }
                }
                DownloadManager.STATUS_FAILED -> {
                    val reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    failedSnapshot("다운로드 실패 (오류 코드: $reason). 다시 시도해 주세요.")
                }
                else -> failedSnapshot("알 수 없는 다운로드 상태입니다.")
            }
        }
    }

    fun startDownload(): Long {
        val storageDirectory = modelFile.parentFile ?: appContext.filesDir
        val availableBytes = StatFs(storageDirectory.absolutePath).availableBytes
        val requiredBytes = EXPECTED_MODEL_BYTES + 600_000_000L
        if (availableBytes < requiredBytes) {
            throw IOException("저장 공간이 부족합니다. 약 4.3GB 이상의 여유 공간을 확보해 주세요.")
        }

        val existingId = preferences.getLong(KEY_DOWNLOAD_ID, -1L)
        if (existingId >= 0L) {
            runCatching { downloadManager.remove(existingId) }
        }
        modelFile.delete()
        preferences.edit().putBoolean(KEY_COMPLETED, false).apply()

        val request = DownloadManager.Request(Uri.parse(MODEL_DOWNLOAD_URL))
            .setTitle("MyAITutor 모델 다운로드")
            .setDescription("Gemma 4 E4B-IT · 약 3.66GB · Wi-Fi 전용")
            .setMimeType("application/octet-stream")
            .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                appContext,
                Environment.DIRECTORY_DOWNLOADS,
                MODEL_FILE_NAME
            )
            .addRequestHeader("User-Agent", "MyAITutor-Android/0.0.1")

        val id = downloadManager.enqueue(request)
        preferences.edit().putLong(KEY_DOWNLOAD_ID, id).apply()
        return id
    }

    fun deleteModel() {
        val id = preferences.getLong(KEY_DOWNLOAD_ID, -1L)
        if (id >= 0L) runCatching { downloadManager.remove(id) }
        modelFile.delete()
        preferences.edit()
            .remove(KEY_DOWNLOAD_ID)
            .putBoolean(KEY_COMPLETED, false)
            .putBoolean(KEY_HASH_VERIFIED, false)
            .putBoolean(KEY_HASH_MISMATCH, false)
            .apply()
    }

    fun isModelReady(): Boolean = snapshot().status == ModelDownloadStatus.READY

    /**
     * 해시 검증이 필요한지 여부. EXPECTED_MODEL_SHA256이 비어 있으면 검증을 건너뜁니다.
     */
    fun needsHashVerification(): Boolean {
        if (EXPECTED_MODEL_SHA256.isEmpty()) return false
        if (!modelFile.isFile || modelFile.length() < MIN_MODEL_BYTES) return false
        if (preferences.getBoolean(KEY_HASH_VERIFIED, false)) return false
        if (preferences.getBoolean(KEY_HASH_MISMATCH, false)) return false
        return true
    }

    /**
     * 모델 파일의 SHA-256을 계산해 기대값과 비교합니다.
     * IO 스레드에서 호출해야 합니다. 결과는 SharedPreferences에 캐시됩니다.
     */
    suspend fun verifyModelHash(): Boolean = withContext(kotlinx.coroutines.Dispatchers.IO) {
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
            preferences.edit()
                .putBoolean(KEY_HASH_VERIFIED, match)
                .putBoolean(KEY_HASH_MISMATCH, !match)
                .apply()
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
            "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm?download=true"
        const val MODEL_FILE_NAME = "gemma-4-E4B-it.litertlm"
        const val EXPECTED_MODEL_BYTES = 3_659_530_240L
        // 모델 파일의 SHA-256 (소문자 hex). 비어 있으면 해시 검증을 건너뜁니다.
        const val EXPECTED_MODEL_SHA256 = ""
        private const val MIN_MODEL_BYTES = 3_500_000_000L
        private const val PREFS_NAME = "focus_ai_model_manager"
        private const val KEY_DOWNLOAD_ID = "download_id"
        private const val KEY_COMPLETED = "model_completed"
        private const val KEY_HASH_VERIFIED = "model_hash_verified"
        private const val KEY_HASH_MISMATCH = "model_hash_mismatch"
    }
}

enum class ModelDownloadStatus {
    NOT_INSTALLED,
    QUEUED,
    DOWNLOADING,
    PAUSED,
    READY,
    FAILED
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
        return if (gb >= 1.0) String.format("%.2f GB", gb) else String.format("%.0f MB", bytes / 1_000_000.0)
    }
}