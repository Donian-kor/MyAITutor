package com.donian.myaitutor

import android.app.Application
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.util.Log
import com.donian.myaitutor.ocr.OcrBlock
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
                Log.e("MyAITutorApplication", "PaddleOCR 모델을 불러오지 못했습니다.", error)
                paddleOcr.completeExceptionally(error)
            }
        }
    }

    override suspend fun recognize(imageFile: File): List<OcrBlock> {
        val bitmap = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(imageFile.absolutePath)
        } ?: throw IllegalArgumentException("이미지를 불러오지 못했습니다.")

        return try {
            val results = paddleOcr.await().recognize(bitmap).results
            results.mapIndexed { index, result ->
                // PaddleOCR 결과에서 위치, 신뢰도 등 추가 정보 추출
                // SDK가 제공하는 필드에 따라 안전하게 접근
                val bounds = extractBounds(result)
                val confidence = if (result.confidence >= 0) result.confidence else null
                // 텍스트 유형 추론 (단순 휴리스틱: 수식 기호 포함 여부)
                val blockType = inferBlockType(result.text)
                OcrBlock(
                    text = result.text,
                    type = blockType,
                    bounds = bounds,
                    confidence = confidence,
                    readingOrder = index,
                    sourceFile = imageFile.absolutePath
                )
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun extractBounds(result: Any): Rect? {
        // PaddleOCR SDK가 제공하는 필드에 따라 안전하게 접근
        // SDK가 제공하지 않는 필드는 null로 처리
        return try {
            val boundingBox = result.javaClass.getDeclaredField("bounding_box").apply { isAccessible = true }.get(result)
            if (boundingBox != null) {
                val left = boundingBox.javaClass.getDeclaredField("left").apply { isAccessible = true }.getFloat(boundingBox)
                val top = boundingBox.javaClass.getDeclaredField("top").apply { isAccessible = true }.getFloat(boundingBox)
                val right = boundingBox.javaClass.getDeclaredField("right").apply { isAccessible = true }.getFloat(boundingBox)
                val bottom = boundingBox.javaClass.getDeclaredField("bottom").apply { isAccessible = true }.getFloat(boundingBox)
                Rect(
                    left.toInt(),
                    top.toInt(),
                    right.toInt(),
                    bottom.toInt()
                )
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun inferBlockType(text: String): OcrBlock.OcrBlockType {
        val trimmed = text.trim()
        // 표 패턴 분류: 파이프(|), 탭(\t), 3개 이상의 공백으로 구분된 다단 셀, 표 헤더/구분선 패턴
        val hasPipeTable = trimmed.count { it == '|' } >= 2 || trimmed.contains("+-")
        val hasTabTable = trimmed.contains("\t")
        val hasMultiColumnSpaces = trimmed.split("""\s{3,}""".toRegex()).size >= 3
        if (hasPipeTable || hasTabTable || hasMultiColumnSpaces) {
            return OcrBlock.OcrBlockType.TABLE
        }

        // 수식 기호가 포함되면 FORMULA로 분류
        val formulaSymbols = "∫∑∏√±×÷≠≤≥∞∂∇∆∈∉⊂⊃∪∩∀∃∴∵∝"
        if (trimmed.any { formulaSymbols.contains(it) }) {
            return OcrBlock.OcrBlockType.FORMULA
        }
        // 간단한 패턴 기반: 분수, 지수, 변수 패턴
        val hasFractionPattern = trimmed.matches(""".*\d+/\d+.*""".toRegex())
        val hasVariablePower = trimmed.matches(""".*[xX]\^\d+.*""".toRegex())
        val hasScientificNotation = trimmed.matches(""".*\d+\.\d+[eE][+-]?\d+.*""".toRegex())
        if (hasFractionPattern || hasVariablePower || hasScientificNotation) {
            return OcrBlock.OcrBlockType.FORMULA
        }
        return OcrBlock.OcrBlockType.TEXT
    }

    companion object {
        const val TAG = "MyAITutorApplication"
    }
}