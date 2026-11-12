package com.donian.myaitutor.ocr

import java.io.File

interface OcrTextRecognizer {
    suspend fun recognize(imageFile: File): List<String>
}
