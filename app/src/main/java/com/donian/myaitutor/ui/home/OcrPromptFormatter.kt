package com.donian.myaitutor.ui.home

import com.donian.myaitutor.ocr.OcrBlock

/**
 * OCR 블록을 Gemma에 전달할 텍스트로 포맷팅합니다.
 * 블록의 위치, 신뢰도, 유형 정보를 활용해 구조화된 텍스트를 만듭니다.
 */
internal fun formatOcrBlocksForPrompt(blocks: List<OcrBlock>): String {
    if (blocks.isEmpty()) return ""
    return blocks.map { block ->
        when (block.type) {
            OcrBlock.OcrBlockType.FORMULA -> {
                // 수식은 별도 표시로 구분
                "[수식] ${block.text.trim()}"
            }
            OcrBlock.OcrBlockType.TABLE -> {
                // 표 구조 보존을 위해 [표] 태그 부여
                val text = block.text.trim()
                if (text.startsWith("[표]")) text else "[표]\n$text"
            }
            OcrBlock.OcrBlockType.IMAGE -> {
                // 이미지 설명은 [이미지] 태그로 표시
                "[이미지] ${block.text.trim()}"
            }
            OcrBlock.OcrBlockType.TEXT -> {
                // 일반 텍스트는 그대로
                block.text.trim()
            }
            else -> {
                // 알 수 없거나 다른 유형은 그대로
                block.text.trim()
            }
        }
    }.joinToString("\n")
}
