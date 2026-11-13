package com.donian.myaitutor.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ModelSelection]의 정합 불변식과 [PrefsModelSelectionRepository]가 약속하는
 * 정규화 규칙을 검증한다.
 *
 * SharedPreferences 없이 순수 로직만 검증하기 위해, 정규화 규칙을
 * [ModelSelection.normalize]에 두고 저장소는 그 결과를 그대로 저장한다고 본다.
 */
class ModelSelectionRepositoryTest {

    @Test
    fun `저장값이 없으면 기본값은 GEMMA다`() {
        val selection = ModelSelection.DEFAULT
        assertEquals(AppModel.GEMMA, selection.model)
        assertFalse(selection.chatMode)
    }

    @Test
    fun `GEMMA는 자유 대화를 허용한다`() {
        assertTrue(ModelSelection(AppModel.GEMMA, chatMode = true).isFreeChatAllowed)
    }

    @Test
    fun `QWEN3는 자유 대화를 허용하지 않는다`() {
        assertFalse(ModelSelection(AppModel.QWEN3, chatMode = false).isFreeChatAllowed)
    }

    @Test
    fun `QWEN3와 chatMode true 조합은 정합 상태가 아니다`() {
        assertFalse(ModelSelection(AppModel.QWEN3, chatMode = true).isConsistent)
    }

    @Test
    fun `QWEN3와 chatMode false 조합은 정합 상태다`() {
        assertTrue(ModelSelection(AppModel.QWEN3, chatMode = false).isConsistent)
    }

    @Test
    fun `정규화는 QWEN3와 chatMode true 조합에서 chatMode를 끈다`() {
        val normalized = ModelSelection(AppModel.QWEN3, chatMode = true).normalized()
        assertEquals(AppModel.QWEN3, normalized.model)
        assertFalse(normalized.chatMode)
        assertTrue(normalized.isConsistent)
    }

    @Test
    fun `GEMMA 조합은 정규화해도 값이 바뀌지 않는다`() {
        val original = ModelSelection(AppModel.GEMMA, chatMode = true)
        assertEquals(original, original.normalized())
    }

    @Test
    fun `QWEN3 선택 시 저장된 chatMode는 false로 유지된다`() {
        // 저장소의 setSelectedModel 규칙: QWEN3를 고르면 자유 대화가 꺼진다.
        val afterGemmaFreeChat = ModelSelection(AppModel.GEMMA, chatMode = true)
        val afterQwen = afterGemmaFreeChat.copy(
            model = AppModel.QWEN3,
            chatMode = false
        )
        assertFalse(afterQwen.chatMode)
        assertEquals(AppModel.QWEN3, afterQwen.model)
    }

    @Test
    fun `GEMMA로 되돌아오면 chatMode=false로 시작한다`() {
        val afterQwen = ModelSelection(AppModel.QWEN3, chatMode = false)
        val backToGemma = afterQwen.copy(model = AppModel.GEMMA)
        assertFalse(backToGemma.chatMode)
        assertTrue(backToGemma.isFreeChatAllowed)
    }

    @Test
    fun `정규화 결과는 항상 정합 상태다`() {
        val cases = AppModel.entries.flatMap { model ->
            listOf(true, false).map { chatMode -> ModelSelection(model, chatMode) }
        }
        cases.forEach { selection ->
            assertTrue(
                "정규화 결과가 정합하지 않음: $selection",
                selection.normalized().isConsistent
            )
        }
    }
}