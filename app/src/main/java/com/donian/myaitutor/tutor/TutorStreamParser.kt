package com.donian.myaitutor.tutor

/**
 * 튜터 스트림의 진행 단계. 답변 텍스트 자체는 담지 않습니다.
 */
enum class TutorProgressStage {
    IDLE,
    OCR,
    PREPARING,
    ANALYZING,
    EXPLAINING,
    COMPLETED,
    CANCELLED,
    ERROR
}

/**
 * 튜터 UI 진행 상태. 실제 해설 텍스트의 단일 원본은
 * [com.donian.myaitutor.ui.home.HomeUiState.answer]이며 여기에는 중복 저장하지 않습니다.
 */
data class TutorUiState(
    val isGenerating: Boolean = false,
    val isCancelling: Boolean = false,
    val currentStatus: String = "",
    val progressStage: TutorProgressStage = TutorProgressStage.IDLE,
    val progressMessages: List<String> = emptyList(),
    val isCompleted: Boolean = false,
    val errorMessage: String? = null,
    val isCancelled: Boolean = false,
    /** 취소 완료를 확인하지 못해 새 추론을 시작하면 안 되는 상태. */
    val requiresRecovery: Boolean = false
) {
    val isTerminal: Boolean
        get() = isCompleted || isCancelled || errorMessage != null
}

/** 엔진이 전달하는 SDK 비종속 스트림 이벤트. */
sealed interface TutorStreamChunk {
    /** Thought 채널이 활동했다는 사실만 전달합니다. 원문은 포함하지 않습니다. */
    data object ThoughtActivity : TutorStreamChunk

    /** 해설 답변의 조각. 전체 누적 문자열이 아니라 증가분입니다. */
    data class Answer(val text: String) : TutorStreamChunk

    data object Completed : TutorStreamChunk
    data object Cancelled : TutorStreamChunk
    data class Error(val message: String) : TutorStreamChunk
}

/** 파서가 돌려주는 결과. [answerTextDelta]만 HomeUiState.answer에 이어 붙입니다. */
data class TutorParseResult(
    val state: TutorUiState,
    val answerTextDelta: String? = null
)

/** 파서가 사용하는 고정 안내 문구. 순수 JVM 계층이므로 리소스에 의존하지 않습니다. */
data class TutorStatusStrings(
    val readingPhoto: String = "선생님이 사진에서 문제를 읽고 있어요.",
    val lookingAtProblem: String = "선생님이 문제를 꼼꼼히 살펴보고 있어요.",
    val explaining: String = "선생님이 풀이를 작성하고 있어요.",
    val cancelling: String = "풀이를 안전하게 중단하고 있어요…",
    val completed: String = "✓ 풀이 완성 (눌러서 풀이 진행 내역 보기)",
    val cancelled: String = "풀이가 중단되었습니다.",
    val error: String = "풀이 중 오류가 발생했습니다.",
    val cancellationUnconfirmed: String =
        "AI 추론이 완전히 멈추었는지 확인하지 못했습니다. 새 대화로 복구한 뒤 다시 시도해 주세요."
)

/**
 * SDK의 Message/channels를 직접 다루지 않고, 이미 분리된 이벤트만 소비하는 순수 파서.
 *
 * - Thought 이벤트는 원문 없이 진행 상태만 갱신합니다.
 * - Answer 조각은 특수 토큰 누출을 방어한 뒤 증가분으로 돌려줍니다.
 * - 완료/취소/오류를 서로 분리해 상태를 전이합니다.
 */
