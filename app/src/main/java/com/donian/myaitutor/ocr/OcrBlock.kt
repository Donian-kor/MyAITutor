package com.donian.myaitutor.ocr

import android.graphics.Rect

/**
 * OCR 결과의 구조화된 표현.
 * 원본 이미지에서 인식된 텍스트 블록을 포함하여,
 * 위치, 유형, 신뢰도, 읽기 순서 등을 보존합니다.
 */
data class OcrBlock(
    val text: String,
    val type: OcrBlockType,
    val bounds: Rect?,
    val confidence: Float?,
    val readingOrder: Int?,
    val sourceFile: String
) {
    enum class OcrBlockType {
        TEXT,
        FORMULA,
        TABLE,
        IMAGE,
        UNKNOWN
    }
}