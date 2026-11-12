package com.donian.myaitutor.model

import java.io.File

/**
 * 모델 다운로드/상태 조회를 추상화한 인터페이스.
 * 테스트에서는 가짜 구현을 주입할 수 있습니다.
 */
interface ModelDownloadRepository {
    val modelFile: File
    fun snapshot(): ModelDownloadSnapshot
    fun startDownload(): Long
    fun deleteModel()
    fun isModelReady(): Boolean
    fun needsHashVerification(): Boolean
    suspend fun verifyModelHash(): Boolean
}