class TutorStreamParser(
    private val strings: TutorStatusStrings = TutorStatusStrings()
) {
    private var state = TutorUiState()
    private var filter = SpecialTokenFilter()
    private var thoughtProgressRecorded = false
    private var explainingProgressRecorded = false

    fun state(): TutorUiState = state

    fun reset() {
        state = TutorUiState()
        filter = SpecialTokenFilter()
        thoughtProgressRecorded = false
        explainingProgressRecorded = false
    }

    /** 사진에서 OCR을 시작하는 단계. */
    fun startOcr(): TutorUiState {
        state = TutorUiState(
            isGenerating = true,
            currentStatus = strings.readingPhoto,
            progressStage = TutorProgressStage.OCR
        )
        addProgress("문제 사진 인식 시작")
        return state
    }

    /** 후속 질문처럼 OCR 없이 바로 추론을 시작하는 단계. */
    fun startFollowUp(): TutorUiState {
        state = TutorUiState(
            isGenerating = true,
            currentStatus = strings.lookingAtProblem,
            progressStage = TutorProgressStage.PREPARING
        )
        addProgress("AI 분석 시작")
        return state
    }

    /** OCR이 끝나고 Gemma 추론 준비가 된 상태. */
    fun onOcrCompleted(): TutorUiState {
        state = state.copy(
            isGenerating = true,
            currentStatus = strings.lookingAtProblem,
            progressStage = TutorProgressStage.ANALYZING
        )
        addProgress("OCR 문제 인식 완료")
        return state
    }

    /** Gemma 요청을 실제로 보내는 시점. */
    fun onAnalysisStarted(): TutorUiState {
        state = state.copy(
            isGenerating = true,
            currentStatus = strings.lookingAtProblem,
            progressStage = TutorProgressStage.ANALYZING
        )
        addProgress("AI 분석 시작")
        return state
    }

    fun onChunk(chunk: TutorStreamChunk): TutorParseResult {
        var delta: String? = null
        when (chunk) {
            TutorStreamChunk.ThoughtActivity -> {
                state = state.copy(
                    isGenerating = true,
                    currentStatus = strings.lookingAtProblem,
                    progressStage = TutorProgressStage.ANALYZING
                )
                if (!thoughtProgressRecorded) {
                    thoughtProgressRecorded = true
                    addProgress("AI가 문제를 분석하는 중")
                }
            }

            is TutorStreamChunk.Answer -> {
                val cleaned = filter.feed(chunk.text)
                if (cleaned.isNotEmpty()) {
                    if (state.progressStage != TutorProgressStage.EXPLAINING) {
                        state = state.copy(
                            isGenerating = true,
                            currentStatus = strings.explaining,
                            progressStage = TutorProgressStage.EXPLAINING
                        )
                    }
                    if (!explainingProgressRecorded) {
                        explainingProgressRecorded = true
                        addProgress("해설 생성 시작")
                    }
                    delta = cleaned
                }
            }

            TutorStreamChunk.Completed -> {
                val cleaned = filter.flush()
                if (cleaned.isNotEmpty()) delta = cleaned
                state = state.copy(
                    isGenerating = false,
                    isCancelling = false,
                    currentStatus = strings.completed,
                    progressStage = TutorProgressStage.COMPLETED,
                    isCompleted = true,
                    isCancelled = false,
                    errorMessage = null
                )
                addProgress("해설 생성 완료")
            }

            TutorStreamChunk.Cancelled -> {
                // 취소된 답변의 미완성 조각은 화면에 이어 붙이지 않습니다.
                filter.flush()
                state = state.copy(
                    isGenerating = false,
                    isCancelling = false,
                    currentStatus = strings.cancelled,
                    progressStage = TutorProgressStage.CANCELLED,
                    isCompleted = false,
                    isCancelled = true
                )
                addProgress("사용자가 풀이를 중단함")
            }

            is TutorStreamChunk.Error -> {
                filter.flush()
                state = state.copy(
                    isGenerating = false,
                    isCancelling = false,
                    currentStatus = strings.error,
                    progressStage = TutorProgressStage.ERROR,
                    isCompleted = false,
                    errorMessage = chunk.message
                )
                addProgress("풀이 중 오류 발생")
            }
        }
        return TutorParseResult(state = state, answerTextDelta = delta)
    }

    /** 사용자가 중단 버튼을 누른 직후. 네이티브 종료 확인 전까지 생성 중으로 둡니다. */
    fun onCancellationRequested(): TutorUiState {
        if (!state.isGenerating) return state
        state = state.copy(isCancelling = true, currentStatus = strings.cancelling)
        return state
    }

    /** 네이티브 취소 완료가 확인된 뒤. */
    fun onCancellationConfirmed(): TutorUiState {
        state = state.copy(
            isGenerating = false,
            isCancelling = false,
            currentStatus = strings.cancelled,
            progressStage = TutorProgressStage.CANCELLED,
            isCompleted = false,
            isCancelled = true
        )
        addProgress("사용자가 풀이를 중단함")
        return state
    }

    /** 제한 시간 안에 네이티브 종료를 확인하지 못한 안전 정지 상태. */
    fun onCancellationUnconfirmed(): TutorUiState {
        state = state.copy(
            isGenerating = false,
            isCancelling = false,
            currentStatus = strings.cancellationUnconfirmed,
            progressStage = TutorProgressStage.ERROR,
            errorMessage = strings.cancellationUnconfirmed,
            requiresRecovery = true
        )
        addProgress("취소 완료 확인 실패")
        return state
    }

    /** 스트림이 완료 이벤트 없이 끝난 경우의 방어 처리. */
    fun onStreamEnded(): TutorParseResult {
        if (state.isTerminal) return TutorParseResult(state)
        val cleaned = filter.flush()
        state = state.copy(
            isGenerating = false,
            isCancelling = false,
            currentStatus = strings.completed,
            progressStage = TutorProgressStage.COMPLETED,
            isCompleted = true,
            isCancelled = false,
            errorMessage = null
        )
        addProgress("해설 생성 완료")
        return TutorParseResult(
            state = state,
            answerTextDelta = cleaned.ifEmpty { null }
        )
    }

    private fun addProgress(message: String) {
        if (state.progressMessages.lastOrNull() == message) return
        state = state.copy(progressMessages = state.progressMessages + message)
    }
}

