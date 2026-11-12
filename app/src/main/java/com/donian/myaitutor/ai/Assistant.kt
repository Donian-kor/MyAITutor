package com.donian.myaitutor.ai

import java.io.File

/**
 * Gemma 런타임과의 상호작용을 추상화한 인터페이스.
 * 테스트에서는 가짜 구현을 주입할 수 있습니다.
 */
interface Assistant {
    suspend fun analyzeImage(imageFile: File, subject: String, onTextChanged: suspend (String) -> Unit)
    suspend fun askFollowUp(question: String, onTextChanged: suspend (String) -> Unit)
    suspend fun backendLabel(): String
    fun cancelGeneration()
    suspend fun preWarm()
    suspend fun release()
}
