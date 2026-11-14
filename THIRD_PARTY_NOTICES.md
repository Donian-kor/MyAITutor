# Third-party notices — MyAITutor

이 문서는 현재 프로젝트에서 사용하는 주요 외부 구성요소와 원본 주소를 기록합니다. 출시 전에는 실제 Gradle dependency graph와 각 배포 revision의 LICENSE/NOTICE를 다시 확인해야 합니다.

## 1. 모델 (앱에 포함하지 않고 사용자가 다운로드)

| 구성요소 | 사용 목적 | 라이선스/조건 | 출처 |
|---|---|---|---|
| Gemma 4 E4B-IT LiteRT-LM | 온디바이스 텍스트 기반 문제 풀이 및 자유 대화 | Hugging Face 저장소 모델 카드 기준 Apache 2.0 | https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm |
| Qwen3-4B-Instruct-2507 LiteRT-LM | 온디바이스 텍스트 기반 문제 풀이(학습 전용) | Apache 2.0 (원본 Qwen3-4B-Instruct-2507 및 LiteRT 변환본 기준) | https://huggingface.co/litert-community/Qwen3-4B-Instruct-2507 |

## 2. OCR 및 이미지 처리

| 구성요소 | 사용 목적 | 라이선스/조건 | 출처 |
|---|---|---|---|
| PaddleOCR Android SDK (`:ppocr-sdk`) | 기기 내 텍스트 검출 및 인식 | 저장소에 포함된 PaddleOCR `LICENSE.txt` 기준 Apache 2.0 | https://github.com/PaddlePaddle/PaddleOCR/tree/main/deploy/ppocr-android |
| PP-OCRv5 한국어 인식 모델 | `korean_PP-OCRv5_mobile_rec` 텍스트 인식 | Apache 2.0 (PaddleOCR 프로젝트 기준) | https://www.paddleocr.ai/latest/version3.x/module_usage/text_recognition.html |
| ONNX Runtime Android 1.21.1 | PaddleOCR ONNX 모델 추론 | MIT | https://github.com/microsoft/onnxruntime |
| OpenCV Android 4.13.0 | OCR 이미지 처리 및 네이티브 연산 | Apache 2.0 | https://github.com/opencv/opencv |

## 3. AndroidX / Jetpack

| 구성요소 | 사용 목적 | 라이선스/조건 | 출처 |
|---|---|---|---|
| AndroidX Core KTX 1.19.1 | Android 코어 유틸리티 | AndroidX 라이선스(Apache 2.0) | https://developer.android.com/jetpack/androidx/releases/core |
| AndroidX Lifecycle 2.11.0 | ViewModel, 런타임 생명주기 | AndroidX 라이선스(Apache 2.0) | https://developer.android.com/jetpack/androidx/releases/lifecycle |
| AndroidX Activity Compose 1.13.0 | Compose Activity 진입점 | AndroidX 라이선스(Apache 2.0) | https://developer.android.com/jetpack/androidx/releases/activity |
| Jetpack Compose (BOM 2026.09.00) | Android UI | AndroidX 라이선스(Apache 2.0) | https://developer.android.com/compose |
| AndroidX Compose Material Icons Extended | Compose 머티리얼 아이콘 세트 | AndroidX 라이선스(Apache 2.0) | https://developer.android.com/jetpack/androidx/releases/compose |
| AndroidX CameraX 1.6.2 | 카메라 프리뷰 및 촬영 | AndroidX 라이선스(Apache 2.0) | https://developer.android.com/jetpack/androidx/releases/camera |
| AndroidX ExifInterface 1.4.2 | 촬영 이미지 EXIF 방향 보정 | AndroidX 라이선스(Apache 2.0) | https://developer.android.com/jetpack/androidx/releases/exifinterface |

## 4. AI 런타임 및 수식 렌더링

| 구성요소 | 사용 목적 | 라이선스/조건 | 출처 |
|---|---|---|---|
| LiteRT-LM Android 0.18.0 | 온디바이스 LLM 추론 런타임 | Apache 2.0 | https://github.com/google-ai-edge/LiteRT-LM |
| Compose LaTeX Renderer 1.7.2 | AI 답변의 LaTeX 수식 렌더링 | MIT | https://github.com/huarangmeng/latex |

## 5. 코루틴 및 테스트

| 구성요소 | 사용 목적 | 라이선스/조건 | 출처 |
|---|---|---|---|
| Kotlin Coroutines 1.11.0 | 비동기 처리 | Apache 2.0 | https://github.com/Kotlin/kotlinx.coroutines |
| JUnit 4.13.2 | 단위 테스트 (APK 미포함) | Eclipse Public License 1.0 | https://junit.org |
| kotlinx-coroutines-test 1.11.0 | 코루틴 단위 테스트 (APK 미포함) | Apache 2.0 | https://github.com/Kotlin/kotlinx.coroutines |

## 6. 디버그 전용 (릴리스 APK 미포함)

| 구성요소 | 사용 목적 | 라이선스/조건 | 출처 |
|---|---|---|---|
| AndroidX Compose UI Tooling | Compose 디버그 미리보기/검사 | AndroidX 라이선스(Apache 2.0) | https://developer.android.com/jetpack/androidx/releases/compose |

Gemma/Qwen 모델 파일은 앱에 포함하지 않고 사용자가 앱 내에서 Hugging Face 원본 저장소로부터 다운로드합니다. PaddleOCR의 ONNX 모델 파일은 `ppocr-sdk/src/main/assets/models/`에 포함되어 앱과 함께 배포됩니다. 앱 출시 전에 Gemma/Qwen 및 PaddleOCR 모델의 최신 이용 조건, OpenCV·ONNX Runtime·LiteRT-LM AAR과 모든 전이 의존성의 라이선스 및 고지 의무를 최종 점검하십시오.