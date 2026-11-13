package com.donian.myaitutor.ui.home

/**
 * 채팅 메시지 역할 구분.
 */
enum class ChatRole {
    /** AI 선생님 답변 */
    TEACHER,
    /** 학생 질문 */
    STUDENT
}

/**
 * 채팅 메시지 데이터 모델.
 * AI 답변과 학생 질문을 개별 버블로 분리하여 표시합니다.
 */
data class ChatMessage(
    val role: ChatRole,
    val text: String
)