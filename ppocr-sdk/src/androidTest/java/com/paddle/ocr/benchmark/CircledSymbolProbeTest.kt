// Copyright (c) 2026 PaddlePaddle Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.paddle.ocr.benchmark

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.paddle.ocr.EngineConfig
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.PaddleOCRConfig
import com.paddle.ocr.model.ModelConfig
import com.paddle.ocr.model.OCRRunResult
import com.paddle.ocr.util.OpenCVUtils
import kotlinx.coroutines.runBlocking
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * 원문자 기호(㉠㉡㉢㉮㉯)가 어느 단계에서 실패하는지 확인하는 진단용 계측 테스트.
 *
 * 검증 질문:
 * 1. detection(DB)이 원문자를 박스로 잡는가?
 * 2. 잡혔다면 recognition(CTC)이 어떤 문자열을 내놓는가?
 * 3. det 임계값을 완화하면 잡히는가?
 * 4. 기준선으로 ①~③(사전에 있는 원숫자)은 정상 인식되는가?
 *
 * 각 케이스의 렌더링 이미지는 기기 외부 저장소 ocr_probe/ 에 PNG로 저장된다.
 */
@RunWith(AndroidJUnit4::class)
class CircledSymbolProbeTest {

    companion object {
        private const val TAG = "CircledProbe"

        private lateinit var defaultOcr: PaddleOCR
        private lateinit var permissiveOcr: PaddleOCR
        private lateinit var outputDir: File

        /** 사전에 ㉠ 계열이 있는지 확인할 문자들. */
        private val CIRCLED_HANGUL = listOf('㉠', '㉡', '㉢', '㉣', '㉮', '㉯', '㉰')

        /** 기준선: 사전에 존재함이 확인된 원숫자와 기호. */
        private val BASELINE = listOf('①', '②', '③', '○', '●', '∠')

        @JvmStatic
        @BeforeClass
        fun setUp() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            OpenCVUtils.init(context)

            outputDir = File(context.getExternalFilesDir(null), "ocr_probe")
            outputDir.mkdirs()
            println("[$TAG] Output dir: ${outputDir.absolutePath}")

            runBlocking {
                defaultOcr = PaddleOCR.create(context, PaddleOCRConfig(), EngineConfig())
                permissiveOcr = PaddleOCR.create(
                    context,
                    PaddleOCRConfig(
                        // 작은 기호가 det에서 잘리는지 확인하기 위해 대폭 완화한다.
                        detThresh = 0.2f,
                        detBoxThresh = 0.2f,
                        detUnclipRatio = 1.8f,
                    ),
                    EngineConfig(),
                )
            }
            println("[$TAG] Engines ready")
        }
    }

    /** 사전에 원문자가 실제로 있는지 코드에서 직접 확인한다. */
    @Test
    fun reportDictionaryCoverage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val characterList = ModelConfig.parse(context, "models/rec/inference.yml").characterList

        println("[$TAG] ===== DICTIONARY COVERAGE =====")
        println("[$TAG] characterList.size = ${characterList.size}")

        val charSet = characterList.toSet()
        for (ch in CIRCLED_HANGUL) {
            println("[$TAG] circled hangul '$ch' (U+%04X) present=%b".format(ch.code, ch.toString() in charSet))
        }
        for (ch in BASELINE) {
            println("[$TAG] baseline '$ch' (U+%04X) present=%b".format(ch.code, ch.toString() in charSet))
        }

        // U+3260~U+327F 범위에 사전 항목이 몇 개 있는지 센다.
        val enclosedHangul = characterList.filter { it.length == 1 && (it[0].code in 0x3260..0x327F) }
        println("[$TAG] U+3260-U+327F entries in dict = $enclosedHangul")
        println("[$TAG] ================================")
    }

    /** 원문자 단독 렌더링(큰 크기). det가 박스를 만드는지 확인. */
    @Test
    fun probeStandaloneLargeSymbols() {
        val text = "㉠ ㉡ ㉢"
        val bitmap = renderTextLine(text, textSizePx = 96f, padding = 48)
        runProbe("standalone_large", text, bitmap)
    }

    /** 실제 문제집에 가까운 크기(작은 원문자 단독). */
    @Test
    fun probeStandaloneSmallSymbols() {
        val text = "㉠ ㉡ ㉢"
        val bitmap = renderTextLine(text, textSizePx = 40f, padding = 24)
        runProbe("standalone_small", text, bitmap)
    }

    /** 문장 안에 섞인 원문자. 실제 사용 형태에 가장 가깝다. */
    @Test
    fun probeInlineSymbols() {
        val text = "㉠과 ㉡의 값을 구하시오."
        val bitmap = renderTextLine(text, textSizePx = 56f, padding = 32)
        runProbe("inline_sentence", text, bitmap)
    }

    /** 사용자가 보여준 형태와 유사한 짧은 라인. */
    @Test
    fun probeShortMarkerLine() {
        val text = "㉠, ㉡"
        val bitmap = renderTextLine(text, textSizePx = 48f, padding = 20)
        runProbe("short_marker_line", text, bitmap)
    }

    /** 기준선: 사전에 있는 원숫자·기호는 정상 인식되는지. */
    @Test
    fun probeBaselineSymbols() {
        val text = "① ② ③ ∠ ● ○"
        val bitmap = renderTextLine(text, textSizePx = 56f, padding = 28)
        runProbe("baseline_symbols", text, bitmap)
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private fun runProbe(name: String, expected: String, bitmap: Bitmap) {
        println("[$TAG] ===== PROBE '$name' =====")
        println("[$TAG] rendered text: \"$expected\"  size=${bitmap.width}x${bitmap.height}")

        savePng(name, bitmap)

        val defaultResult = runBlocking { defaultOcr.recognize(bitmap) }
        println("[$TAG] --- default config ---")
        printResult(defaultResult)

        val permissiveResult = runBlocking { permissiveOcr.recognize(bitmap) }
        println("[$TAG] --- permissive config (detThresh=0.2, detBoxThresh=0.2, unclip=1.8) ---")
        printResult(permissiveResult)

        println("[$TAG] ===== END '$name' =====")
    }

    private fun printResult(result: OCRRunResult) {
        println("[$TAG]   lineCount=${result.lineCount} total=${result.totalTimeMs}ms " +
            "det=${result.detectionTimeMs}ms rec=${result.recognitionTimeMs}ms " +
            "detInput=${result.detInputShape}")
        if (result.results.isEmpty()) {
            println("[$TAG]   (no boxes at all — detection missed the region)")
            return
        }
        result.results.forEachIndexed { index, item ->
            val xs = item.box.points.map { it.x }
            val ys = item.box.points.map { it.y }
            val left = xs.minOrNull() ?: 0f
            val top = ys.minOrNull() ?: 0f
            val right = xs.maxOrNull() ?: 0f
            val bottom = ys.maxOrNull() ?: 0f
            val w = (right - left).toInt()
            val h = (bottom - top).toInt()
            val escaped = item.text.map { ch ->
                if (ch.code in 0x20..0x7E) ch.toString() else "\\u%04X".format(ch.code)
            }.joinToString("")
            println("[$TAG]   [$index] box=(${left.toInt()},${top.toInt()},${w}x$h) " +
                "score=%.3f text=\"%s\" (len=%d)".format(item.confidence, escaped, item.text.length))
        }
    }

    private fun renderTextLine(text: String, textSizePx: Float, padding: Int): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = textSizePx
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val metrics = paint.fontMetrics
        val textWidth = paint.measureText(text).toInt().coerceAtLeast(1)
        val textHeight = (metrics.descent - metrics.ascent).toInt().coerceAtLeast(1)

        val width = textWidth + padding * 2
        val height = textHeight + padding * 2

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.drawText(text, padding.toFloat(), padding - metrics.ascent, paint)
        return bitmap
    }

    private fun savePng(name: String, bitmap: Bitmap) {
        val file = File(outputDir, "probe_$name.png")
        runCatching {
            FileOutputStream(file).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                stream.flush()
            }
            println("[$TAG] saved ${file.absolutePath}")
        }.onFailure { error ->
            Log.w(TAG, "failed to save probe image: $name", error)
        }
    }
}
