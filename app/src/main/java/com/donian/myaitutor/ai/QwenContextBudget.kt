package com.donian.myaitutor.ai

import kotlin.math.ceil
import kotlin.math.max

/**
 * Qwen의 2,048 컨텍스트 예산을 지키기 위한 순수 계산 로직.
 *
 * 추론 전에 실제 토크나이저를 호출할 수 없으므로 문자 수 기반의 **보수적 추정**을 쓴다.
 * 추정치가 실제보다 크면 더 많이 잘라내는 방향이므로 예산을 넘길 가능성은 줄어든다.
 */
object QwenContextBudget {

    /** 한국어 1~2토큰과 영문 약 1/4토큰이 섞인 학습 텍스트 기준의 보수적 추정치(문자/토큰). */
    const val CHARS_PER_TOKEN: Double = 3.0

    /** 추정 여유분. 시스템 지시문 외의 변동분을 흡수한다. */
    const val SAFETY_MARGIN_TOKENS: Int = 64

    /** 축약 위치를 드러내는 표시. */
    const val OMITTED_MARKER: String = "\n…[이하 생략]"

    /** 문자열이 차지한다고 추정하는 토큰 수. 빈 문자열은 0입니다. */
    fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        return ceil(text.length / CHARS_PER_TOKEN).toInt()
    }

    /**
     * 입력 프롬프트에 쓸 수 있는 토큰 예산.
     *
     * 컨텍스트 전체에서 출력 예약분, 시스템 지시문, 안전 여유분을 뺀 값이다.
     */
    fun inputBudgetTokens(
        contextTokens: Int,
        outputTokens: Int,
        systemInstructionTokens: Int,
        safetyMarginTokens: Int = SAFETY_MARGIN_TOKENS
    ): Int = max(0, contextTokens - outputTokens - systemInstructionTokens - safetyMarginTokens)

    /**
     * [parts]를 [budgetTokens] 안에 들어가도록 앞 블록부터 채우고, 넘치는 블록은 [omittedMarker]로 자른다.
     *
     * 첫 블록조차 담을 수 없으면 [FitResult.overflow]가 true다. 호출부는 사용자에게
     * 입력을 줄여 달라고 안내해야 한다.
     */
    fun fit(
        parts: List<String>,
        budgetTokens: Int,
        omittedMarker: String = OMITTED_MARKER
    ): FitResult {
        if (parts.isEmpty()) return FitResult(emptyList(), truncated = false, overflow = false)

        var remaining = budgetTokens
        var truncated = false
        val fitted = ArrayList<String>(parts.size)

        for (part in parts) {
            val partTokens = estimateTokens(part)
            if (partTokens <= remaining) {
                fitted += part
                remaining -= partTokens
                continue
            }

            val shortened = truncateToTokens(part, remaining, omittedMarker)
            fitted += shortened
            truncated = true
            remaining = 0
            // 첫 블록조차 마커를 담을 수 없으면 예산 자체가 부족한 것이다.
            if (shortened.isEmpty()) {
                return FitResult(fitted, truncated = true, overflow = true)
            }
        }

        return FitResult(fitted, truncated = truncated, overflow = false)
    }

    /** [text]가 [maxTokens] 안에 들어오도록 앞부분만 남기고 뒤를 [omittedMarker]로 자른다. */
    fun truncateToTokens(
        text: String,
        maxTokens: Int,
        omittedMarker: String = OMITTED_MARKER
    ): String {
        if (text.isEmpty() || maxTokens <= 0) return ""
        val maxChars = (maxTokens * CHARS_PER_TOKEN).toInt()
        if (text.length <= maxChars) return text
        val keep = maxChars - omittedMarker.length
        if (keep <= 0) return ""
        return text.take(keep) + omittedMarker
    }

    /** [fit] 결과. */
    data class FitResult(
        val parts: List<String>,
        /** 하나라도 잘렸는지 여부. 진단·로그용입니다. */
        val truncated: Boolean,
        /** 예산 부족으로 첫 블록을 담지 못했는지 여부. 사용자 안내가 필요합니다. */
        val overflow: Boolean
    )
}