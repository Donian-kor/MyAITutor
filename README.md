# 나만의 AI쌤 (MyAITutor) Android

**나만의 AI쌤**은 촬영한 문제의 글자를 기기 안의 PaddleOCR이 인식하고, 인식된 텍스트를 온디바이스 AI 모델(Gemma 4 E4B-IT 또는 Qwen3-4B-Instruct-2507)이 분석해 개념 설명과 풀이를 제공하는 Android 학습 앱입니다. AI 모델에는 원본 사진 대신 PaddleOCR이 추출한 텍스트가 전달됩니다.

기본 AI 엔진으로는 **Gemma 4 E4B-IT**를 사용하며, 필요한 경우 설정에서 **Qwen3-4B-Instruct-2507** 모델을 선택해 사용할 수 있습니다. 메모리 효율을 위해 한 번에 하나의 AI 모델만 메모리에 로드되어 동작합니다.

## 주요 기능

- **다중 온디바이스 AI 모델 지원:**
  - **Gemma 4 E4B-IT (기본):** 과목 문제 풀이 및 자유 대화 모드 지원 (다운로드 약 3.66GB, 컨텍스트 4,096 토큰)
  - **Qwen3-4B-Instruct-2507 (추가):** 과목 문제 풀이 전용 (다운로드 약 2.66GB, 컨텍스트 2,048 토큰)
  - **단일 메모리 상주 원칙:** 설정에서 모델 전환 시 이전 모델의 네이티브 자원을 안전하게 해제한 후 새 모델을 1개만 로드합니다.
- **자유 대화 모드 (Gemma 전용):** 과목 학습 외에도 학년별 맞춤 반말 톤으로 일상 고민 및 잡담 대화 가능 (Qwen 활성 시에는 자유 대화 칩이 자동 비활성화되고 안내 문구 표시).
- **통합 AI 모델 선택 및 다운로드 UI:**
  - Gemma와 Qwen 모델의 다운로드 상태, 진행률, 용량을 한 카드에서 확인 및 관리.
  - 모델별 독립 다운로드 및 SHA-256 자동 무결성 검증.
  - 이미 내려받은 모델은 라디오 클릭으로 바로 전환되며, 미설치 모델은 설치 후 전환 지원.
- **추론(Thinking) 모드 토글:** 학년과 무관하게 사용자가 직접 켜고 끌 수 있는 추론 단계 활성화 모드.
- **안전한 전환 복구 메커니즘:** 전환 중 모델 로드 실패 시 자동으로 이전 모델로 복구되며, 복구 실패 시 3가지 선택지(다시 시도 / Gemma로 돌아가기 / 나중에) 다이얼로그 제공.
- **OCR 및 영상 처리 파이프라인:**
  - Jetpack Compose + Material 3 기반 반응형 UI (컴팩트 레이아웃 지원)
  - 전체 화면 후면 카메라 촬영 및 손가락 사각형 영역 선택 크롭 (미선택 시 전체 사진 처리)
  - 선택 영역의 픽셀 해상도 유지 잘라내기 및 EXIF 방향 보정 정규화 (장변 1600px)
  - PaddleOCR 인식 단계와 AI 풀이 단계를 화면에 구분 표시
- **학습 편의 기능:**
  - 과목 선택 드롭다운 (국어, 영어, 수학, 과학, 사회, 역사, 도덕·윤리, 기술·가정, 정보, 기타)
  - 학년 선택 칩 (초등, 중등, 고등) - 학년별 맞춤 설명 스타일
  - LaTeX 수식의 인라인·블록 렌더링 및 긴 수식 자동 줄바꿈
  - AI 답변(하늘색)과 학생 질문(연두색) 개별 채팅 버블 분리
  - 기본 후속 질문 칩 및 직접 입력 후속 대화
  - 생성 중 답변 실시간 스트리밍 및 중단(Stop) 지원
  - 분석 후 임시 촬영 이미지 자동 정리

## OCR 및 AI 모델

