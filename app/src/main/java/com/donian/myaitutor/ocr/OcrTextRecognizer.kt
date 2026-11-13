package com.donian.myaitutor.ocr

import java.io.File

/**
 * OCR 인식 결과를 구조화된 형태로 반환합니다.
 * 원본 이미지에서 인식된 텍스트 블록을 포함하여,
 * 위치, 유형, 신뢰도, 읽기 순서 등을 보존합니다.
 */
interface OcrTextRecognizer {
    /**
    * 인식된 텍스트 블록 목록을 반환합니다.
    * 각 블록은 텍스트, 유형, 좌표, 신뢰도, 소스 파일 경로를 포함합니다.
    *
    * @return OcrBlock 목록
    */
    suspend fun recognize(imageFile: File): List<OcrBlock>
}