package com.donian.myaitutor

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.donian.myaitutor.ocr.OcrTextRecognizer
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.util.OpenCVUtils
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MyAITutorApplication : Application(), OcrTextRecognizer {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val paddleOcr = CompletableDeferred<PaddleOCR>()

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            try {
                check(OpenCVUtils.init(this@MyAITutorApplication)) {
                    "OpenCV 네이티브 라이브러리를 초기화하지 못했습니다."
                }
                paddleOcr.complete(PaddleOCR.create(this@MyAITutorApplication))
            } catch (error: CancellationException) {
                paddleOcr.completeExceptionally(error)
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "PaddleOCR 모델을 불러오지 못했습니다.", error)
                paddleOcr.completeExceptionally(error)
            }
        }
    }

    override suspend fun recognize(imageFile: File): List<String> {
        val bitmap = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(imageFile.absolutePath)
        } ?: throw IllegalArgumentException("이미지를 불러오지 못했습니다.")

        return try {
            paddleOcr.await().recognize(bitmap).results.map { it.text }
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        const val TAG = "MyAITutorApplication"
    }
}
