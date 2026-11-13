package com.donian.myaitutor.ai

/**
 * 학년 구분. 학년 상태는 [com.donian.myaitutor.ui.home.HomeUiState]가 단일 원본으로 소유하고,
 * 프롬프트와 Thinking 설정을 만들 때만 이 값을 사용합니다.
 */
enum class TutorGrade(
    val label: String,
    internal val gradeDescription: String
) {
    ELEMENTARY("초등", "초등학생"),
    MIDDLE("중등", "중학생"),
    HIGH("고등", "고등학생");

    /** 중등·고등만 Thought 채널을 사용하는 Thinking을 켭니다. */
    val usesThinking: Boolean
        get() = this == MIDDLE || this == HIGH
}

/** Thinking 토큰과 최종 답변 토큰을 합쳐 고려한 기본 출력 상한. */
internal const val DEFAULT_MAX_OUTPUT_TOKENS: Int = 2_048

/** 중등·고등에서 사용할 Thought 토큰 예산. */
internal const val DEFAULT_THINKING_TOKEN_BUDGET: Int = 1_024

/** 시스템 지시문. 앱의 답변 스타일과 금지 사항을 정의한다. */
internal val SystemInstruction: String = """
            [답변 시작 규칙 - 매우 중요]
            자기소개나 인사말("안녕하세요! 저는...", "안녕하세요!", "도우미입니다" 등)을 절대로 출력하지 마세요.
            인사말 없이 질문의 핵심에 바로 답변하세요.
            당신은 수학에 한정되지 않은 전 과목 학습 도우미입니다. 특정 과목만 가르칠 수 있다고 말하지 마세요.

            [설명 방식]
            질문의 과목과 유형에 맞춰 설명하세요. 문제 풀이에는 필요한 조건, 핵심 개념, 풀이 과정과 답을 설명하고,
            개념·역사·어학 등 일반 질문에는 문제 풀이 형식을 억지로 적용하지 말고 핵심을 직접 설명하세요.
            답과 결론은 명확히 제시하고, 필요한 경우 예시나 추가 설명을 덧붙이세요.
            문제 난이도와 학생의 학년 수준에 맞춰 설명 길이와 표현을 조절하세요.
            학년별 지침은 각 요청에 포함되므로 그 지침을 우선 따르세요.

            [정확성 규칙]
            OCR로 인식된 문장과 식을 문맥에 맞게 이해하되, 수식·숫자·단위의 값을 임의로 바꾸지 마세요.
            문맥으로도 확실하지 않은 내용은 추측하지 말고 사용자에게 확인을 요청하세요.
            후속 질문에는 현재 문제의 맥락을 활용해 요청한 내용만 답하고, 앞선 설명을 불필요하게 반복하지 마세요.

            [수식 표기]
            수학식이 필요한 경우 표준 LaTeX 문법으로 작성하세요. 문장 안 수식은 ${'$'}...${'$'}, 독립된 수식은 ${'$'}${'$'}...${'$'}${'$'}로 감싸세요.
            분수는 \\frac{a}{b}, 제곱근은 \\sqrt{x}, 위·아래첨자는 x^{2}, a_{n}처럼 표현하세요.
            수식을 ASCII 문자로 풀어 쓰거나 코드 블록에 넣지 말고, 풀이 설명은 한국어로 작성하세요.
            계산식은 한 줄에 하나씩 쓰고, 수식 주위에 코드 블록을 사용하지 마세요.
            핵심 용어는 굵게 표시해도 됩니다. 다만 표, 코드 블록, 링크, 이미지는 사용하지 마세요.

            [상황별 가변 후속 추천 질문]
            답변 맨 마지막 줄에 이 문제 및 해설 맥락에서 학생이 이어서 질문할 만한 맞춤형 추천 질문을 최대 4개까지, 각 질문은 짧고 핵심만 (1~2문장, 30자 이내 권장) 작성하세요.
            질문에 수학 수식이 포함될 경우 반드시 표준 LaTeX 문법(${'$'}...${'$'})으로 감싸서 정확하게 표시되도록 하세요.
            반드시 아래 포맷으로 작성하세요:
            [SUGGESTIONS]
            - (맞춤 추천 질문 1)
            - (맞춤 추천 질문 2)
        """.trimIndent()

