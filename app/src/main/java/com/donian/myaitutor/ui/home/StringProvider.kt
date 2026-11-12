package com.donian.myaitutor.ui.home

/**
 * 문자열 리소스 조회 추상화.
 * 단위 테스트에서는 Context 없이 가짜 구현을 주입할 수 있습니다.
 */
fun interface StringProvider {
    fun get(resId: Int, vararg args: Any): String
}
