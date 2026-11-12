package com.donian.myaitutor.ui.home

internal val Subjects = listOf("국어", "수학", "영어", "과학", "사회", "역사", "도덕·윤리", "기술·가정", "정보", "기타")
internal data class FollowUpChoice(val label: String, val prompt: String?)

internal val FollowUpChoices = listOf(
    FollowUpChoice(
        label = "자세한 풀이",
        prompt = "방금 촬영한 문제를 핵심 개념과 계산 과정을 포함해 단계별로 자세히 풀이해 주세요."
    ),
    FollowUpChoice(
        label = "답변만",
        prompt = "방금 촬영한 문제의 최종 정답만 알려 주세요. 풀이 설명은 생략해 주세요."
    ),
    FollowUpChoice(
        label = "간략한 요약",
        prompt = "방금 설명한 풀이를 핵심만 2~3문장으로 간략하게 요약해 주세요."
    ),
    FollowUpChoice(
        label = "다시 설명",
        prompt = "방금 풀이를 더 쉬운 표현과 다른 설명 방식으로 다시 설명해 주세요."
    ),
    FollowUpChoice(label = "직접 입력", prompt = null)
)