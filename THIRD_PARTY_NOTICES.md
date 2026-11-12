# Third-party notices — MyAITutor 0.0.1

이 문서는 초기 프로젝트에서 사용하는 주요 외부 구성요소와 원본 주소를 기록합니다. 출시 전에는 실제 Gradle dependency graph와 각 배포 revision의 LICENSE/NOTICE를 다시 확인해야 합니다.

| 구성요소 | 사용 목적 | 라이선스/조건 | 출처 |
|---|---|---|---|
| Gemma 4 E4B-IT LiteRT-LM | 온디바이스 멀티모달 AI | Hugging Face 저장소 모델 카드 기준 Apache 2.0 | https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm |
| LiteRT-LM Android | 모델 추론 런타임 | 배포 AAR 및 전이 의존성의 라이선스 확인 필요 | https://github.com/google-ai-edge/LiteRT-LM |
| AndroidX CameraX | 카메라 프리뷰 및 촬영 | AndroidX 라이선스 | https://developer.android.com/jetpack/androidx/releases/camera |
| Jetpack Compose | Android UI | AndroidX 라이선스 | https://developer.android.com/compose |
| Compose LaTeX Renderer | AI 답변의 LaTeX 수식 렌더링 | MIT | https://github.com/huarangmeng/latex |
| Kotlin Coroutines | 비동기 처리 | Apache 2.0 | https://github.com/Kotlin/kotlinx.coroutines |

모델 파일은 앱에 포함하지 않고 사용자가 앱 내에서 Hugging Face 원본 저장소로부터 다운로드합니다. 앱 출시 전에 Gemma 모델 관련 최신 이용 조건, LiteRT-LM AAR의 라이선스, 모든 전이 의존성의 고지 의무를 최종 점검하십시오.
