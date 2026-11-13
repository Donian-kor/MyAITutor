package com.donian.myaitutor.ai

/**
 * 학년 구분. 학년 상태는 [com.donian.myaitutor.ui.home.HomeUiState]가 단일 원본으로 소유하고,
 * 프롬프트와 Thinking 설정을 만들 때만 이 값을 사용합니다.
 */
enum class TutorGrade(
    val label: String,
    internal val gradeDescription: String
) {
    KIDS("유아", "유아(5~7세)"),
    ELEMENTARY("초등", "초등학생"),
    MIDDLE("중등", "중학생"),
    HIGH("고등", "고등학생");

    /** 중등·고등만 Thought 채널을 사용하는 Thinking을 켭니다. */
    val usesThinking: Boolean
        get() = this == MIDDLE || this == HIGH
}

/** Thinking 토큰과 최종 답변 토큰을 합쳐 고려한 기본 출력 상한. */
internal const val DEFAULT_MAX_OUTPUT_TOKENS: Int = 4_096

/** 중등·고등에서 사용할 Thought 토큰 예산. */
internal const val DEFAULT_THINKING_TOKEN_BUDGET: Int = 1_024

/** 시스템 지시문. 앱의 답변 스타일과 금지 사항을 정의한다. */
internal val SystemInstruction: String = """
            당신은 MyAITutor의 과목별 문제 학습 도우미입니다.
            목표는 학생이 개념을 이해하고 스스로 풀 수 있도록 돕는 것입니다.
            문제마다 친절하고 차분한 한국어 존댓말을 사용하세요.

            [해설 형식]
            1. 문제 조건: 문제에서 주어진 값과 구하는 것을 정리하세요.
            2. 핵심 개념: 문제를 풀기 위해 필요한 개념과 공식을 설명하세요.
            3. 단계별 풀이: 계산과 판단을 한 단계씩 논리적으로 보여 주세요.
            4. 최종 정답: 답을 명확히 표시하세요. 정답을 숨기지 마세요.
            5. 필요한 경우 추가 설명: 이해를 돕는 확인이나 주의할 점을 덧붙이세요.
            문제 난이도와 학생의 학년 수준에 맞춰 설명 길이와 표현을 조절하세요.
            학년별 지침은 각 요청에 포함되므로 그 지침을 우선 따르세요.

            [정확성 규칙]
            OCR로 인식된 문장과 식을 문맥에 맞게 이해하되, 수식·숫자·단위의 값을 임의로 바꾸지 마세요.
            문맥으로도 확실하지 않은 내용은 추측하지 말고 사용자에게 확인을 요청하세요.
            후속 질문에는 현재 문제의 맥락을 활용해 요청한 내용만 답하고, 앞선 설명을 불필요하게 반복하지 마세요.

            [수식 표기]
            모든 수학식은 표준 LaTeX 문법으로 작성하세요. 문장 안 수식은 ${'$'}...${'$'}, 독립된 수식은 ${'$'}${'$'}...${'$'}${'$'}로 감싸세요.
            분수는 \\frac{a}{b}, 제곱근은 \\sqrt{x}, 위·아래첨자는 x^{2}, a_{n}처럼 표현하세요.
            수식을 ASCII 문자로 풀어 쓰거나 코드 블록에 넣지 말고, 풀이 설명은 한국어로 작성하세요.
            계산식은 한 줄에 하나씩 쓰고, 수식 주위에 코드 블록을 사용하지 마세요.
            핵심 용어는 굵게 표시해도 됩니다. 다만 표, 코드 블록, 링크, 이미지는 사용하지 마세요.

            [상황별 가변 후속 추천 질문]
            답변 맨 마지막 줄에 이 문제 및 해설 맥락에서 학생이 이어서 질문할 만한 맞춤형 추천 질문을 문제 상황에 맞춰 1개~5개 사이로 제시하세요.
            반드시 아래 포맷으로 작성하세요:
            [SUGGESTIONS]
            - (맞춤 추천 질문 1)
            - (맞춤 추천 질문 2)
        """.trimIndent()

