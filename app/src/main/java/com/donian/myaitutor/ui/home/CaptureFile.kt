package com.donian.myaitutor.ui.home

import java.io.File

/** 촬영 임시 파일 이름 접두사. cacheDir 정리 대상 식별에 사용합니다. */
internal const val CaptureFilePrefix = "focus_capture_"

/** 아직 남아 있는 촬영 임시 파일 중 1시간이 지난 것만 지웁니다. 사용 중인 파일은 건드리지 않습니다. */
internal fun File.deleteStaleCaptures(now: Long = System.currentTimeMillis()) {
    runCatching {
        listFiles { file -> file.name.startsWith(CaptureFilePrefix) }?.forEach { file ->
            if (now - file.lastModified() > 60L * 60L * 1000L) file.delete()
        }
    }
}

/** 촬영 임시 파일을 지웁니다. 이미 없거나 삭제에 실패해도 예외를 던지지 않습니다. */
internal fun File.deleteQuietly() {
    runCatching { if (exists()) delete() }
}