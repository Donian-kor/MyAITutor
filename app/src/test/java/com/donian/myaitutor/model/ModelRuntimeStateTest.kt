package com.donian.myaitutor.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 런타임 상태 불변식을 검증한다.
 *
 * 핵심 규칙(계획서 4.1): `activeEngineCount <= 1`을 모든 상태 전환에서 보장하며,
 * 전환 중에는 활성 엔진이 0개여야 하고 ACTIVE 상태에서는 반드시 1개다.
 */
class ModelRuntimeStateTest {

    @Test
    fun `IDLE 상태는 활성 엔진 0개다`() {
        val status = RuntimeStatus(state = ModelRuntimeState.IDLE, activeEngineCount = 0)
        assertEquals(ModelRuntimeState.IDLE, status.state)
        assertFalse(status.state.isUsable)
    }

    @Test
    fun `ACTIVE 상태는 활성 엔진 1개다`() {
        val status = RuntimeStatus(
            state = ModelRuntimeState.ACTIVE,
            activeEngineCount = 1,
            activeModel = AppModel.GEMMA
        )
        assertTrue(status.state.isUsable)
        assertEquals(AppModel.GEMMA, status.activeModel)
    }

    @Test
    fun `TRANSITIONING 상태는 활성 엔진 0개다`() {
        val status = RuntimeStatus(state = ModelRuntimeState.TRANSITIONING, activeEngineCount = 0)
        assertFalse(status.state.isUsable)
    }

    @Test
    fun `NO_MODEL_LOADED 상태는 활성 엔진 0개다`() {
        val status = RuntimeStatus(state = ModelRuntimeState.NO_MODEL_LOADED, activeEngineCount = 0)
        assertFalse(status.state.isUsable)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `ACTIVE 상태에 활성 엔진 0개는 불변식 위반이다`() {
        RuntimeStatus(state = ModelRuntimeState.ACTIVE, activeEngineCount = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `TRANSITIONING 상태에 활성 엔진 1개는 불변식 위반이다`() {
        RuntimeStatus(state = ModelRuntimeState.TRANSITIONING, activeEngineCount = 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `activeEngineCount는 2를 넘을 수 없다`() {
        RuntimeStatus(state = ModelRuntimeState.IDLE, activeEngineCount = 2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `activeEngineCount는 음수가 될 수 없다`() {
        RuntimeStatus(state = ModelRuntimeState.IDLE, activeEngineCount = -1)
    }

    @Test
    fun `전환 실패 사유는 기본적으로 null이다`() {
        assertEquals(null, RuntimeStatus().failureMessage)
    }

    @Test
    fun `전환 실패 사유를 보존한다`() {
        val status = RuntimeStatus(
            state = ModelRuntimeState.NO_MODEL_LOADED,
            activeEngineCount = 0,
            failureMessage = "메모리 부족"
        )
        assertEquals("메모리 부족", status.failureMessage)
    }

    @Test
    fun `사용 가능한 상태는 ACTIVE뿐이다`() {
        val usableStates = ModelRuntimeState.entries.filter { it.isUsable }
        assertEquals(listOf(ModelRuntimeState.ACTIVE), usableStates)
    }
}