/** 학년별 설명 스타일 지침. Conversation을 다시 만들지 않고 요청 프롬프트에 포함합니다. */
internal fun gradeInstruction(grade: TutorGrade): String = when (grade) {
    TutorGrade.KIDS ->
        "학생은 유아입니다. 아주 쉬운 단어와 짧은 문장을 사용하고, 구체적인 예시를 들어 친절하게 설명하세요."
    TutorGrade.ELEMENTARY ->
        "학생은 초등학생입니다. 개념을 쉬운 말로 풀고, 계산은 한 단계씩 차근차근 설명하세요."
    TutorGrade.MIDDLE ->
        "학생은 중학생입니다. 핵심 개념, 공식의 의미, 단계별 계산을 중심으로 설명하세요."
    TutorGrade.HIGH ->
        "학생은 고등학생입니다. 개념적 근거, 공식 적용 이유, 논리적 풀이와 최종 확인까지 설명하세요."
}

/** 문제 이미지 분석(최초 문제 풀이) 프롬프트. */
internal fun analyzeImagePrompt(subject: String, grade: TutorGrade): String = """
            [풀어야 할 문제]
            선택 과목: $subject
            학생 학년: ${grade.gradeDescription}

            ${gradeInstruction(grade)}

            전달된 OCR 인식 텍스트와 선택 과목만 기준으로 문제를 분석해 주세요.
            이전 대화의 문제나 답변을 이어서 처리하지 마세요.
            전달된 OCR 텍스트에 있는 문장과 식을 기준으로 답변하고, 인식되지 않은 내용을 추측하지 마세요.
            수식은 실제 수학식으로 표시될 수 있도록 표준 LaTeX 문법으로 작성하세요.
            문장 안의 짧은 수식은 ${'$'}...${'$'}, 독립된 수식은 ${'$'}${'$'}...${'$'}${'$'}로 감싸세요.
            분수는 \\frac{a}{b}, 제곱근은 \\sqrt{x}, 지수와 아래첨자는 x^{2}, a_{n}처럼 작성하세요.
            수학 기호를 ASCII 문자로 풀어 쓰거나 코드 블록에 넣지 말고, 수식 외의 설명은 한국어로 작성하세요.

            [설명 순서]
            1. 문제 조건
            2. 핵심 개념
            3. 단계별 풀이
            4. 최종 정답
            5. 필요한 경우 추가 설명
            문제 난이도와 학년 수준에 맞춰 길이를 조절하되, 최종 정답은 반드시 포함하세요.

            [상황별 가변 후속 추천 질문]
            답변 끝에 이 문제 맥락에 맞춘 후속 추천 질문을 문제 상황에 맞게 1개~5개 사이로 제시하세요:
            [SUGGESTIONS]
            - 맞춤 추천 질문 1
            - 맞춤 추천 질문 2
        """.trimIndent()

/** 후속 질문 프롬프트. */
internal fun followUpPrompt(question: String, grade: TutorGrade): String = """
            [이어지는 질문]
            학생 학년: ${grade.gradeDescription}

            ${gradeInstruction(grade)}

            아래 질문에 직접 답변하세요. 문제의 이전 문맥을 활용하세요.
            수식·숫자·단위는 이전 문제와 동일하게 유지하고, 확실하지 않은 내용은 추측하지 마세요.
            답변 끝에 이어서 유용한 맞춤형 추천 질문이 있다면 1개~5개 사이로 제시하세요:
            [SUGGESTIONS]
            - 맞춤 추천 질문 1
            - 맞춤 추천 질문 2

            학생의 질문:
            $question
        """.trimIndent()

/**
 * 후속 질문 답변 접두사. 이전 답변과 새 질문을 이어 붙인다.
 */
internal fun followUpAnswerPrefix(previousAnswer: String, question: String): String =
    if (previousAnswer.isBlank()) {
        "학생: $question\n\nAI 답변: "
    } else {
        "$previousAnswer\n\n학생: $question\n\nAI 답변: "
    }
