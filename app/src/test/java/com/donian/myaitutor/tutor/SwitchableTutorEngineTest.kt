package com.donian.myaitutor.tutor

import com.donian.myaitutor.ai.Assistant
import com.donian.myaitutor.ai.AssistantRequest
import com.donian.myaitutor.ai.AssistantStreamEvent
import com.donian.myaitutor.ai.SystemInstruction
import com.donian.myaitutor.ai.TutorGrade
import com.donian.myaitutor.model.AppModel
import com.donian.myaitutor.model.EngineRuntime
import com.donian.myaitutor.model.ModelSelection
import com.donian.myaitutor.model.ModelSelectionRepository
import com.donian.myaitutor.model.ModelRuntimeState
import com.donian.myaitutor.model.RuntimeStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SwitchableTutorEngine]이 현재 활성 모델에 요청을 위임하는지 검증한다.
 *
 * 실제 Engine은 생성하지 않고 [EngineRuntime] 인터페이스를 구현하는 대역을 주입해
 * 상태 머신과 위임 규칙만 검증한다.
 */
class SwitchableTutorEngineTest {

    /** 활성 엔진 없이 상태만 관리하는 조정자 대역. */
    private class StubCoordinator(
        private val engine: LlmInferenceEngine?,
        private val active: Boolean = true
    ) : EngineRuntime {
        var ensureActiveCalls = 0
        var releaseActiveCalls = 0

        private val _status = MutableStateFlow(
            if (active && engine != null) {
                RuntimeStatus(
                    state = ModelRuntimeState.ACTIVE,
                    activeEngineCount = 1,
                    activeModel = AppModel.GEMMA
                )
            } else {
                RuntimeStatus(
                    state = ModelRuntimeState.IDLE,
                    activeEngineCount = 0,
                    activeModel = AppModel.GEMMA
                )
            }
        )

        override val status: MutableStateFlow<RuntimeStatus> = _status

        override suspend fun ensureActive(): LlmInferenceEngine {
            ensureActiveCalls++
            return engine ?: throw IllegalStateException("엔진 없음")
        }

        override fun getActiveEngine(): LlmInferenceEngine? = engine

        override suspend fun releaseActive() {
            releaseActiveCalls++
        }

        override suspend fun switchTo(target: AppModel): Result<LlmInferenceEngine> =
            Result.failure(UnsupportedOperationException("테스트용"))
    }

    /** 위임 호출을 기록하는 최소 어댑터. */
    private class RecordingEngine : LlmInferenceEngine {
        val events = mutableListOf<String>()
        var lastSystemInstruction: String? = null

        override fun stream(request: TutorRequest): Flow<TutorStreamChunk> = emptyFlow()

        override fun cancelGeneration() {
            events += "cancel"
        }

        override suspend fun awaitGenerationStopped(timeoutMillis: Long): Boolean {
            events += "awaitStopped"
            return true
        }

        override suspend fun resetConversation(systemInstruction: String) {
            events += "reset"
            lastSystemInstruction = systemInstruction
        }

        override suspend fun backendLabel(): String = "FAKE"

        override suspend fun getTokenCount(): Int = 0

        override suspend fun preWarm() {
            events += "preWarm"
        }

        override suspend fun release() {
            events += "release"
        }
    }

    @Test
    fun `엔진이 없으면 취소는 예외 없이 무시된다`() {
        val coordinator = StubCoordinator(engine = null)
        val switchable = SwitchableTutorEngine(coordinator)

        switchable.cancelGeneration()

        // 예외가 발생하지 않아야 한다.
        assertTrue(true)
    }

    @Test
    fun `활성 엔진이 없으면 awaitGenerationStopped는 true를 반환한다`() = runTest {
        val coordinator = StubCoordinator(engine = null)
        val switchable = SwitchableTutorEngine(coordinator)

        assertTrue(switchable.awaitGenerationStopped(1_000L))
    }

