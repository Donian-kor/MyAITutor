# 나만의 AI쌤 (MyAITutor) Android

**나만의 AI쌤**은 촬영한 문제의 글자를 기기 안의 PaddleOCR이 인식하고, 인식된 텍스트를 Gemma 4 E4B-IT가 분석해 개념 설명과 풀이를 제공하는 Android 온디바이스 학습 앱입니다. Gemma에는 원본 사진이 아니라 OCR 텍스트가 전달됩니다.

## 주요 기능

- Jetpack Compose + Material 3 기반 반응형 UI (다크/라이트 테마, Shape/Typography 시스템)
- 별도 전체 화면 후면 카메라 촬영과 사진 확인·재촬영
- 촬영 사진 확인 화면에서 손가락으로 사각형 영역을 선택해 해당 부분만 OCR 처리 (선택하지 않으면 전체 사진 처리)
- 선택 영역은 촬영 원본의 픽셀 해상도를 유지해 잘라내며, 전체 사진은 EXIF 방향 보정 후 장변 1600px 기준으로 정규화
- 사진 확인 후 현재 선택 과목과 새 이미지를 기준으로 AI 풀이 자동 시작
- PaddleOCR 인식 단계와 Gemma 풀이 단계를 화면에 구분해 표시하고, 인식 텍스트를 펼쳐 확인
- 과목 선택 드롭다운 박스 (국어, 영어, 수학, 과학, 사회, 역사, 도덕·윤리, 기술·가정, 정보, 기타)
- 학년 선택 칩 (유아, 초등, 중등, 고등) - 학년별 맞춤 설명 스타일
- LaTeX 수식의 인라인·블록 렌더링 및 긴 수식 자동 줄바꿈
- AI 답변과 학생 질문을 개별 채팅 버블로 분리 표시 (서로 다른 배경색으로 구분)
- 짧은 첫 답변 뒤 후속 설명 선택 메뉴 (자세한 풀이·답변만·간략한 요약·다시 설명·직접 입력)
- 반응형 레이아웃: 좁은 화면에서도 잘림/가림 없이 적응 (BoxWithConstraints 기반 컴팩트 모드)
- 설정 화면과 Gemma 모델 다운로드 상태 및 실시간 진행률 표시
- Hugging Face에서 Gemma 4 E4B `.litertlm` 파일 다운로드 (Wi-Fi 전용)
- 모델 다운로드 후 SHA-256 체크섬 자동 무결성 검증
- LiteRT-LM Kotlin API를 통한 GPU 우선 / CPU 대체 초기화
- OCR 텍스트 + 과목 + 학년 + 교사 지침을 Gemma에 전달
- 생성 중인 답변을 실시간 스트리밍 표시하며 사용자가 스크롤 중일 때 읽던 위치 유지
- 답변 생성 중단(Stop) 기능 지원
- 같은 대화 세션에서 추가 질문 전송 및 새 문제 촬영 시 세션 초기화
- 분석 후 임시 촬영 이미지 파일 자동 삭제
- PaddleOCR Android SDK (`:ppocr-sdk`)와 PP-OCR 모델을 앱에 포함

## 아직 포함하지 않은 기능

- Room 기반 학습 기록 및 대화의 앱 재시작 후 복원
- 음성 입력 및 음성 답변
- 여러 개의 OCR 영역을 한 번에 지정하는 기능
- 기기별 성능 프로파일과 자동 백엔드 선택

AI 답변의 `$...$` 및 `$$...$$` 형식 수식은 LaTeX 렌더러로 표시합니다.

## OCR 및 AI 모델

