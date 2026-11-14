package com.paddle.ocr.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [YamlUtils]의 들여쓰기 계산을 검증한다.
 * inference.yml 파싱은 이 값으로 YAML 중첩 구조(PostProcess / character_dict)를 판별하므로
 * 공백·탭·빈 줄 처리가 중요하다.
 */
class YamlUtilsTest {

    @Test
    fun `공백 개수를 그대로 센다`() {
        assertEquals(0, YamlUtils.leadingSpaces("key: value"))
        assertEquals(1, YamlUtils.leadingSpaces(" key"))
        assertEquals(4, YamlUtils.leadingSpaces("    - a"))
    }

    @Test
    fun `모두 공백이면 문자열 길이를 돌려준다`() {
        assertEquals(0, YamlUtils.leadingSpaces(""))
        assertEquals(6, YamlUtils.leadingSpaces("      "))
    }

    @Test
    fun `탭은 들여쓰기로 세지 않는다`() {
        assertEquals(0, YamlUtils.leadingSpaces("\t- a"))
        assertEquals(1, YamlUtils.leadingSpaces(" \t- a"))
    }

    @Test
    fun `개행 문자 뒤의 내용은 공백이 아니므로 0이다`() {
        assertEquals(0, YamlUtils.leadingSpaces("\n  - a"))
    }

    @Test
    fun `YAML 목록 항목의 들여쓰기를 구분한다`() {
        val lines = """
            PostProcess:
              name: DBPostProcess
              character_dict:
                - a
                - b
        """.trimIndent().lines()

        assertEquals(listOf(0, 2, 2, 4, 4), lines.map { YamlUtils.leadingSpaces(it) })
    }
}