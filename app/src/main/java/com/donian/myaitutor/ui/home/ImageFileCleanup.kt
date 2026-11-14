package com.donian.myaitutor.ui.home

import java.io.File

/**
 * 이미지 분석 후 임시 파일을 정리합니다.
 *
 * 원본 촬영 이미지는 재분석을 위해 보존하고, [preserved]가 가리키는 파일
 *("OCR 전달 이미지"로 노출된 전처리 결과)도 보존합니다.
 * [deleteQuietly]를 사용해 파일이 없거나 삭제에 실패해도 예외를 던지지 않습니다.
 */
internal fun deleteTempAnalysisImage(temp: File?, original: File, preserved: File?) {
    if ((temp != null && temp != original && temp != preserved)) {
        temp.deleteQuietly()
    }
}