- **문자 검출 및 인식:** PaddleOCR Android SDK (`:ppocr-sdk`), ONNX Runtime 및 OpenCV 사용
- **인식 모델:** `korean_PP-OCRv5_mobile_rec` (`ppocr-sdk/src/main/assets/models/rec/inference.yml`에서 확인)
- **검출 모델:** `models/det/inference.onnx` (세부 모델 ID는 현재 파일에서 확인되지 않음)
- **모델 출처:** [PaddleOCR 모델 문서](https://www.paddleocr.ai/latest/version3.x/module_usage/text_recognition.html), [PaddleOCR Android SDK](https://github.com/PaddlePaddle/PaddleOCR/tree/main/deploy/ppocr-android)
- **풀이 모델:** [Gemma 4 E4B-IT LiteRT-LM](https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm)

OCR 인식 텍스트는 홈 화면에서 확인할 수 있습니다. 선택 영역의 인식률은 사진 품질과 선택 범위에 따라 달라질 수 있으며 정확도를 보장하지 않습니다.

## 문제 풀이 사용법

1. 상단 헤더 카드의 과목 드롭다운에서 과목을 선택하고 학년 칩에서 학년을 선택합니다.
2. 하단 카메라 버튼으로 문제를 촬영합니다.
3. 사진 확인 화면에서 필요한 경우 문제 부분을 손가락으로 사각형 드래그해 선택합니다. 선택하지 않으면 전체 사진을 처리합니다.
4. **확인**을 누르면 PaddleOCR이 사진 또는 선택 영역에서 글자를 인식하고, Gemma 4 E4B가 인식된 텍스트와 선택 과목·학년을 바탕으로 풀이합니다.
5. **PaddleOCR 인식 내용 보기**를 눌러 Gemma에 전달된 텍스트를 확인할 수 있습니다.
6. 답변 아래의 추천 질문을 누르거나 직접 입력해 설명을 이어갈 수 있습니다. AI 답변(하늘색)과 학생 질문(연두색)이 개별 버블로 구분 표시됩니다.
7. 다른 과목의 문제를 풀려면 과목을 바꾸고 새 사진을 촬영합니다. 새 과목을 선택하면 이전 사진과 대화 세션 상태가 초기화됩니다.

## 개발 환경

- Android Studio: AGP 9.5.1 및 Gradle 9.6.0을 지원하는 버전
- JDK: 17 이상
- Android SDK Platform: API 37
- Android Build Tools: Android Studio SDK Manager가 AGP에 맞는 버전을 설치하도록 설정
- 최소 Android: API 26 (Android 8.0)
- 지원 ABI: 64비트 전용 (`arm64-v8a`, `x86_64`)
- Kotlin Compose Compiler: 2.4.21
- Compose BOM: 2026.09.00
- Material 3 (androidx.compose.material3)
- LiteRT-LM Android: 0.18.0
- CameraX: 1.6.2
- PaddleOCR SDK: 저장소의 `:ppocr-sdk` 모듈
- ONNX Runtime Android: 1.21.1
- OpenCV Android: 4.13.0 (`org.opencv:opencv`, Maven Central)
- Compose LaTeX Renderer: 1.7.2 (MIT)

## 프로젝트 열기

1. 압축 파일 또는 Git 저장소 폴더를 준비합니다.
2. Android Studio에서 **Open**을 선택합니다.
3. 최상위 프로젝트 폴더를 엽니다. (`app` 폴더가 아니라 `settings.gradle.kts`가 있는 최상위 폴더)
4. Gradle Sync가 끝날 때까지 기다립니다. 최초 동기화에는 Gradle 및 라이브러리 다운로드를 위해 인터넷이 필요합니다.
5. Android 휴대폰(64비트, RAM 8GB 이상 권장)을 USB로 연결하고 USB 디버깅을 활성화합니다.
6. 실행 장치로 휴대폰을 선택하고 **Run**을 누릅니다.

### Gradle 빌드 명령

프로젝트에는 Gradle 9.6.0을 준비하는 `gradlew.bat` / `gradlew` 스크립트가 포함되어 있습니다.

Windows 터미널에서 직접 실행:

```bat
gradlew.bat assembleDebug
```

APK 출력 위치:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 최초 AI 모델 설치

1. 앱을 실행하고 우측 상단 설정 아이콘을 누릅니다.
2. **AI 모델 다운로드**를 누릅니다.
3. 약 3.66GB 모델 파일이므로 Wi-Fi 연결과 충분한 저장 공간(4.3GB 이상)을 확인합니다.
4. 다운로드 완료 후 자동 SHA-256 무결성 검증을 거친 뒤 홈으로 돌아와 사용합니다.

모델 원본:

- Hugging Face: https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm
- 파일: `gemma-4-E4B-it.litertlm`
- SHA-256 해시값: `0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0`
- 모델 라이선스: 저장소 모델 카드 기준 Apache 2.0

모델은 APK에 포함되지 않으며 앱 전용 외부 저장소에 다운로드됩니다. 다운로드 완료 후 파일 크기와 SHA-256 체크섬을 검증하며, 실패 시 재다운로드를 안내합니다.

## 기기 성능 주의 및 권장 사양

모델 파일 크기 3.66GB는 실행 중 메모리 사용량과 다릅니다. 실행 시 가중치 + 대화 KV 캐시 + OS 메모리가 요구되므로 기기 하드웨어 사양이 중요합니다.

- **최소 하드웨어 사양**: 64비트(arm64-v8a) OS, 최소 6GB RAM (8GB RAM 이상 권장)
- **미지원 원인**: RAM 4GB 이하 기기는 모델 로딩 시 메모리 부족(OOM)으로 앱이 강제 종료됩니다.

#### ❌ 미지원 기기 (RAM 4GB 이하 또는 32비트 — OOM 튕김 발생)
- **갤럭시 A / M / F 시리즈 (저가형)**: Galaxy A01~A05, A10~A15, A20~A25, A30~A33 (RAM 4GB 이하 모델), M10~M33, F12~F23 등
- **갤럭시 탭 (저가형 태블릿)**: Galaxy Tab A7 / A7 Lite, Tab A8 10.5, Tab A9 / A9+ (RAM 4GB 옵션), Tab Active3
- **구형 갤럭시 플래그십**: Galaxy S7 / S7 Edge, S8 / S8+, S9 기본형 (RAM 4GB), Note 8 등

#### ⚠️ 미지원 권장 또는 주의 기기 (RAM 6GB 모델 — 카메라+AI 동시 실행 시 튕김 위험)
- Galaxy S9+ / Note 9 (RAM 6GB 모델), S10e / S10 / S10+ (RAM 6GB 옵션), S20 FE / S21 FE (RAM 6GB 옵션)
- Galaxy A34 5G / A35 5G / A51 5G / A52 / A53 5G / A54 5G (RAM 6GB 옵션 모델)

#### ✅ 정상 지원 권장 기기 (RAM 8GB 이상)
- **갤럭시 S 시리즈**: Galaxy S20 / S21 / S22 / S23 / S24 시리즈 (S20 Ultra, S21~S24 전 모델 등)
- **갤럭시 폴더블 시리즈**: Galaxy Z Fold 2 / Fold 3 / Fold 4 / Fold 5 / Fold 6, Z Flip 3 / Flip 4 / Flip 5 / Flip 6
- **갤럭시 탭 고성능 라인업**: Galaxy Tab S7 / S7+, Tab S8 / S8+ / S8 Ultra, Tab S9 / S9+ / S9 Ultra, Tab S10+ / S10 Ultra
- **갤럭시 A 시리즈 (고성능 모델)**: Galaxy A54 5G / A55 5G (RAM 8GB 이상 선택 모델)

## 개인정보 처리

- 카메라 권한은 사용자가 허용해야 합니다.
- 촬영된 사진은 분석 요청 처리를 위한 임시 파일로 저장되며 추론 완료 또는 재촬영 시 자동 삭제됩니다.
- 모든 AI 추론과 이미지 처리는 기기 내부에서 작동하며 외부 AI 서버로 데이터를 전송하지 않습니다.
- 모델 최초 다운로드 외에 네트워크 사용은 하지 않습니다.
- 별도의 회원가입이나 계정 저장이 없습니다.

## 패키지 및 브랜딩 정보

- 앱 표시 이름: `나만의 AI쌤` (`@string/app_name`)
- 앱 아이콘: `@drawable/app_icon`
- Application ID: `com.donian.myaitutor`
- 앱 버전은 `app/build.gradle.kts`에서 Git 커밋 수로 자동 계산합니다. `versionCode`는 커밋 수이며 `versionName`은 `0.<커밋 수 / 10>.<커밋 수 % 10>` 형식입니다. 예: 커밋 수 76이면 `0.7.6`; Debug 빌드에는 `-debug`가 붙습니다.
- 출시용 키스토어 및 release 서명 설정은 아직 프로젝트에 추가되지 않았습니다.

## UI 디자인 시스템

- **색상 팔레트**: 딥 블루(#1565C0) 기본 색상, 스카이 블루 보조 색상, 민트 그린(tertiary) 강조
- **Shape 시스템**: extraSmall(8dp) ~ extraLarge(28dp) 일관된 라운드 코너
- **Typography**: headline/title/body/label 위계 체계
- **반응형 레이아웃**: BoxWithConstraints 기반 컴팩트/일반 모드 전환
- **애니메이션**: staggered fade-in + slide-in 전환 효과

## 알려진 제한

- Gemma 4 E4B는 현재 원본 이미지가 아닌 PaddleOCR 텍스트를 입력받습니다. OCR 결과와 풀이 정확도는 사진과 기기 환경에 따라 달라질 수 있습니다.
- OpenCV 및 ONNX Runtime 네이티브 라이브러리는 `arm64-v8a`, `x86_64` 대상으로 구성되어 있습니다.
- 수식은 표준 LaTeX 문법을 사용하는 경우 렌더링되며, 미지원 명령이나 비표준 문법은 텍스트로 표시될 수 있습니다.
- 좁은 화면에서는 카메라 촬영 버튼이 아이콘으로 표시되고 입력창과 전송 버튼과 한 줄로 정렬됩니다.
- AI 생성과 모델 해제는 한 번에 하나씩 처리합니다. 답변 생성 중에는 중단(Stop)을 누른 후 새 질문을 해야 합니다.
- 대화 문맥은 현재 앱 프로세스의 메모리에 유지되며, 앱 프로세스 종료 후 대화 복원은 후속 버전에서 구현합니다.