    @Test
    fun `활성 엔진이 없으면 backendLabel은 미실행이다`() = runTest {
        val coordinator = StubCoordinator(engine = null)
        val switchable = SwitchableTutorEngine(coordinator)

        assertEquals("미실행", switchable.backendLabel())
    }

    @Test
    fun `활성 엔진이 없으면 getTokenCount는 0이다`() = runTest {
        val coordinator = StubCoordinator(engine = null)
        val switchable = SwitchableTutorEngine(coordinator)

        assertEquals(0, switchable.getTokenCount())
    }

    @Test
    fun `비활성 상태에서 resetConversation은 엔진에 전달하지 않는다`() = runTest {
        val engine = RecordingEngine()
        val coordinator = StubCoordinator(engine = engine, active = false)
        val switchable = SwitchableTutorEngine(coordinator)

        switchable.resetConversation(SystemInstruction)

        assertFalse(engine.events.contains("reset"))
    }

    @Test
    fun `preWarm은 조정자에 위임한다`() = runTest {
        val engine = RecordingEngine()
        val coordinator = StubCoordinator(engine = engine)
        val switchable = SwitchableTutorEngine(coordinator)

        switchable.preWarm()

        assertTrue(coordinator.ensureActiveCalls > 0)
    }

    @Test
    fun `release는 조정자에 위임한다`() = runTest {
        val engine = RecordingEngine()
        val coordinator = StubCoordinator(engine = engine)
        val switchable = SwitchableTutorEngine(coordinator)

        switchable.release()

        assertTrue(coordinator.releaseActiveCalls > 0)
    }

    @Test
    fun `stream은 활성 엔진에 요청을 전달한다`() = runTest {
        val coordinator = StubCoordinator(engine = RecordingEngine())
        val switchable = SwitchableTutorEngine(coordinator)
        val request = TutorRequest(
            text = "질문",
            subject = "수학",
            grade = TutorGrade.ELEMENTARY,
            options = inferenceOptionsFor(isFollowUp = false, thinkingEnabled = false)
        )

        switchable.stream(request).toList()

        assertEquals(1, coordinator.ensureActiveCalls)
    }

    @Test
    fun `Qwen 활성 상태에서 자유 대화 요청은 QwenTutorEngine에서 차단된다`() = runTest {
        val engine = QwenTutorEngine(NoopAssistant())
        val request = TutorRequest(
            text = "안녕",
            subject = "수학",
            grade = TutorGrade.ELEMENTARY,
            options = inferenceOptionsFor(isFollowUp = false, thinkingEnabled = false),
            mode = TutorMode.FREE_CHAT
        )

        val result = runCatching { engine.stream(request).toList() }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("자유 대화") == true)
    }

    @Test
    fun `RuntimeStatus 기본값은 IDLE이다`() {
        val default = RuntimeStatus()
        assertEquals(ModelRuntimeState.IDLE, default.state)
        assertEquals(0, default.activeEngineCount)
    }

    @Test
    fun `RuntimeStatus 기본값은 GEMMA이다`() {
        val default = RuntimeStatus()
        assertEquals(AppModel.GEMMA, default.activeModel)
    }

    @Test
    fun `RuntimeStatus 기본값은 실패 사유가 null이다`() {
        val default = RuntimeStatus()
        assertEquals(null, default.failureMessage)
    }

    @Test
    fun `RuntimeStatus 기본값은 사용 불가 상태다`() {
        val default = RuntimeStatus()
        assertFalse(default.state.isUsable)
    }

    private class NoopAssistant : Assistant {
        override fun streamAnswer(request: AssistantRequest): Flow<AssistantStreamEvent> = emptyFlow()

        override suspend fun awaitGenerationStopped(timeoutMillis: Long): Boolean = true

        override suspend fun resetConversation(systemInstruction: String) = Unit

        override suspend fun backendLabel(): String = "NONE"

        override suspend fun getTokenCount(): Int = 0

        override fun cancelGeneration() = Unit

        override suspend fun preWarm() = Unit

        override suspend fun release() = Unit
    }
}