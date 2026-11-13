package com.donian.myaitutor.ai

import org.junit.Assert.assertTrue
import org.junit.Test

class Qwen3ChatTemplateTest {
    @Test
    fun `string content renders question`() {
        val messages = listOf(mapOf("role" to "user", "content" to "이 문제를 단계별로 설명해줘"))
        val rendered = Qwen3ChatTemplate.render(messages)
        assertTrue(rendered.contains("이 문제를 단계별로 설명해줘"))
    }

    @Test
    fun `typed parts render question`() {
        val messages = listOf(mapOf("role" to "user", "content" to listOf(mapOf("type" to "text", "text" to "이 문제를 단계별로 설명해줘"))))
        val rendered = Qwen3ChatTemplate.render(messages)
        assertTrue(rendered.contains("이 문제를 단계별로 설명해줘"))
    }
}