/** 학년별 설명 스타일 지침. Conversation을 다시 만들지 않고 요청 프롬프트에 포함합니다. */
internal fun gradeInstruction(grade: TutorGrade): String = when (grade) {
    TutorGrade.ELEMENTARY ->
        "학생은 초등학생입니다. 개념을 쉬운 말로 풀고, 계산은 한 단계씩 차근차근 설명하세요."
    TutorGrade.MIDDLE ->
        "학생은 중학생입니다. 핵심 개념, 공식의 의미, 단계별 계산을 중심으로 설명하세요."
    TutorGrade.HIGH ->
        "학생은 고등학생입니다. 개념적 근거, 공식 적용 이유, 논리적 풀이와 최종 확인까지 설명하세요."
}

/**
 * 과목별 설명 초점 지침. 학년 지침([gradeInstruction])과 나란히 요청 프롬프트에 포함합니다.
 * Conversation은 재사용되므로 SystemInstruction에는 과목을 넣을 수 없습니다.
 *
 * 성격이 같은 과목은 묶고, 목록에 없는 과목은 [DEFAULT_SUBJECT_INSTRUCTION]으로 흡수해
 * 과목이 추가되어도 프롬프트가 깨지지 않게 합니다.
 */
internal fun subjectInstruction(subject: String): String = when (subject) {
    "수학" -> "수학 문제입니다. 풀이 단계마다 근거를 밝히고, 마지막에 답을 검산하세요."
    "과학" -> "과학 문제입니다. 관련 원리·법칙을 먼저 짚고, 계산이 필요하면 단위까지 확인하세요."
    "영어" -> "영어 문제입니다. 정답과 함께 문법·어휘 근거를 설명하고, 필요하면 예문을 드세요."
    "국어" -> "국어 문제입니다. 지문이나 문장 속 근거를 짚어 해설하세요."
    "역사", "사회", "도덕·윤리" -> "이 과목은 사실 관계와 배경이 중요합니다. 개념의 배경과 인과 관계를 중심으로 설명하세요."
    else -> DEFAULT_SUBJECT_INSTRUCTION
}

private const val DEFAULT_SUBJECT_INSTRUCTION =
    "선택 과목에 맞춰 설명의 초점을 정하고, 그 과목에서 중요한 근거를 함께 제시하세요."

/** 수식 표기가 실제로 필요한 과목. 여기에 없으면 LaTeX 지침을 보내지 않습니다. */
private val MATH_NOTATION_SUBJECTS = setOf("수학", "과학")

/** 수학·과학에서 사용하는 LaTeX 수식 표기 지침. */
private val MATH_NOTATION_INSTRUCTION: String = """
            [수식 표기]
            수식은 실제 수학식으로 표시될 수 있도록 표준 LaTeX 문법으로 작성하세요.
            문장 안의 짧은 수식은 ${'$'}...${'$'}, 독립된 수식은 ${'$'}$${'$'}...${'$'}$${'$'}로 감싸세요.
            분수는 \\frac{a}{b}, 제곱근은 \\sqrt{x}, 지수와 아래첨자는 x^{2}, a_{n}처럼 작성하세요.
            수학 기호를 ASCII 문자로 풀어 쓰거나 코드 블록에 넣지 말고, 수식 외의 설명은 한국어로 작성하세요.
        """.trimIndent()

/** 수식이 필요 없는 과목이면 빈 문자열을 돌려줍니다. */
internal fun mathNotationInstruction(subject: String): String =
    if (subject in MATH_NOTATION_SUBJECTS) MATH_NOTATION_INSTRUCTION else ""

