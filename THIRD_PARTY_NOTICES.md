# Third-party notices — MyAITutor

이 문서는 현재 프로젝트에서 사용하는 주요 외부 구성요소와 원본 주소를 기록합니다. 출시 전에는 실제 Gradle dependency graph와 각 배포 revision의 LICENSE/NOTICE를 다시 확인해야 합니다.

| 구성요소 | 사용 목적 | 라이선스/조건 | 출처 |
|---|---|---|---|
| Gemma 4 E4B-IT LiteRT-LM | 온디바이스 텍스트 기반 문제 풀이 | Hugging Face 저장소 모델 카드 기준 Apache 2.0 | https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm |
| LiteRT-LM Android | 모델 추론 런타임 | 배포 AAR 및 전이 의존성의 라이선스 확인 필요 | https://github.com/google-ai-edge/LiteRT-LM |
| PaddleOCR Android SDK (`:ppocr-sdk`) | 기기 내 텍스트 검출 및 인식 | 저장소에 포함된 PaddleOCR `LICENSE.txt` 기준 Apache 2.0 | https://github.com/PaddlePaddle/PaddleOCR/tree/main/deploy/ppocr-android |
| PP-OCRv5 한국어 인식 모델 | `korean_PP-OCRv5_mobile_rec` 텍스트 인식 | 모델별 배포 조건은 출시 전 PaddleOCR 모델 문서와 원본 revision에서 재확인 | https://www.paddleocr.ai/latest/version3.x/module_usage/text_recognition.html |
| ONNX Runtime Android 1.21.1 | PaddleOCR ONNX 모델 추론 | MIT | https://github.com/microsoft/onnxruntime |
| OpenCV Android 4.13.0 | OCR 이미지 처리 및 네이티브 연산 | Apache 2.0 | https://github.com/opencv/opencv |
| AndroidX CameraX | 카메라 프리뷰 및 촬영 | AndroidX 라이선스 | https://developer.android.com/jetpack/androidx/releases/camera |
| Jetpack Compose | Android UI | AndroidX 라이선스 | https://developer.android.com/compose |
| Compose LaTeX Renderer | AI 답변의 LaTeX 수식 렌더링 | MIT | https://github.com/huarangmeng/latex |
| Kotlin Coroutines | 비동기 처리 | Apache 2.0 | https://github.com/Kotlin/kotlinx.coroutines |
| AndroidX ExifInterface | 촬영 이미지 EXIF 방향 보정 | AndroidX 라이선스 | https://developer.android.com/jetpack/androidx/releases/exifinterface |
| JUnit 4 / kotlinx-coroutines-test | 단위 테스트 (APK 미포함) | JUnit: Eclipse Public License 1.0 / Coroutines: Apache 2.0 | https://junit.org |

Gemma 모델 파일은 앱에 포함하지 않고 사용자가 앱 내에서 Hugging Face 원본 저장소로부터 다운로드합니다. PaddleOCR의 ONNX 모델 파일은 `ppocr-sdk/src/main/assets/models/`에 포함되어 앱과 함께 배포됩니다. 앱 출시 전에 Gemma 및 PaddleOCR 모델의 최신 이용 조건, OpenCV·ONNX Runtime·LiteRT-LM AAR과 모든 전이 의존성의 라이선스 및 고지 의무를 최종 점검하십시오.
