package com.donian.myaitutor.ocr

import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max

/**
 * OCR이 돌려준 원본 텍스트 후보.
 *
 * 좌표는 이미지 픽셀 기준이며, 좌표를 얻지 못한 경우 [hasGeometry]가 false가 됩니다.
 * Android 프레임워크 타입을 쓰지 않아 JVM 단위 테스트에서 그대로 검증할 수 있습니다.
 */
data class OcrTextCandidate(
    val text: String,
    val confidence: Float?,
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
    val hasGeometry: Boolean = false
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * PaddleOCR 결과를 Gemma 프롬프트에 넣기 전에 정리합니다.
 *
 * - 전각/반각 및 유니코드 정규화(NFKC)
 * - 공백·제어문자 정리
 * - 숫자 문맥에서만 혼동 문자 보정 (O→0, l/I→1)
 * - 기호만 있거나 신뢰도가 매우 낮은 조각 제거
 * - 겹치는 중복 블록 제거
 * - 좌표 기반 읽기 순서 정렬
 *
 * PaddleOCR SDK는 수정하지 않으며, SDK가 반환한 값만 다룹니다.
 */
object OcrTextPostProcessor {
    /** 이 값보다 낮은 신뢰도의 한 글자 조각은 노이즈로 간주합니다. */
    private const val MIN_CONFIDENCE_FOR_SHORT_TEXT = 0.30f

    /** 짧다고 판단하는 글자 수(공백 제외). */
    private const val SHORT_TEXT_LENGTH = 1

    /** 이 문자들만으로 이루어진 블록은 글자 정보가 없다고 보고 버립니다. */
    private const val PUNCTUATION_ONLY_CHARS = "-–—_=~^*•·.,:;'\"|/\\()[]{}<>!?+×÷"

    /** 중복으로 판단하는 박스 겹침 비율(IoU). */
    private const val DUPLICATE_IOU = 0.5f

    /** 같은 행으로 묶는 세로 중심 거리 비율. */
    private const val SAME_ROW_CENTER_RATIO = 0.6

    private val ZERO_WIDTH = setOf('\u200B', '\u200C', '\u200D', '\uFEFF')

    /** 숫자와 혼동되기 쉬운 OCR 문자 집합. */
    private const val NUMERIC_CONFUSABLE = "OoIl|"

    fun process(candidates: List<OcrTextCandidate>): List<OcrTextCandidate> {
        if (candidates.isEmpty()) return emptyList()

        val cleaned = candidates.mapNotNull { candidate ->
            val text = normalizeText(candidate.text)
            when {
                text.isEmpty() -> null
                isNoise(text, candidate.confidence) -> null
                text != candidate.text -> candidate.copy(text = text)
                else -> candidate
            }
        }
        return orderByGeometry(removeDuplicates(cleaned))
    }

    /**
     * 전각 문자를 반각으로 바꾸고, 공백/제어문자를 정리한 뒤 숫자 혼동 문자를 보정합니다.
     */
    fun normalizeText(raw: String): String {
        if (raw.isEmpty()) return raw

        val normalized = Normalizer.normalize(raw, Normalizer.Form.NFKC)
        val builder = StringBuilder(normalized.length)
        var previousWasSpace = false
        for (char in normalized) {
            val mapped = when {
                char in ZERO_WIDTH -> continue
                char == '\u00A0' || char == '\u3000' || char.isWhitespace() -> ' '
                char.code < 0x20 -> ' '
                char == '\u2212' || char == '\u2013' || char == '\u2014' -> '-'
                else -> char
            }
            if (mapped == ' ') {
                if (!previousWasSpace) builder.append(' ')
                previousWasSpace = true
            } else {
                builder.append(mapped)
                previousWasSpace = false
            }
        }
        return fixNumericConfusions(builder.toString().trim())
    }

    /**
     * 숫자가 섞인 토큰에서만 O→0, o→0, l/I/|→1 보정을 적용합니다.
     *
     * "2O25" → "2025"처럼 숫자가 우세한 경우만 고치고,
     * "CO2", "H2O", "1O"처럼 화학식·일반 표기일 수 있는 경우는 건드리지 않습니다.
     */
    fun fixNumericConfusions(text: String): String {
        if (text.isEmpty() || text.none { it in NUMERIC_CONFUSABLE }) return text

        val builder = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (char.isDigit() || char in NUMERIC_CONFUSABLE || char == '.' || char == ',') {
                var end = index
                while (end < text.length && (text[end].isDigit() || text[end] in NUMERIC_CONFUSABLE || text[end] == '.' || text[end] == ',')) {
                    end++
                }
                val token = text.substring(index, end)
                if (shouldFixToken(token)) {
                    builder.append(token.map(::mapConfusable).joinToString(""))
                } else {
                    builder.append(token)
                }
                index = end
            } else {
                builder.append(char)
                index++
            }
        }
        return builder.toString()
    }

    /**
     * 글자/숫자가 전혀 없고 문장부호만 있는 조각, 또는 신뢰도가 매우 낮은 한 글자 조각을
     * 노이즈로 판단합니다. ①②③ 같은 원문자 기호는 선택지 번호일 수 있어 유지합니다.
     */
    fun isNoise(text: String, confidence: Float?): Boolean {
        if (text.all { it.isWhitespace() || it in PUNCTUATION_ONLY_CHARS }) return true

        val meaningful = text.count { it.isLetterOrDigit() }
        if (meaningful == 0) return false
        return confidence != null &&
            confidence < MIN_CONFIDENCE_FOR_SHORT_TEXT &&
            meaningful <= SHORT_TEXT_LENGTH
    }

    private fun shouldFixToken(token: String): Boolean {
        val confusableCount = token.count { it in NUMERIC_CONFUSABLE }
        val digitCount = token.count { it.isDigit() }
        // 숫자가 2개 이상이고, 혼동 문자보다 숫자가 많을 때만 보정합니다.
        return confusableCount > 0 && digitCount >= 2 && digitCount > confusableCount
    }

    private fun mapConfusable(char: Char): Char = when (char) {
        'O', 'o' -> '0'
        'I', 'l', '|' -> '1'
        else -> char
    }

    /**
     * 텍스트가 같고 박스가 크게 겹치는 블록을 하나로 합칩니다. 신뢰도가 높은 쪽을 남깁니다.
     */
    private fun removeDuplicates(candidates: List<OcrTextCandidate>): List<OcrTextCandidate> {
        if (candidates.size <= 1) return candidates
        val kept = mutableListOf<OcrTextCandidate>()
        for (candidate in candidates) {
            val duplicateIndex = kept.indexOfFirst { existing ->
                existing.hasGeometry &&
                    candidate.hasGeometry &&
                    existing.text == candidate.text &&
                    intersectionOverUnion(existing, candidate) >= DUPLICATE_IOU
            }
            if (duplicateIndex < 0) {
                kept.add(candidate)
            } else if ((candidate.confidence ?: 0f) > (kept[duplicateIndex].confidence ?: 0f)) {
                kept[duplicateIndex] = candidate
            }
        }
        return kept
    }

    private fun intersectionOverUnion(a: OcrTextCandidate, b: OcrTextCandidate): Float {
        val left = max(a.left, b.left)
        val top = max(a.top, b.top)
        val right = minOf(a.right, b.right)
        val bottom = minOf(a.bottom, b.bottom)
        val intersectionWidth = right - left
        val intersectionHeight = bottom - top
        if (intersectionWidth <= 0 || intersectionHeight <= 0) return 0f

        val intersection = intersectionWidth.toLong() * intersectionHeight
        val union = a.width.toLong() * a.height + b.width.toLong() * b.height - intersection
        if (union <= 0L) return 0f
        return intersection.toFloat() / union
    }

    /**
     * 좌표가 있으면 행 단위로 묶어 위→아래, 같은 행에서는 왼쪽→오른쪽으로 정렬합니다.
     * 좌표가 하나라도 없으면 SDK가 준 순서를 그대로 유지합니다.
     */
    private fun orderByGeometry(candidates: List<OcrTextCandidate>): List<OcrTextCandidate> {
        if (candidates.size <= 1) return candidates
        if (candidates.any { !it.hasGeometry }) return candidates

        val sorted = candidates.sortedWith(
            compareBy({ it.top + it.bottom }, { it.left })
        )
        val rows = mutableListOf<MutableList<OcrTextCandidate>>()
        for (candidate in sorted) {
            val row = rows.lastOrNull()
            if (row != null && isSameRow(row, candidate)) {
                row.add(candidate)
            } else {
                rows.add(mutableListOf(candidate))
            }
        }
        return rows.flatMap { row -> row.sortedBy { it.left } }
    }

    private fun isSameRow(row: List<OcrTextCandidate>, candidate: OcrTextCandidate): Boolean {
        val rowCenter = row.sumOf { (it.top + it.bottom).toDouble() } / (2.0 * row.size)
        val candidateCenter = (candidate.top + candidate.bottom) / 2.0
        val rowHeight = row.maxOf { it.height }
        val threshold = max(8.0, SAME_ROW_CENTER_RATIO * max(rowHeight, candidate.height))
        return abs(candidateCenter - rowCenter) <= threshold
    }
}