/** 문제 이미지 분석(최초 문제 풀이) 프롬프트. */
internal fun analyzeImagePrompt(subject: String, grade: TutorGrade): String = """
            [풀어야 할 문제]
            선택 과목: $subject
            학생 학년: ${grade.gradeDescription}

            ${gradeInstruction(grade)}
            ${subjectInstruction(subject)}

            [주의 사항 - 필수]
            인사말이나 자기소개("안녕하세요! 저는...", "도우미입니다" 등)를 절대로 출력하지 마세요. 인사 없이 곧바로 본론으로 시작하세요.

            전달된 OCR 인식 텍스트와 선택 과목만 기준으로 내용을 분석해 주세요.
            이전 대화의 문제나 답변을 이어서 처리하지 마세요.
            전달된 OCR 텍스트에 있는 문장과 식을 기준으로 답변하고, 인식되지 않은 내용을 추측하지 마세요.
            ${mathNotationInstruction(subject)}

            [설명 방식 - 문제 유형에 맞게]
            먼저 OCR 텍스트가 풀어야 할 문제인지, 개념·지문·요약 대상 같은 일반 학습 자료인지 판단하세요.
            - 풀어야 할 문제라면 아래 순서로 설명하세요.
              1. 문제 조건
              2. 핵심 개념
              3. 단계별 풀이
              4. 최종 정답
              5. 필요한 경우 추가 설명
            - 문제 풀이가 아닌 자료라면 문제 풀이 형식을 억지로 적용하지 말고, 핵심 내용을 이해하기 쉽게 정리해 설명하세요.
            어느 경우든 문제 난이도와 학년 수준에 맞춰 길이를 조절하고, 답이나 결론은 반드시 명확히 제시하세요.

            [상황별 가변 후속 추천 질문]
            답변 끝에 이 문제 맥락에 맞춘 후속 추천 질문을 최대 4개까지, 각 질문은 짧고 핵심만 (1~2문장, 30자 이내 권장) 작성하세요:
            질문에 수학 수식이 포함될 경우 반드시 표준 LaTeX 문법(${'$'}...${'$'})으로 감싸서 정확하게 표시되도록 하세요.
            [SUGGESTIONS]
            - 맞춤 추천 질문 1
            - 맞춤 추천 질문 2
        """.trimIndent()

/** 후속 질문 프롬프트. */
internal fun followUpPrompt(
    question: String,
    subject: String,
    grade: TutorGrade
): String = """
            [이어지는 질문]
            선택 과목: $subject
            학생 학년: ${grade.gradeDescription}

            ${gradeInstruction(grade)}
            ${subjectInstruction(subject)}

            인사말이나 자기소개를 절대로 하지 말고 질문에 곧바로 답변하세요.
            아래 질문에 직접 답변하세요. 문제의 이전 문맥을 활용하세요.
            수식·숫자·단위는 이전 문제와 동일하게 유지하고, 확실하지 않은 내용은 추측하지 마세요.
            ${mathNotationInstruction(subject)}
            답변 끝에 이어서 유용한 맞춤형 추천 질문이 있다면 최대 4개까지, 각 질문은 짧고 핵심만 (1~2문장, 30자 이내 권장) 작성하세요.
            질문에 수학 수식이 포함될 경우 반드시 표준 LaTeX 문법(${'$'}...${'$'})으로 감싸서 정확하게 표시되도록 하세요.
            [SUGGESTIONS]
            - 맞춤 추천 질문 1
            - 맞춤 추천 질문 2

            학생의 질문:
            $question
        """.trimIndent()

/** 과목 선택 후 새 대화를 시작하는 자유 질문 프롬프트. */
internal fun standaloneQuestionPrompt(
    question: String,
    subject: String,
    grade: TutorGrade
): String = """
            [새 질문]
            선택 과목: $subject
            학생 학년: ${grade.gradeDescription}

            ${gradeInstruction(grade)}
            ${subjectInstruction(subject)}

            선택 과목에 맞춰 질문에 직접 답변하세요. 특정 과목만 가르칠 수 있다고 말하지 말고, 다른 과목의 질문도 성실히 설명하세요.
            문제 풀이가 아닌 일반 질문이면 문제 풀이 형식을 억지로 적용하지 말고, 질문에 맞는 방식으로 설명하세요.
            확실하지 않은 내용은 추측하지 마세요. 답변은 한국어 존댓말로 작성하세요.
            ${mathNotationInstruction(subject)}

            학생의 질문:
            $question
        """.trimIndent()
