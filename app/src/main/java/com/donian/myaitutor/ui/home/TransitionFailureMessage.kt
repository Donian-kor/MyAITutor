package com.donian.myaitutor.ui.home

import com.donian.myaitutor.R
import com.donian.myaitutor.ai.CpuFallbackBlockedException

/**
 * 모델 로드 실패 문구를 고릅니다.
 *
 * CPU 폴백 차단은 준비된 안내 문구([R.string.error_cpu_fallback_blocked])로 알리고,
 * 그 외에는 예외 메시지를 그대로 사용합니다. ModelTransitionException가 원인을 cause로
 * 감싸므로 원인 체인을 따라 확인합니다.
 */
internal fun transitionFailureMessage(
    error: Throwable,
    fallbackStringRes: Int,
    stringProvider: StringProvider,
): String {
    var current: Throwable? = error
    while (current != null) {
        if (current is CpuFallbackBlockedException) {
            return stringProvider.get(R.string.error_cpu_fallback_blocked)
        }
        current = current.cause
    }
    return error.message ?: stringProvider.get(fallbackStringRes)
}
