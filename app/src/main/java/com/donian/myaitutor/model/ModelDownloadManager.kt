package com.donian.myaitutor.model

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import java.io.IOException
import java.io.File

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
            return ModelDownloadSnapshot(
                status = ModelDownloadStatus.READY,
                downloadedBytes = modelFile.length(),
                totalBytes = EXPECTED_MODEL_BYTES,
                message = "AI Î™®Îç∏ ?§Ïπò ?ÑÎ£å"
            )
        }

        if (downloadId < 0L) {
            return ModelDownloadSnapshot(
                status = ModelDownloadStatus.NOT_INSTALLED,
                downloadedBytes = 0L,
                totalBytes = EXPECTED_MODEL_BYTES,
                message = "Gemma 4 E4B Î™®Îç∏???§Ïπò?òÏ? ?äÏïò?µÎãà??"
            )
        }

        val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
            ?: return failedSnapshot("?§Ïö¥Î°úÎìú ?ÅÌÉúÎ•??ïÏù∏?????ÜÏäµ?àÎã§.")

        cursor.use {
            if (!it.moveToFirst()) return failedSnapshot("?§Ïö¥Î°úÎìú ??™©??Ï∞æÏùÑ ???ÜÏäµ?àÎã§.")

            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))

            return when (status) {
                DownloadManager.STATUS_PENDING -> ModelDownloadSnapshot(
                    ModelDownloadStatus.QUEUED, downloaded, total,
                    "?§Ïö¥Î°úÎìú ?ÄÍ∏?Ï§?¬∑ Wi-Fi ?∞Í≤∞???ïÏù∏??Ï£ºÏÑ∏??"
                )
                DownloadManager.STATUS_RUNNING -> ModelDownloadSnapshot(
                    ModelDownloadStatus.DOWNLOADING, downloaded, total,
                    "Gemma 4 E4B Î™®Îç∏ ?§Ïö¥Î°úÎìú Ï§?
                )
                DownloadManager.STATUS_PAUSED -> ModelDownloadSnapshot(
                    ModelDownloadStatus.PAUSED, downloaded, total,
                    "?§Ïö¥Î°úÎìú ?ºÏãú Ï§ëÏ? ¬∑ Wi-Fi ?∞Í≤∞???ïÏù∏??Ï£ºÏÑ∏??"
                )
                DownloadManager.STATUS_SUCCESSFUL -> {
                    if (modelFile.isFile && modelFile.length() >= MIN_MODEL_BYTES) {
                        preferences.edit().putBoolean(KEY_COMPLETED, true).apply()
                        ModelDownloadSnapshot(
                            ModelDownloadStatus.READY,
                            modelFile.length(),
                            if (total > 0L) total else EXPECTED_MODEL_BYTES,
                            "AI Î™®Îç∏ ?§Ïπò ?ÑÎ£å"
                        )
                    } else {
                        failedSnapshot("?§Ïö¥Î°úÎìú ?åÏùº ?¨Í∏∞Í∞Ä ?àÏÉÅÎ≥¥Îã§ ?ëÏäµ?àÎã§. Î™®Îç∏???§Ïãú ?§Ïö¥Î°úÎìú??Ï£ºÏÑ∏??")
                    }
                }
                DownloadManager.STATUS_FAILED -> {
                    val reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    failedSnapshot("?§Ïö¥Î°úÎìú ?§Ìå® (?§Î•ò ÏΩîÎìú: $reason). ?§Ïãú ?úÎèÑ??Ï£ºÏÑ∏??")
                }
                else -> failedSnapshot("?????ÜÎäî ?§Ïö¥Î°úÎìú ?ÅÌÉú?ÖÎãà??")
            }
        }
    }

    fun startDownload(): Long {
        val storageDirectory = modelFile.parentFile ?: appContext.filesDir
        val availableBytes = StatFs(storageDirectory.absolutePath).availableBytes
        val requiredBytes = EXPECTED_MODEL_BYTES + 600_000_000L
        if (availableBytes < requiredBytes) {
            throw IOException("?Ä??Í≥µÍ∞Ñ??Î∂ÄÏ°±Ìï©?àÎã§. ??4.3GB ?¥ÏÉÅ???¨Ïú† Í≥µÍ∞Ñ???ïÎ≥¥??Ï£ºÏÑ∏??")
        }

        val existingId = preferences.getLong(KEY_DOWNLOAD_ID, -1L)
        if (existingId >= 0L) {
            runCatching { downloadManager.remove(existingId) }
        }
        modelFile.delete()
        preferences.edit().putBoolean(KEY_COMPLETED, false).apply()

        val request = DownloadManager.Request(Uri.parse(MODEL_DOWNLOAD_URL))
            .setTitle("MyAITutor Î™®Îç∏ ?§Ïö¥Î°úÎìú")
            .setDescription("Gemma 4 E4B-IT ¬∑ ??3.66GB ¬∑ Wi-Fi ?ÑÏö©")
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
            .apply()
    }

    fun isModelReady(): Boolean = snapshot().status == ModelDownloadStatus.READY

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
        const val EXPECTED_MODEL_BYTES = 3_654_000_000L
        private const val MIN_MODEL_BYTES = 3_500_000_000L
        private const val PREFS_NAME = "focus_ai_model_manager"
        private const val KEY_DOWNLOAD_ID = "download_id"
        private const val KEY_COMPLETED = "model_completed"
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
        get() = if (totalBytes > 0L) formatBytes(totalBytes) else "?ïÏù∏ Ï§?

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 MB"
        val gb = bytes / 1_000_000_000.0
        return if (gb >= 1.0) String.format("%.2f GB", gb) else String.format("%.0f MB", bytes / 1_000_000.0)
    }
}
