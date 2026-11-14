package com.donian.myaitutor.ai

import android.util.Log

/**
 * LiteRT-LM SDK 예외를 사용자 안내 문구로 변환합니다.
 *
 * SDK는 gRPC/Abseil 스타일의 영어 메시지("status code 9", "FAILED_PRECONDITION" 등)를
 * 그대로 던지므로 UI에 노출하면 사용자에게 의미를 알 수 없는 영문이 보인다.
 * 여기서 원인을 분류해 한국어 안내로 바꾸고, 원본 메시지는 진단 로그로만 남긴다.
 */
internal object LiteRtErrorMapper {

    private const val TAG = "LiteRtError"

    /**
     * 오류를 사용자 안내 문구로 변환한다.
     *
     * @param raw SDK가 던진 원본 메시지. 진단 로그에만 사용하고 UI에는 노출하지 않는다.
     */
    fun toUserMessage(raw: String?): String {
        Log.i(TAG, "SDK 오류 원본: ${raw.orEmpty()}")
        val text = raw.orEmpty()
        return when {
            // StatusCode 9 = FAILED_PRECONDITION
            text.contains("status code 9", ignoreCase = true) ||
                text.contains("FAILED_PRECONDITION", ignoreCase = true) ->
                "모델 실행 조건을 충족하지 못했습니다. 모델을 다시 다운로드하거나 앱을 재시작해 주세요."

            // 컨텍스트/토큰 예산 초과
            text.contains("context", ignoreCase = true) &&
                (text.contains("exceed", ignoreCase = true) ||
                    text.contains("overflow", ignoreCase = true) ||
                    text.contains("too long", ignoreCase = true)) ->
                "대화 내용이 모델이 처리할 수 있는 길이를 넘었습니다. 새 문제로 다시 시도해 주세요."

            // 메모리 부족
            text.contains("out of memory", ignoreCase = true) ||
                text.contains("OOM", ignoreCase = true) ||
                text.contains("allocation", ignoreCase = true) ->
                "메모리가 부족해 AI를 실행할 수 없습니다. 다른 앱을 종료한 뒤 다시 시도해 주세요."

            // 모델 파일 손상/없음
            text.contains("model", ignoreCase = true) &&
                (text.contains("not found", ignoreCase = true) ||
                    text.contains("failed to load", ignoreCase = true) ||
                    text.contains("corrupt", ignoreCase = true)) ->
                "AI 모델 파일을 불러오지 못했습니다. 설정에서 모델을 다시 다운로드해 주세요."

            // 그 외
            else -> "AI 추론 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."
        }
    }
}