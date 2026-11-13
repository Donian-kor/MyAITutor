package com.donian.myaitutor

import android.app.Application
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.util.Log
import com.donian.myaitutor.ocr.OcrBlock
import com.donian.myaitutor.ocr.OcrImageEnhancer
import com.donian.myaitutor.ocr.OcrTextCandidate
import com.donian.myaitutor.ocr.OcrTextPostProcessor
import com.donian.myaitutor.ocr.OcrTextRecognizer
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.model.OCRResult
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
        // 모델 로딩이 끝난 뒤에만 전처리를 시작해 실패 시 낭비를 줄입니다.
        val ocr = paddleOcr.await()

        var enhancedFile: File? = null
        var bitmap: android.graphics.Bitmap? = null
        try {
            val targetFile = withContext(Dispatchers.IO) {
                enhancedFile = OcrImageEnhancer.enhance(imageFile)
                enhancedFile ?: imageFile
            }
            bitmap = withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(targetFile.absolutePath)
            } ?: throw IllegalArgumentException("이미지를 불러오지 못했습니다.")

            val decodedBitmap = bitmap
            val results = ocr.recognize(decodedBitmap).results
            val candidates = results.map { result ->
                val bounds = extractBounds(result)
                OcrTextCandidate(
                    text = result.text,
                    confidence = if (result.confidence >= 0f) result.confidence else null,
                    left = bounds?.left ?: 0,
                    top = bounds?.top ?: 0,
                    right = bounds?.right ?: 0,
                    bottom = bounds?.bottom ?: 0,
                    hasGeometry = bounds != null
                )
            }

            return OcrTextPostProcessor.process(candidates).mapIndexed { index, candidate ->
                val bounds = if (candidate.hasGeometry) {
                    Rect(candidate.left, candidate.top, candidate.right, candidate.bottom)
                } else {
                    null
                }
                OcrBlock(
                    text = candidate.text,
                    type = inferBlockType(candidate.text),
                    bounds = bounds,
                    confidence = candidate.confidence,
                    readingOrder = index,
                    sourceFile = imageFile.absolutePath
                )
            }
        } finally {
            bitmap?.recycle()
            enhancedFile?.let { file ->
                runCatching { if (file.exists()) file.delete() }
            }
        }
    }

    /**
     * PaddleOCR의 사각형 좌표(4개 점)에서 화면 표시·정렬에 필요한 최소/최대 경계를 계산합니다.
     */
    private fun extractBounds(result: OCRResult): Rect? {
        val points = result.box.points
        if (points.isEmpty()) return null

        val xs = points.map { it.x }
        val ys = points.map { it.y }
        if (xs.any { !it.isFinite() } || ys.any { !it.isFinite() }) return null

        val left = xs.minOrNull()?.toInt() ?: return null
        val top = ys.minOrNull()?.toInt() ?: return null
        val right = xs.maxOrNull()?.toInt() ?: return null
        val bottom = ys.maxOrNull()?.toInt() ?: return null
        if (right <= left || bottom <= top) return null
        return Rect(left, top, right, bottom)
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