- **문자 검출 및 인식:** PaddleOCR Android SDK (`:ppocr-sdk`), ONNX Runtime 및 OpenCV 사용
  - **인식 모델:** `korean_PP-OCRv5_mobile_rec` (`ppocr-sdk/src/main/assets/models/rec/inference.yml`)
  - **검출 모델:** `models/det/inference.onnx`
  - **출처:** [PaddleOCR 모델 문서](https://www.paddleocr.ai/latest/version3.x/module_usage/text_recognition.html), [PaddleOCR Android SDK](https://github.com/PaddlePaddle/PaddleOCR/tree/main/deploy/ppocr-android)
- **풀이 모델 1 (기본):** [Gemma 4 E4B-IT LiteRT-LM](https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm)
  - 파일: `gemma-4-E4B-it.litertlm` (약 3.66GB)
  - Revision: `f254b5ba6e36f7d89cc7892e268e9877bc20f0a0`
  - SHA-256: `0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0`
  - 특징: 멀티모달, 과목 학습 및 자유 대화 지원, 컨텍스트 4,096 토큰
- **풀이 모델 2 (추가):** [Qwen3-4B-Instruct-2507 LiteRT-LM](https://huggingface.co/litert-community/Qwen3-4B-Instruct-2507)
  - 파일: `qwen3_4b_instruct_2507_mixed_int4.litertlm` (약 2.66GB)
  - Revision: `6ee6014fa9193ea4dbb309f3305d6ee0d78db5a0`
  - SHA-256: `9e48b165836256f5344d9d044930607b9c47f6ef34e27f82e96881664f3ba2fd`
  - 특징: 과목 학습 전용, typed parts Jinja 템플릿 처리, 컨텍스트 2,048 토큰

AI 답변의 `$...$` 및 `$$...$$` 형식 수식은 LaTeX 렌더러로 표시합니다.

## 문제 풀이 사용법

1. 상단 헤더 카드의 과목 드롭다운에서 과목을 선택하고 학년 칩에서 학년을 선택합니다. (자유 대화 모드를 원할 경우 '자유 대화' 칩 선택 - Gemma 활성 시 가능)
2. 하단 카메라 버튼으로 문제를 촬영합니다.
3. 사진 확인 화면에서 필요한 경우 문제 부분을 손가락으로 사각형 드래그해 선택합니다. 선택하지 않으면 전체 사진을 처리합니다.
4. **확인**을 누르면 PaddleOCR이 사진 또는 선택 영역에서 글자를 인식하고, 선택된 AI 모델(Gemma 또는 Qwen)이 인식된 텍스트와 선택 과목·학년을 바탕으로 풀이합니다.
5. **PaddleOCR 인식 내용 보기**를 눌러 AI에 전달된 텍스트를 확인할 수 있습니다.
6. 답변 아래의 후속 질문 칩을 누르거나 직접 입력해 설명을 이어갈 수 있습니다. AI 답변과 학생 질문이 개별 버블로 구분 표시됩니다.
7. 다른 과목의 문제를 풀려면 과목을 바꾸고 새 사진을 촬영합니다. 새 과목을 선택하면 이전 사진과 대화 세션 상태가 초기화됩니다.

## 설정 메뉴 구성

설정 화면은 아래 순서로 정돈되어 있습니다:

1. **설정 헤더 카드:** AI 선생님 설정 타이틀 및 뒤로 가기
2. **AI 모델 선택 및 다운로드 카드:** Gemma 4 E4B / Qwen3-4B 라디오 선택, 모델별 다운로드 상태, 진행률, 다운로드/삭제 버튼
3. **Gemma 4 추론(Thinking) 모드 카드:** 직접 켜고 끄는 추론 단계 설정 스위치
4. **정보 카드 섹션:** OCR 모델 및 출처, 다운로드 안내, 학습 방식 안내, 개인정보 오프라인 처리 안내
5. **버전 및 백엔드 정보 카드:** 앱 버전, 실행 환경, 현재 동작 백엔드(GPU/CPU)
6. **최하단 버튼 섹션:** [임시 파일 캐시 정리], [오픈소스 라이선스 및 버전 정보]

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

## 프로젝트 열기 및 빌드

1. Android Studio에서 프로젝트 최상위 폴더(`settings.gradle.kts`가 있는 폴더)를 엽니다.
2. Gradle Sync가 완료될 때까지 기다립니다.
3. Windows 터미널에서 Gradle 빌드 실행:

```bat
gradlew.bat assembleDebug
```

4. 전체 단위 테스트 실행:

```bat
gradlew.bat testDebugUnitTest
```

APK 출력 위치:
```text
app/build/outputs/apk/debug/app-debug.apk
```

## 기기 성능 주의 및 권장 사양

모델 파일 크기(3.66GB / 2.66GB)는 실행 중 메모리 사용량과 다릅니다. 실행 시 가중치 + 대화 KV 캐시 + OS 메모리가 요구되므로 기기 하드웨어 사양이 중요합니다.

- **최소 하드웨어 사양**: 64비트(arm64-v8a) OS, 최소 6GB RAM (8GB RAM 이상 권장)
- **미지원 원인**: RAM 4GB 이하 기기는 모델 로딩 시 메모리 부족(OOM)으로 앱이 강제 종료됩니다.

#### ❌ 미지원 기기 (RAM 4GB 이하 또는 32비트 — OOM 튕김 발생)
- **갤럭시 A / M / F 시리즈 (저가형)**: Galaxy A01~A05, A10~A15, A20~A25, A30~A33 (RAM 4GB 이하 모델), M10~M33, F12~F23 등
- **갤럭시 탭 (저가형 태블릿)**: Galaxy Tab A7 / A7 Lite, Tab A8 10.5, Tab A9 / A9+ (RAM 4GB 옵션), Tab Active3
- **구형 갤럭시 플래그십**: Galaxy S7 / S7 Edge, S8 / S8+, S9 기본형 (RAM 4GB), Note 8 등

#### ⚠️ 주의 기기 (RAM 6GB 모델 — 카메라+AI 동시 실행 시 메모리 주의)
- Galaxy S9+ / Note 9 (RAM 6GB 모델), S10e / S10 / S10+ (RAM 6GB 옵션), S20 FE / S21 FE (RAM 6GB 옵션)
- Galaxy A34 5G / A35 5G / A51 5G / A52 / A53 5G / A54 5G (RAM 6GB 옵션 모델)

#### ✅ 정상 지원 권장 기기 (RAM 8GB 이상)
- **갤럭시 S 시리즈**: Galaxy S20 / S21 / S22 / S23 / S24 시리즈
- **갤럭시 폴더블 시리즈**: Galaxy Z Fold 2~6, Z Flip 3~6
- **갤럭시 탭 고성능 라인업**: Galaxy Tab S7~S10 시리즈
- **갤럭시 A 시리즈 (고성능 모델)**: Galaxy A54 5G / A55 5G (RAM 8GB 이상 선택 모델)

## 개인정보 처리

- 모든 AI 추론과 이미지 처리(PaddleOCR)는 기기 내부에서 오프라인으로 작동하며 외부 AI 서버로 데이터를 전송하지 않습니다.
- 최초 모델 다운로드 및 업데이트에만 인터넷(Wi-Fi 권장)을 사용합니다.
- 촬영된 사진은 분석 요청 처리를 위한 임시 파일로 저장되며 추론 완료 또는 재촬영 시 자동 삭제됩니다.
- 회원가입이나 계정 저장이 없습니다.

## 패키지 및 브랜딩 정보

- 앱 표시 이름: `나만의 AI쌤` (`@string/app_name`)
- Application ID: `com.donian.myaitutor`
- 버전 관리: `app/build.gradle.kts`에서 Git 커밋 수로 자동 계산 (`versionCode` = 커밋 수, `versionName` = `0.<커밋수/10>.<커밋수%10>`, Debug 빌드 접미사 `-debug`).