/**
 * 채널 분리가 정상 작동하지 않는 예외 상황에서만 사용하는 특수 토큰 방어 필터.
 * 청크 경계에 걸쳐 토큰이 나뉘어 들어와도 완성 전에는 방출하지 않습니다.
 */
internal class SpecialTokenFilter(
    private val tokens: List<String> = DEFAULT_SPECIAL_TOKENS
) {
    private val buffer = StringBuilder()

    // 스트림 시작부 인사말 제거용 상태
    private val leadingBuffer = StringBuilder()
    private var leadingResolved = false

    fun feed(text: String): String {
        if (text.isEmpty()) return ""
        buffer.append(text)
        removeCompleteTokens()

        val hold = holdbackLength()
        val output = if (hold == 0) {
            val all = buffer.toString()
            buffer.clear()
            all
        } else {
            val out = buffer.substring(0, buffer.length - hold)
            buffer.delete(0, buffer.length - hold)
            out
        }
        return filterLeadingGreeting(output)
    }

    fun flush(): String {
        removeCompleteTokens()
        val remaining = buffer.toString()
        buffer.clear()
        // 불완전한 특수 토큰 접두사만 남았다면 노출하지 않습니다.
        val safe = if (remaining.length >= 2 && tokens.any { it.startsWith(remaining) }) "" else remaining
        return filterLeadingGreeting(safe) + resolveLeadingOnFlush()
    }

    /**
     * 스트림 시작부의 AI 인사말/자기소개를 제거합니다.
     * 인사말 여부가 확정될 때까지 선두 텍스트를 보류했다가 판정 후 방출합니다.
     */
    private fun filterLeadingGreeting(text: String): String {
        if (leadingResolved) return text
        if (text.isNotEmpty()) leadingBuffer.append(text)
        val current = leadingBuffer.toString()

        // 완전한 인사말로 시작하면 제거
        for (pattern in GREETING_PATTERNS) {
            if (current.startsWith(pattern)) {
                val rest = current.substring(pattern.length).trimStart()
                leadingBuffer.clear()
                leadingResolved = true
                return rest
            }
        }

        // 아직 인사말의 일부일 가능성이 있으면 계속 보류
        val couldBeGreeting = GREETING_PATTERNS.any { it.startsWith(current) || current.startsWith(it) }
        if (couldBeGreeting && current.length <= MAX_GREETING_LENGTH) return ""

        // 인사말이 아님 → 그대로 방출
        leadingBuffer.clear()
        leadingResolved = true
        return current
    }

    /** 스트림이 인사말 판정 전에 끝난 경우 보류분을 정리해 방출합니다. */
    private fun resolveLeadingOnFlush(): String {
        if (leadingResolved) return ""
        var result = leadingBuffer.toString()
        leadingBuffer.clear()
        leadingResolved = true
        for (pattern in GREETING_PATTERNS) {
            if (result.startsWith(pattern)) {
                result = result.substring(pattern.length).trimStart()
                break
            }
        }
        return result
    }

    private fun removeCompleteTokens() {
        var changed = true
        while (changed) {
            changed = false
            for (token in tokens) {
                var index = buffer.indexOf(token)
                while (index >= 0) {
                    buffer.delete(index, index + token.length)
                    changed = true
                    index = buffer.indexOf(token, index)
                }
            }
        }
    }

    private fun holdbackLength(): Int {
        val maxTokenLength = tokens.maxOfOrNull { it.length } ?: return 0
        val max = minOf(buffer.length, maxTokenLength - 1)
        if (max < 2) return 0
        for (length in max downTo 2) {
            val suffix = buffer.substring(buffer.length - length)
            if (tokens.any { it.length > length && it.startsWith(suffix) }) return length
        }
        return 0
    }

    private companion object {
        val DEFAULT_SPECIAL_TOKENS = listOf(
            "<start_of_thought>",
            "<end_of_thought>",
            "<|thought|>",
            "<|channel>thought",
            "<channel|>",
            "<start_of_turn>",
            "<end_of_turn>",
            "<bos>",
            "<eos>",
            "<|endoftext|>",
            "<|im_start|>",
            "<|im_end|>"
        )

        /** 스트림 시작부에서 잘라내야 할 AI 인사말/자기소개 패턴 (긴 것부터). */
        val GREETING_PATTERNS = listOf(
            "안녕하세요! 저는 MyAITutor의 과목별 문제 학습 도우미입니다.",
            "안녕하세요! 저는 MyAITutor의 과목별 문제 학습 도우미",
            "안녕하세요! 저는 MyAITutor",
            "안녕하세요! 저는",
            "안녕하세요!"
        )

        /** 인사말 판정을 위해 보류할 최대 글자 수. */
        const val MAX_GREETING_LENGTH = 80
    }
}
