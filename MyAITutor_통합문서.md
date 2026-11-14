# MyAITutor 통합 기술 문서

**기준 코드:** HEAD `5bd9189` (커밋 99개, `versionName 0.9.9`)
**최종 갱신:** 2026-11-15
**통합 대상 (4개, 모두 삭제됨):**
- `MyAITutor_Qwen3_Implementation_Plan.md` (Qwen 구현 계획/검증)
- `코드리뷰_전수조사결과.md` (전체 코드 리뷰)
- `MyAITutor_개선안.md` (개선 작업 3종 진행 현황)
- `코드리뷰kilo.md` (정적 코드 조사, Git 추적 131파일 기준)

> [!NOTE]
> 이 문서는 세 문서를 하나로 통합했다. 통합 과정에서 **중복 제거, 오류 정정, 추측성 서술 삭도, 코드 미존재 항목 정리**를 수행했다.
> 정정 상세는 [11장](#11-통합-과정에서-정정한-내용)에 모았다.

---

## 목차

| 장 | 내용 |
|---|---|
| [1장](#1-프로젝트-개요) | 프로젝트 개요 — 모듈, 빌드 환경, 의존성 |
| [2장](#2-소스-파일-목록) | 소스 파일 목록 (실측) |
| [3장](#3-아키텍처) | 아키텍처 — 계층, 설계 패턴, 실행 구조 |
| [4장](#4-ai-추론-계층) | AI 추론 계층 — Gemma / Qwen |
| [5장](#5-모델-배포-및-다운로드) | 모델 배포 정보 및 다운로드·검증 |
| [6장](#6-모델-선택-및-런타임-정책) | 모델 선택·전환·단일 메모리 상주 규칙 |
| [7장](#7-ocr--카메라-계층) | OCR 및 카메라 계층 |
| [8장](#8-ui-계층) | UI 계층 |
| [9장](#9-개선-작업-이력) | 개선 작업 이력 (완료 3건) |
| [10장](#10-qwen-추가-구현-완료도) | Qwen 추가 구현 완료도 |
| [11장](#11-테스트) | 테스트 현황 및 합격 기준 |
| [12장](#12-통합-과정에서-정정한-내용) | 통합 과정에서 정정한 내용 |
| [13장](#13-발견된-문제점-및-개선-후보) | 발견된 문제점 및 개선 후보 |
| [14장](#14-보안-및-성능-고려사항) | 보안 및 성능 고려사항 |
| [15장](#15-검증-범위와-한계) | 검증 범위와 한계 |
| [16장](#16-참고-자료) | 참고 자료 |

---

## 1. 프로젝트 개요

### 1.1 모듈 구성

| 모듈 | 타입 | 경로 |
|---|---|---|
| `:app` | Android App | `app/` |
| `:ppocr-sdk` | Android Library | `ppocr-sdk/` |

### 1.2 빌드 환경

| 항목 | 값 | 비고 |
|---|---|---|
| AGP | 9.5.1 | 루트 `build.gradle.kts`의 플러그인 버전 (`apply false`) |
| Android Library 플러그인 | 9.5.1 | `:ppocr-sdk` |
| Kotlin Compose 플러그인 | 2.4.21 | |
| Gradle Wrapper | 9.6.0 | `gradle-wrapper.properties` |
| Compile SDK | 37 | 앱·라이브러리 공통 |
| Min SDK / Target SDK | 26 / 36 | |
| Java / Kotlin | 17 | |
| ABI | `arm64-v8a`, `x86_64` | 32비트는 LiteRT-LM 미제공으로 제외 |
| `isMinifyEnabled` | `false` | release에서도 비활성 |
| Gradle JVM args | `-Xmx5g`, `-XX:+UseParallelGC`, `file.encoding=UTF-8` | `gradle.properties` |
| 병렬 빌드/캐시 | `org.gradle.parallel=true`, `org.gradle.caching=true` | `org.gradle.tooling.parallel=true` (Gradle 9.4+) |
| 기타 | `android.useAndroidX=true`, `android.nonTransitiveRClass=true`, `kotlin.code.style=official` | |

---

## 0. 작업 진행 상태 요약

아래는 문서에 등재된 문제점·개선 후보 중 현재 진행 상태를 구별하기 위한 표입니다.

| 상태 | 항목 | 문서 위치 | 비고 |
|:---:|---|---|---|
| [완료] | Qwen 엔진 초기화 경로 SHA-256 검증 강제 | 13.2-3 | `isFileVerified()` 추가 및 초기화 경로에서 요구 |
| [완료] | OCR 원문 Logcat 출력을 DEBUG 빌드로 제한 | 13.1-4 | `BuildConfig.DEBUG` 조건부 |
| [완료] | `HomeViewModel.close()` `runBlocking` 제거 (ANR 위험 해소) | 13.1-1 | 별도 daemon 스레드 스코프에서 비동기 해제 |
| [완료] | Gemma 최소 모델 크기 기준 단일 원본화 | 13.1-2 | `MIN_MODEL_BYTES` internal 노출 |
| [완료] | Assistant 레거시 콜백 API 제거 (학년 하드코딩 해소) | 13.2-8 | `analyzeImage`/`askFollowUp`/`collectLegacy` 삭제 |
| [완료] | 컨텍스트 토큰 표기를 선택 모델 기준으로 분기 | 13.3 | `HomeUiState.maxContextTokens` 계산 속성 |
| [완료] | 진행 단계 상세 문구 모델명 분기 | 10.3 | `home_stage_model_detail` %1$s 치환 |
| [완료] | Qwen 2,048 컨텍스트 예산 강제 (입력 축약 + 초과 안내) | 10.3 | `QwenContextBudget` + 12개 단위 테스트 |
| [완료] | Qwen 선택 시 생각 모드 카드 비활성화 | 10.3 | `thinkingSupported` 스위치 |
| [완료] | Qwen 다운로드 시작 시 가용 메모리 확인 | 10.3 | `canStartDownload` + 저장소/메모리 판정 |
| [완료] | Qwen 검증 캐시를 파일 지문(크기·수정시각)에 바인딩 | 5.3 / 13.3 | 파일 교체 시 재검증 |
| [완료] | 설정 화면에 LiteRT-LM 런타임 버전 표시 | 10.3 | `BuildConfig.LITERTLM_VERSION` |
| [완료] | Linux gradlew 버전을 wrapper(9.6.0)와 일치 | 13.3 | `GRADLE_VERSION` 9.3.1 → 9.6.0 |
| [완료] | 크롭 파일명에 UUID 추가 (충돌 방지) | 13.3 | `ImageCropper` |
| [완료] | ppocr-sdk JVM 단위 테스트 도입 (39개) | 13.3 | CTCDecoder·BoxSorter·QuadGeometry·MathUtils·YamlUtils |
| [완료] | 모델 복구 다이얼로그 노출 규칙 테스트 | 11.3 | `isNoModelRecoveryRequired` 3개 테스트 |
| [완료] | THIRD_PARTY_NOTICES 구성요소별 표 재정리 | — | Qwen·LiteRT-LM 추가 |
| [완료] | MyAITutor 통합 기술 문서 추가 | — | 4개 문서 통합 정리 |
| [미진행] | `HomeViewModel` 단일 파일 분리 (1,188줄) | 13.2-7 | 대규모 리팩터링, 별도 승인 필요 |
| [보류] | Gemma 해시 검증 I/O 오류 처리 (현재 상태 유지) | 13.4 | 사용자 결정: 완화(완전 보존), 별도 승인 시 재검토 |
| [미진행] | 실기기 테스트 전 항목 (11.4) | 11.4 | 모든 수정 완료 후 마지막 단계 |
| [보류] | 다크 테마 도입 (도입 계획 없음) | 13.3 | 사용자 결정: 도입 계획 없음, 별도 승인 시 재검토 |
| [미진행] | Qwen 2,048 예산 추정치 실제 토크나이저 검증 | 10.3 | 단위 테스트와 함께 실제 기기 검증 필요 |

---

**버전 관리:** `versionCode` = Git 커밋 수, `versionName` = `MAJOR.MINOR.PATCH` (`커밋/100` . `(커밋%100)/10` . `커밋%10`).
`.git`이 없는 환경에서 빌드하면 `gitCommitCount`가 1로 폴백한다. 현재 HEAD는 커밋 99개이므로 `0.9.9`다.

### 1.3 CI (`.github/workflows/android.yml`)

| 항목 | 값 |
|---|---|
| 트리거 | `push`/`pull_request` on `master`, `main` |
| 러너 | `ubuntu-latest` |
| JDK | 17 (Temurin) |
| Gradle 설정 | `gradle/actions/setup-gradle@v4` |
| 실행 작업 | `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug` (모두 `--stacktrace`) |
| 산출물 | `app/build/test-results/`, `app/build/reports/lint-results-debug.html` |

> [!NOTE]
> CI는 `chmod +x gradlew`를 선행한다. 단 Linux `gradlew` 스크립트는 자체 부트스트랩으로 Gradle **9.3.1**을 다운로드하는 반면 `gradle-wrapper.properties`는 **9.6.0**이다 (13.3 참조).

### 1.4 주요 의존성

| 라이브러리 | 버전 | 용도 |
|---|---|---|
| `androidx.compose:compose-bom` | 2026.09.00 | Jetpack Compose |
| `androidx.camera:camera-*` | 1.6.2 | 카메라 프리뷰·촬영 |
| `com.google.ai.edge.litertlm:litertlm-android` | 0.18.0 | 온디바이스 LLM 추론 |
| `io.github.huarangmeng:latex-renderer` | 1.7.2 | LaTeX 수식 렌더링 |
| `com.microsoft.onnxruntime:onnxruntime-android` | 1.21.1 | OCR 추론 (ppocr-sdk) |
| `org.opencv:opencv` | 4.13.0 | 이미지 전처리 (ppocr-sdk) |

---

## 2. 소스 파일 목록

실측 기준 Kotlin 파일 **97개** = 앱 프로덕션 56 + 앱 테스트 13 + ppocr-sdk 프로덕션 26 + ppocr-sdk 계측 테스트 2.

### 2.1 앱 프로덕션 소스 (56개)

| 패키지 | 파일 | 라인 | 역할 |
|---|---|---|---|
| 루트 | `MainActivity.kt` | 35 | 진입점, Edge-to-Edge |
| 루트 | `MyAITutorApplication.kt` | 146 | Application, PaddleOCR 초기화, `OcrTextRecognizer` 구현 |
| `ai` | `Assistant.kt` | 66 | Assistant 인터페이스·스트림 이벤트 |
| `ai` | `GemmaAssistant.kt` | 375 | Gemma 4 E4B LiteRT-LM 엔진 |
| `ai` | `QwenAssistant.kt` | 318 | Qwen3-4B LiteRT-LM 엔진 |
| `ai` | `PromptTemplates.kt` | 348 | 프롬프트·안전 규칙·시스템 지시문·토큰 상수 |
| `ai` | `Qwen3ChatTemplate.kt` | 76 | Qwen ChatML Jinja 템플릿 + Kotlin 렌더러 |
| `camera` | `CameraPreview.kt` | 202 | CameraX 프리뷰, `ImageCapture` 빌드 |
| `camera` | `ExifImageTransform.kt` | 33 | EXIF orientation 보정 |
| `camera` | `ImageCropper.kt` | 65 | 선택 영역 크롭 |
| `camera` | `ImageNormalizer.kt` | 62 | OCR 입력 정규화 (장변 2400px, JPEG 92%) |
| `camera` | `ImageSelection.kt` | 40 | 선택 영역 데이터 클래스 |
| `model` | `EngineRuntime.kt` | 42 | 런타임 계약 인터페이스 + `ModelTransitionException` |
| `model` | `ModelDownloadManager.kt` | 282 | Gemma 다운로드 관리자 |
| `model` | `ModelDownloadRepository.kt` | 18 | 다운로드 저장소 인터페이스 |
| `model` | `ModelRuntimeCoordinator.kt` | 186 | 전환 직렬화·해제·2단계 복구 |
| `model` | `ModelSelectionRepository.kt` | 101 | `AppModel`·`ModelSelection`·선택 저장소 |
| `model` | `QwenModelDownloadManager.kt` | 257 | Qwen 다운로드 관리자 |
| `model` | `QwenModelVerification.kt` | 74 | Qwen READY 판정 순수 로직 |
| `model` | `QwenRuntimeState.kt` | 48 | `ModelRuntimeState`·`RuntimeStatus` 불변식 |
| `ocr` | `OcrBlock.kt` | 25 | OCR 블록 데이터 클래스 |
| `ocr` | `OcrImageEnhancer.kt` | 463 | 뒤틀림 보정·대비·샤프닝 |
| `ocr` | `OcrTextPostProcessor.kt` | 230 | 후처리 (정규화·중복 제거·정렬) |
| `ocr` | `OcrTextRecognizer.kt` | 18 | OCR 인식기 인터페이스 |
| `tutor` | `LlmInferenceEngine.kt` | 246 | 추론 엔진 인터페이스·Gemma 어댑터·`inferenceOptionsFor` |
| `tutor` | `QwenTutorEngine.kt` | 72 | Qwen 튜터 어댑터 |
| `tutor` | `SwitchableTutorEngine.kt` | 51 | 활성 모델 위임 어댑터 |
| `tutor` | `TutorResultScreen.kt` | 596 | 답변 결과 화면 |
| `tutor` | `TutorStreamParser.kt` | 415 | 스트림 파서·특수 토큰 필터 |
| `ui` | `FocusAiApp.kt` | 346 | 루트 컴포저블·내비게이션·Wi-Fi 확인 |
| `ui/camera` | `CameraRoute.kt` | 75 | 카메라 라우트 |
| `ui/camera` | `CameraScreen.kt` | 378 | 카메라 화면·영역 선택 (미리보기 1600px) |
| `ui/components` | `ChatComponents.kt` | 219 | 채팅 버블·타이핑 인디케이터 |
| `ui/components` | `InfoCard.kt` | 40 | 정보 카드 |
| `ui/home` | `CaptureFile.kt` | 20 | 캡처 파일 유틸 |
| `ui/home` | `ChatMessage.kt` | 20 | 채팅 메시지 데이터 |
| `ui/home` | `ComposerBar.kt` | 164 | 하단 입력 바 |
| `ui/home` | `FollowUpChips.kt` | 100 | 후속 질문 칩 |
| `ui/home` | `HomeContent.kt` | 24 | 과목 목록·후속 선택지 상수 |
| `ui/home` | `HomeScreen.kt` | 472 | 메인 화면 |
| `ui/home` | `HomeUiState.kt` | 108 | 홈 UI 상태 |
| `ui/home` | `HomeViewModel.kt` | 1188 | 홈 비즈니스 로직 |
| `ui/home` | `HomeViewModelHost.kt` | 44 | ViewModel 호스트, 코디네이터 배선 |
| `ui/home` | `NoModelRecoveryDialog.kt` | 64 | NO_MODEL_LOADED 복구 다이얼로그 |
| `ui/home` | `StatusBanner.kt` | 51 | 상태 배너 |
| `ui/home` | `StringProvider.kt` | 10 | 문자열 조회 인터페이스 |
| `ui/loading` | `ModelLoadingPhase.kt` | 58 | 로딩 단계 enum·판정 함수 |
| `ui/loading` | `ModelLoadingScreen.kt` | 492 | 시작 로딩 화면 |
| `ui/settings` | `AppSettingsRepository.kt` | 34 | 설정 저장소 (Thinking 모드) |
| `ui/settings` | `ModelSelectionCard.kt` | 471 | 모델 선택·다운로드 카드 |
| `ui/settings` | `SettingsScreen.kt` | 402 | 설정 화면 |
| `ui/text` | `AnswerMarkdown.kt` | 65 | 마크다운 변환 |
| `ui/text` | `AnswerSegments.kt` | 96 | 답변 세그먼트 파싱 |
| `ui/text` | `AssistantAnswer.kt` | 86 | 답변 렌더링 |
| `ui/text` | `FormulaText.kt` | 70 | 인라인 LaTeX |
| `ui/theme` | `Theme.kt` | 164 | Material3 테마 |

### 2.2 앱 테스트 소스 (13개 파일, 149개 테스트)

| 파일 | 테스트 수 | 대상 |
|---|---|---|
| `ui/home/HomeViewModelTest.kt` | 44 | HomeViewModel |
| `model/QwenModelVerificationTest.kt` | 18 | `QwenModelVerification.decide`, 상수 |
| `tutor/SwitchableTutorEngineTest.kt` | 13 | SwitchableTutorEngine 위임 규칙 |
| `tutor/TutorStreamParserTest.kt` | 13 | 스트림 파서 |
| `model/ModelRuntimeStateTest.kt` | 11 | `RuntimeStatus` 불변식 |
| `model/ModelSelectionRepositoryTest.kt` | 10 | 선택 저장소·정규화 |
| `ui/loading/ModelLoadingPhaseTest.kt` | 10 | `resolveModelLoadingPhase` |
| `ui/text/AnswerSegmentsTest.kt` | 9 | 수식 분리·SUGGESTIONS 파싱 |
| `ocr/OcrTextPostProcessorTest.kt` | 7 | OCR 후처리 |
| `model/ModelDownloadSnapshotTest.kt` | 5 | 다운로드 스냅샷·Gemma URL |
| `ui/text/AnswerMarkdownTest.kt` | 4 | 마크다운 변환 |
| `camera/ImageSelectionTest.kt` | 3 | 선택 영역 정규화 |
| `ai/Qwen3ChatTemplateTest.kt` | 2 | Chat Template 렌더링 |

> [!IMPORTANT]
> 149개는 소스의 `@Test` 정적 계수다. **통과 여부는 이 문서에서 검증하지 않았다.** (14장 참조)

### 2.3 ppocr-sdk 소스 (26개 프로덕션 + 2개 계측 테스트)

| 패키지 | 파일 |
|---|---|
| `com.paddle.ocr` | `PaddleOCR.kt`, `EngineConfig.kt`, `PaddleOCRConfig.kt` |
| `engine` | `DetectionEngine.kt`, `OCREngine.kt`, `OCREngineResult.kt`, `ORTSessionManager.kt`, `RecognitionEngine.kt` |
| `model` | `ModelConfig.kt`, `OCRBox.kt`, `OCRError.kt`, `OCRResult.kt`, `OCRRunResult.kt` |
| `postprocess` | `BoxSorter.kt`, `CTCDecoder.kt`, `DBPostProcessor.kt`, `PolygonUnclip.kt`, `QuadGeometry.kt`, `QuadTextCrop.kt` |
| `preprocess` | `DetPreprocessor.kt`, `RecPreprocessor.kt` |
| `util` | `BitmapUtils.kt`, `ImageUtils.kt`, `MathUtils.kt`, `OpenCVUtils.kt`, `YamlUtils.kt` |
| `androidTest` | `OCRBenchmarkTest.kt`, `BenchmarkFixtures.kt` |

**ppocr-sdk에는 JVM 단위 테스트(`src/test`)가 없다.** `DetPreprocessor`, `RecPreprocessor`, `CTCDecoder`, `DBPostProcessor`, `BoxSorter` 등 순수 로직이 다수 `object`로 구현되어 있어 단위 테스트가 가능한데 검증되지 않고 있다.

### 2.4 에셋 및 리소스

| 항목 | 내용 |
|---|---|
| `ppocr-sdk/src/main/assets/models` | `det/inference.onnx`(탐지 모델), `rec/inference.onnx`(인식 모델), `rec/inference.yml`(인식 설정, 11,989줄) |
| `app/src/main/res` | `drawable`, `drawable-nodpi`, `values`, `values-v27`, `xml/data_extraction_rules.xml` |
| `strings.xml` | 한국어 UI 문자열의 단일 원본 (코드에서 `stringResource` / `appContext.getString`으로 참조) |

**Git 추적 파일 수:** `git ls-files` 기준 131개 (Kotlin 97개 + 리소스/Gradle/Manifest/에셋 등 비 Kotlin 34개).

### 2.5 매니페스트 요약

| 항목 | 값 |
|---|---|
| 권한 | `CAMERA`, `INTERNET`, `ACCESS_NETWORK_STATE` |
| `uses-feature` | `android.hardware.camera.any` (`required="true"`) |
| `allowBackup` / `fullBackupContent` | `false` / `false` |
| `dataExtractionRules` | `@xml/data_extraction_rules` |
| `usesCleartextTraffic` | `false` |
| 네이티브 라이브러리 | `libvndksupport.so`, `libOpenCL.so` (모두 `required="false"`) |
| `MainActivity` | `exported="true"`, LAUNCHER, `configChanges` 다수, `windowSoftInputMode="adjustResize"` |

**API 키/토큰 하드코딩 없음.** 모델 다운로드 URL만 공개 Hugging Face 경로다. Keystore/서명 파일은 저장소에 없다.

---

## 3. 아키텍처

### 3.1 계층 구조

```
UI Layer (Jetpack Compose)
  FocusAiApp → ModelLoadingScreen / HomeScreen / SettingsScreen / CameraScreen / TutorResultScreen
        ↓
ViewModel Layer
  HomeViewModelHost (AndroidViewModel) → HomeViewModel (HomeUiState 단일 상태)
        ↓
Runtime / Model Layer
  ModelRuntimeCoordinator (EngineRuntime 구현, 전환 직렬화)
  SwitchableTutorEngine (활성 엔진 위임)
  ModelDownloadManager / QwenModelDownloadManager
  ModelSelectionRepository / AppSettingsRepository
        ↓
AI Inference Layer
  RealGemmaTutorEngine → GemmaAssistant
  QwenTutorEngine → QwenAssistant
        ↓
OCR Layer
  MyAITutorApplication (OcrTextRecognizer) → OcrImageEnhancer → PaddleOCR(:ppocr-sdk) → OcrTextPostProcessor
        ↓
Camera Layer
  CameraPreview (CameraX) → ImageCropper → ImageNormalizer
```

### 3.2 주요 설계 패턴

1. **단일 엔진 상주** — LLM Engine/Conversation/가중치는 항상 최대 1개. 파일은 2개까지 디스크에 보관 가능.
2. **Mutex 직렬화** — 엔진 생성·추론·전환을 Mutex로 직렬화. `QwenAssistant`는 단일 스레드 executor + Mutex 이중 보호.
3. **StateFlow 단방향** — `HomeUiState` 단일 데이터 클래스로 UI 상태 관리.
4. **Repository 패턴** — 다운로드·선택값·설정을 인터페이스로 추상화해 테스트 대역 주입.
5. **Adapter 패턴** — `GemmaAssistant`/`QwenAssistant`를 `LlmInferenceEngine`로 래핑, `SwitchableTutorEngine`이 현재 활성 엔진에 위임.
6. **테스트 seam** — `EngineRuntime` 인터페이스로 실제 모델 초기화 없이 전환 규칙을 JVM 테스트.

### 3.3 모델 전환 순서

```text
설정 화면에서 모델 선택
        ↓
ModelRuntimeCoordinator.switchTo()  [mutex 직렬화]
  0. 대상 모델 파일 READY 확인 (미리 setSelectedModel에서 차단)
  1. TRANSITIONING 전이, 활성 엔진 수 0으로
  2. cancelGeneration() → awaitGenerationStopped(15s)
  3. release() — Conversation.close → Engine.close
  4. activeEngine = null 확인
        ↓
  5. 새 Engine 생성 → preWarm() 성공 확인
        ↓
  6. ACTIVE 전이 (activeEngineCount = 1), 선택값 저장
  7. 실패 시 → 자동 복구(이전 모델 재로드) → 실패 시 NO_MODEL_LOADED
```

---

## 4. AI 추론 계층

### 4.1 두 엔진 비교

| 항목 | Gemma 4 E4B-IT | Qwen3-4B-Instruct-2507 |
|---|---|---|
| 역할 | 기본 엔진, 자유 대화 + 학습 | 학습 모드 전용 |
| Backend | GPU 우선 → CPU 폴백 | GPU 우선 → CPU 폴백 (**조건부**) |
| CPU 폴백 조건 | 제한 없음 | `availMem >= 5,368,709,120` bytes (5GB) |
| `maxNumTokens` | `MAX_CONTEXT_TOKENS` = 4,096 | `QWEN_MAX_CONTEXT_TOKENS` = 2,048 |
| 대화 리셋 임계값 | `MAX_CONVERSATION_TOKENS` = 3,072 (계산식) | `QWEN_MAX_CONVERSATION_TOKENS` = 1,536 |
| 기본 출력 상한 | `DEFAULT_MAX_OUTPUT_TOKENS` = 1,024 | `QWEN_DEFAULT_OUTPUT_TOKENS` = 512 |
| Thinking | 사용자 설정 기반 (`thinkingEnabled` 토글) | **항상 false** (명시적 `ThinkingConfig(false)`) |
| Sampler | topK=20, topP=0.9, temperature=0.35 | topK=20, topP=0.8, temperature=0.7 |
| Chat Template | 모델 메타데이터 기본값 | `Qwen3ChatTemplate.TEMPLATE` 명시 지정 |
| visionBackend | `Backend.GPU()` / `Backend.CPU()` | 미지정 (텍스트 전용) |
| 자유 대화 프롬프트 | `ChatSystemInstruction`, `ChatSafetyInstruction` | **사용하지 않음** |

> [!IMPORTANT]
> **Gemma 보존 원칙.** `ModelDownloadManager.kt`와 `RealGemmaTutorEngine` 본문은 수정하지 않는다.
> 허용된 Gemma 변경은 다음 4개뿐이며, 모두 실기기 검증된 확정값이다.
> 1. `GemmaAssistant`의 `EngineConfig.maxNumTokens = MAX_CONTEXT_TOKENS` 추가 (GPU/CPU 양쪽)
> 2. `PromptTemplates.MAX_CONVERSATION_TOKENS` 계산식화 (4,096 − 1,024 = 3,072)
> 3. `TutorGrade.usesThinking` 제거 (학년 연동 → 사용자 설정)
> 4. `inferenceOptionsFor(isFollowUp, thinkingEnabled)` 시그니처 변경

### 4.2 공통 수명주기 규정

`google-ai-edge/litert-samples`의 `skills/android/litert-lm/SKILL.md` 규정. 두 Assistant 모두 충족한다.

| 규정 | 구현 |
|---|---|
| `Engine`·`Conversation`은 `AutoCloseable`, 각 1회만 close | `isClosed`/`isReleased` 멱등 보장 |
| Conversation을 Engine보다 먼저 close | `release()`에서 순서 고정 |
| close 후 필드를 `null`로 설정 | |
| 스트리밍 중 Engine close 금지 — `cancelProcess()` 후 Flow 종료 대기 | `activeCompletion`(`CompletableDeferred`) + `awaitGenerationStopped` |
| `initialize()`가 던진 Engine은 close하지 않는다 | GPU 실패 시 `initialized` 플래그로 분기 |
| 단일 스레드 executor가 독점 | `focus-ai-gemma-runtime` / `focus-ai-qwen-runtime` |
| `initialize()`는 백그라운드 스레드 | `runtimeDispatcher` |
| close는 자체 scope에서 동기 실행 | `HomeViewModel.close()`에서 `runBlocking` |
| 모델 파일은 앱 외부 경로 절대 경로 | `getExternalFilesDir(DIRECTORY_DOWNLOADS)` |
| GPU 백엔드 매니페스트 2항목 | `libvndksupport.so`, `libOpenCL.so` (`required="false"`) |

> [!NOTE]
> minP는 Qwen 공식 문서의 권장값이나 LiteRT-LM 0.18.0 Kotlin `SamplerConfig`에 필드가 없어 적용하지 않는다. `topP=0.8`으로 대체한다. 0.19.0+에서 필드 추가 시 재검토한다.

### 4.3 Qwen Chat Template

**배경:** LiteRT-LM 0.18.0부터 `message.content`가 문자열이 아니라 typed parts 목록으로 전달될 수 있다. 대상 Qwen 파일의 기본 템플릿은 문자열만 읽어 사용자 질문이 빈 턴으로 렌더링되고, 오류 없이 엉뚱한 답이 나올 수 있다.

**해결:** 별도 모델 변환 없이 `ConversationConfig(chatTemplate = Qwen3ChatTemplate.TEMPLATE)`로 ChatML 호환 Jinja 템플릿을 지정한다.

템플릿 요구사항과 구현 상태:

| 요구사항 | 상태 |
|---|---|
| 1. `content`가 문자열이면 그대로 처리 | 충족 |
| 2. typed parts 목록이면 각 part의 `text`를 순서대로 연결 | 충족 |
| 3. system/user/assistant 역할 보존 | 충족 |
| 4. ChatML 경계 토큰 및 assistant 응답 시작 형식 | 충족 |
| 5. 지원하지 않는 content type은 명시적 처리 또는 차단 | 부분 — `renderContent`가 `Content.Text` 객체를 빈 문자열로 처리 |
| 6. 다중 텍스트 조각 병합 및 systemInstruction 선두 배치 테스트 | 미작성 |

**필수 회귀 테스트:** `"이 문제를 단계별로 설명해줘"`가 렌더링 후 user 본문에 남아야 한다. `Qwen3ChatTemplateTest` 2개가 string/typed parts 두 경로를 검증한다.

> [!WARNING]
> `litert-samples` 저장소에 Qwen 전용 Chat Template 코드가 존재하지 않는다. `TEMPLATE`은 직접 작성한 것이다.
> 현재 테스트는 Kotlin 보조 렌더러(`render()`)만 검증하며, **실제 Jinja 엔진을 통과한 렌더링은 검증하지 않았다.**

---

## 5. 모델 배포 및 다운로드

### 5.1 배포 정보

| 항목 | Gemma 4 E4B-IT | Qwen3-4B-Instruct-2507 |
|---|---|---|
| Hugging Face | `litert-community/gemma-4-E4B-it-litert-lm` | `litert-community/Qwen3-4B-Instruct-2507` |
| 파일명 | `gemma-4-E4B-it.litertlm` | `qwen3_4b_instruct_2507_mixed_int4.litertlm` |
| Revision | `f254b5ba6e36f7d89cc7892e268e9877bc20f0a0` | `6ee6014fa9193ea4dbb309f3305d6ee0d78db5a0` |
| 정확한 크기 | 3,659,530,240 bytes (약 3.66GB) | 2,659,057,664 bytes (약 2.66GB) |
| 최소 허용 크기 | `MIN_MODEL_BYTES` = 3,500,000,000 | 정확한 크기 일치 필수 |
| SHA-256 | `0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0` | `9e48b165836256f5344d9d044930607b9c47f6ef34e27f82e96881664f3ba2fd` |
| 컨텍스트 | 32k 기본 (앱에서 4,096으로 제한) | 2,048 |
| 라이선스 | Apache-2.0 | Apache-2.0 |
| SharedPreferences | `focus_ai_model_manager` | `focus_ai_qwen_model_manager` |

두 관리자는 저장 경로(`getExternalFilesDir(DIRECTORY_DOWNLOADS)`)를 공유하지만 파일명과 SharedPreferences 키가 완전히 분리되어 있어 상태가 섞이지 않는다.

### 5.2 공통 다운로드 동작

- Android `DownloadManager`, Wi-Fi 전용 (`NETWORK_WIFI`)
- 저장 공간 확인: 예상 크기 + 600,000,000 bytes 여유
- 폴링: 전환 중 1초, 그 외 5초
- 상태 7종: `NOT_INSTALLED`, `QUEUED`, `DOWNLOADING`, `PAUSED`, `VERIFYING`, `READY`, `FAILED`
- 모델 삭제 시 다운로드 ID와 검증 캐시도 정리

### 5.3 READY 판정 조건 (Qwen)

`QwenModelVerification.decide()`가 순수 로직으로 판정하고 `QwenModelDownloadManager`가 상태로 매핑한다. READY가 되려면 **모든** 조건을 만족해야 한다.

1. 이전 다운로드가 완료로 기록되어 있다 (`KEY_COMPLETED`)
2. 파일이 존재한다
3. 파일 길이가 `2,659,057,664` bytes와 **정확히** 일치한다
4. `KEY_HASH_MISMATCH`가 기록되어 있지 않다
5. `KEY_HASH_VERIFIED`가 true이다 (또는 SHA 검증이 불필요한 경우)

| 판정 | 조건 | UI 상태 |
|---|---|---|
| `READY` | 위 5개 모두 충족 | 설치 완료 |
| `NEEDS_HASH_VERIFICATION` | 크기는 맞지만 해시 미검증 | 검증 중 |
| `MISMATCH` | 크기 또는 해시 불일치 | 오류 (재다운로드 안내) |
| `NOT_INSTALLED` | 완료 기록 없음, 파일 없음, 또는 크기 불일치 | 미설치 / 오류 |

해시 계산은 `Dispatchers.IO`에서 1MB 버퍼로 스트리밍한다.

> [!WARNING]
> 검증 캐시는 `KEY_HASH_VERIFIED` / `KEY_HASH_MISMATCH` **불리언만** 저장한다.
> revision·파일 크기·파일 fingerprint(수정시각 등)와 묶지 않아, 다른 파일이 같은 경로에 놓이면 이전 검증 결과가 재사용될 수 있다. Gemma 쪽도 동일한 구조다.

### 5.4 모델 카드 실측 필요 항목 (Qwen)

아래는 모델 카드 기준값이며 **실제 다운로드 파일과의 일치 여부는 미검증**이다.

| 항목 | 모델 카드 값 | 비고 |
|---|---|---|
| 컨텍스트 | 2,048 tokens | `max sequence length` 별도 명시 없음 |
| 외부 가중치 | 없음 | `.litertlm` 1개만 존재 |
| LiteRT-LM 최소 버전 | 명시 없음 | 벤치마크는 v0.13.1 / v0.16.0 기준 |
| GPU 메모리 | Galaxy S26 약 1.53GB / vivo V2502A 4.72GB / TECNO LJ9 4.90GB | **기기 편차 매우 큼** |
| CPU 메모리 | Galaxy S26 XNNPACK 4.58~6.09GB peak RSS | |
| 최소 RAM | 명시 없음 | 실측 피크로 판단 필요 |
| GPU 엔진 초기화 | 프로세스당 약 16~21초 | 첫 응답 UX에 영향 |
| `litertlm_manifest.json` | 저장소에 없음 (404) | |

---

## 6. 모델 선택 및 런타임 정책

### 6.1 기본값과 선택 정책

1. 새 설치에서 선택값이 없으면 `GEMMA`를 기본값으로 사용한다.
2. 기존 사용자도 선택값이 없으면 Gemma로 시작한다. 기존 다운로드 파일과 해시 검증 캐시를 그대로 인정한다.
3. 앱 업데이트 시 Qwen을 자동 선택하거나 자동 다운로드하지 않는다.
4. Qwen은 사용자가 설정에서 선택하고 다운로드·검증을 완료한 뒤에만 활성화한다.
5. Qwen 다운로드 중에도 Gemma는 계속 사용할 수 있다.
6. 마지막으로 **성공한** 모델 선택값을 저장한다. 로드 실패 시 이전 성공값을 보존한다.

구현: `PrefsModelSelectionRepository`가 `focus_ai_model_selection` SharedPreferences에 `selected_model`, `chat_mode`를 저장한다.

### 6.2 모드 정합 규칙 (Qwen 자유 대화 미지원)

**Qwen은 학습 모드 전용이다. 자유 대화 모드는 Gemma에서만 동작한다.**

근거:
1. **토큰 예산** — 2,048 한도에서 자유 대화 지시문이 대부분을 consumed한다.
2. **제품 정책** — 자유 대화의 반말 페르소나는 Gemma에서 검증된 동작이며 Qwen에서 동일 품질을 낼 근거가 없다.

| 상황 | 동작 | 구현 지점 |
|---|---|---|
| Qwen 활성 상태에서 자유 대화 ON 시도 | **차단** + 안내. 자동으로 Gemma를 로드하지 않는다 | `HomeViewModel.setChatMode` 가드 |
| 자유 대화 사용 중 Qwen 선택 시도 | **차단** (보류) + 안내. 자유 대화를 끄면 허용 | `HomeViewModel.setSelectedModel` 가드 |
| 저장값이 Qwen + 자유 대화 ON으로 어긋나 있음 | 앱 시작/읽기 시 자유 대화 **OFF** + Qwen으로 정규화 후 즉시 재저장 | `PrefsModelSelectionRepository.get()` |

> [!IMPORTANT]
> `chatMode`와 선택 모델은 **항상 정합 상태**여야 한다. 정규화는 `PrefsModelSelectionRepository.get()` 단일 지점에서 담당한다.

이 제약으로 Qwen이 `ChatSystemInstruction`·`ChatSafetyInstruction`을 전혀 사용하지 않아 2,048 예산 압박이 완화된다. 학습 모드 지시문만 사용한다.

### 6.3 단일 메모리 상주 불변식

`RuntimeStatus.init`에서 강제한다.

```kotlin
require(activeEngineCount in 0..1) { "activeEngineCount must be 0 or 1" }
require(state != ACTIVE || activeEngineCount == 1) { ... }
require(state != TRANSITIONING || activeEngineCount == 0) { ... }
```

상태 전이: `IDLE`/`ACTIVE` → `TRANSITIONING` → `ACTIVE` 또는 `NO_MODEL_LOADED`.

### 6.4 2단계 복구 정책

| 단계 | 동작 | 결과 |
|---|---|---|
| 1단계 (자동) | Qwen 초기화 실패 시 이전 활성 모델(Gemma) 재로드 | 성공 시 ACTIVE + "Qwen 모델을 불러오지 못했습니다. Gemma로 돌아왔습니다." |
| 2단계 (사용자 선택) | Gemma 재로드도 실패 | `NO_MODEL_LOADED` + 3선택지 다이얼로그 |

2단계 선택지:

| 선택지 | 동작 | 실패 시 |
|---|---|---|
| **다시 시도** | `retryModelLoad()` — 현재 선택 모델 재로드 | `NO_MODEL_LOADED` 유지 |
| **Gemma로 돌아가기** | `fallbackToGemma()` | Gemma 파일이 없으면 `error_no_model_loaded` 안내 |
| **나중에** | `dismissNoModelRecovery()` — 모델 없이 앱 계속 사용 | AI 기능 비활성 |

실패 원인은 `failureMessage`로 전달되며 **OCR 원문·사용자 질문을 담지 않는다.**

---

## 7. OCR 및 카메라 계층

### 7.1 카메라

| 항목 | 값 | 위치 |
|---|---|---|
| 라이브러리 | CameraX 1.6.2 | |
| 캡처 목표 해상도 | 2560×1920 (4:3) | `CameraPreview.CAPTURE_RESOLUTION` |
| 해상도 전략 | `FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER` | |
| 캡처 모드 | `CAPTURE_MODE_MAXIMIZE_QUALITY` (ZSL 비활성) | OCR 인식률 우선 |
| JPEG 품질 | 95% | 촬영 |
| 미리보기 최대 장변 | 1600px | `CameraScreen.PREVIEW_MAX_SIDE_PX` (OOM 방지) |
| 시도 순서 | 후면 고해상도 → 후면 기본 → 전면 기본 | |

선택 영역은 드래그로 사각형을 지정하며, 크롭 후 OCR에 전달한다. 선택하지 않으면 전체 이미지를 처리한다.

### 7.2 OCR 파이프라인

```text
촬영/선택 이미지
  → ImageNormalizer      : EXIF 보정, 장변 2400px 다운스케일, JPEG 92%
  → OcrImageEnhancer     : 뒤틀림 보정, 대비 스트레칭, 샤프닝 (최대 500만 픽셀)
  → PaddleOCR (:ppocr-sdk)
       DetPreprocessor   : 리사이즈 (detMaxSideLimit 4000, 32의 배수 정렬)
       DetectionEngine   : DB + PolygonUnclip (ONNX Runtime)
       QuadTextCrop      : 사각형 텍스트 크롭
       RecPreprocessor   : 높이 48 고정 리사이즈
       RecognitionEngine : CRNN + CTCDecoder
       BoxSorter         : 행 단위 정렬
  → OcrTextCandidate 목록 (MyAITutorApplication가 구성)
  → OcrTextPostProcessor : 유니코드 정규화, 숫자 혼동 보정(O/l), 저신뢰 블록 제거, 중복 제거, 좌표 정렬
  → OcrBlock 목록 (text·type·bounds·confidence·readingOrder·sourceFile)
  → formatOcrBlocksForPrompt : [수식]/[표]/[이미지] 태그 부여
  → LLM에 텍스트만 전달
```

> [!IMPORTANT]
> 원본 이미지나 Bitmap 객체를 LLM에 전달하지 않는다. Gemma와 Qwen 모두 텍스트 전용이다.

### 7.3 OCR 결과 구조

`OcrTextRecognizer.recognize()`는 `List<String>`이 아니라 **`List<OcrBlock>`** 을 반환한다. 구조화 정보는 다음과 같다.

```kotlin
data class OcrBlock(
    val text: String,
    val type: OcrBlockType,
    val bounds: Rect?,          // Android framework 타입 — JVM 단위 테스트에서 사용 불가
    val confidence: Float?,
    val readingOrder: Int?,
    val sourceFile: String
) {
    enum class OcrBlockType { TEXT, FORMULA, TABLE, IMAGE, UNKNOWN }
}
```

| 정보 | 보존 여부 | 비고 |
|---|---|---|
| 텍스트 | ✅ | |
| 유형 | ✅ | `inferBlockType()`이 표/수식 패턴 감지 |
| 좌표 | ✅ | 단 `Rect`는 Android 타입이라 JVM 테스트 불가. `OcrTextPostProcessor`는 순수 Kotlin 좌표(`left/top/right/bottom/hasGeometry`)를 쓰는 별도 `OcrTextCandidate`를 둬 우회 |
| 신뢰도 | ✅ | SDK가 제공하지 않으면 `null` |
| 읽기 순서 | ✅ | 후처리 정렬 후 `mapIndexed`로 재부여 |
| 표 구조 | 부분 | 파이프/탭/3공백 이상 다단 셀 탐지만. 셀 단위 구조는 보존하지 않는다 |

**유형 분류 규칙** (`MyAITutorApplication.inferBlockType`):

| 조건 | 분류 |
|---|---|
| 파이프 2개 이상, `+-` 포함, 탭 포함, 3공백 이상 3단 이상 | `TABLE` |
| 수식 기호(`∫∑∏√±×÷≠≤≥∞∂∇∆∈∉⊂⊃∪∩∀∃∴∵∝`) 포함 | `FORMULA` |
| `\d+/\d+`, `x^\d+`, `\d+\.\d+[eE][+-]?\d+` 패턴 | `FORMULA` |
| 그 외 | `TEXT` |

**프롬프트 포맷** (`formatOcrBlocksForPrompt`): `[수식] …`, `[표]\n…`, `[이미지] …` 태그를 부여하고 일반 텍스트는 그대로 둔다.

### 7.4 이미지 파일 수명주기

`analyzeImage()`는 원본과 임시 파일을 분리해 관리한다.

| 시점 | 동작 |
|---|---|
| 분석 시작 | 촬영 원본은 **보존** (재분석용) |
| `finally` | 전처리 정규화 파일·크롭 결과 등 **임시 파일만 삭제**. 단 `preprocessedImage`가 가리키는 파일은 보존 |
| 새 사진 촬영 (`confirmCapture`) | 이전 `capturedImage`가 새 이미지와 다르면 삭제 |
| 과목 변경 (`resetForNewSubject`) | 진행 중 추론 취소 후 `capturedImage` 삭제 |
| 모델 삭제 / 캐시 정리 | `clearCache()`가 `focus_` 접두·`.jpg` 파일 정리 |

**UI 상태 복구 우선 원칙:** `finally`에서 `recoverUiAfterGeneration()`을 호출해 `isAnalyzing`·진행 단계를 먼저 복구하고, 토큰 수 조회는 그 안에서 `try/catch`로 감싸 실패가 UI 복구를 막지 않게 한다. 모든 결과 반영은 `currentGeneration == generationId` 검사로 보호해 이전 작업의 늦은 결과가 새 작업을 덮어쓰지 못하게 한다.

> [!WARNING]
> `analyzeImage` 경로에서 `ocrLogger(ocrText)`가 OCR 문제 전문을 Logcat에 기록한다 (L705, `// TODO` 주석). 13.1-4 참조.

---

## 8. UI 계층

### 8.1 화면 구성

| 화면 | 역할 |
|---|---|
| `ModelLoadingScreen` | 시작 시 다운로드·검증·워밍업 상태. `resolveModelLoadingPhase`로 단계 판정 |
| `HomeScreen` | 메인 학습 화면 (과목·학년·채팅·촬영). 진행 단계 제목이 선택 모델명으로 분기 |
| `SettingsScreen` | 모델 선택 카드, Thinking 모드, 캐시 정리, 라이선스, 버전·백엔드 정보 |
| `CameraScreen` | 촬영, 영역 선택, 확인/재촬영 |
| `TutorResultScreen` | 스트리밍 답변 표시, 후속 질문 칩 |

### 8.2 상태 관리

- `HomeUiState` 단일 데이터 클래스, `StateFlow`로 전달
- 파생 속성: `isModelBusy`(TRANSITIONING 또는 NO_MODEL_LOADED), `isFreeChatAvailable`, `isNoModelRecoveryRequired`
- 모델 관련 필드: `selectedModel`, `selectedModelSnapshot`, `qwenSnapshot`, `runtimeState`, `transitionFailure`, `freeChatBlockedReason`, `modelSelectionBlockedReason`, `isNoModelDismissed`

### 8.3 모델 선택 카드 (`ModelSelectionCard`)

- Gemma/Qwen 라디오 카드 (`Role.RadioButton`, `selectableGroup`)
- 모델별 이름·설명·용량·다운로드 상태·진행률
- 다운로드/삭제 버튼, 삭제 확인 다이얼로그 (모델별 메시지 분기)
- 전환 중 또는 선택 보류 시 비활성화 + 사유 표시
- "한 번에 하나의 AI 모델만 메모리에 로드됩니다" 안내 (`model_single_load_notice`)

### 8.4 모델별 표기 정책

| 영역 | Gemma | Qwen |
|---|---|---|
| 모델 설명 | "자유 대화 지원 · 학습 모드 지원" | "학습 모드 전용" |
| 학습 안내 | "Gemma 4 E4B-IT (자유대화+학습 모델)" | "Qwen3-4B-Instruct-2507 (학습전용 모델)" |
| 다운로드 용량 | 약 3.66GB | 약 2.66GB |
| 홈 진행 단계 | `home_stage_model`("나만의 AI쌤 · %1$s")에 모델명 주입 | 동일 |
| 자유 대화 칩 | 사용 가능 | 비활성화 + "자유 대화는 나만의 AI쌤(Gemma4)에서 사용할 수 있어요." |
| 알림 | Gemma 모델명 | "Qwen3-4B-Instruct-2507 · 약 2.66GB · Wi-Fi 전용" |

---

## 9. 개선 작업 이력

2026-11 이전에 완료된 개선 작업 3건이다. 모두 코드로 확인했다.

### 9.1 재분석 및 분석 중단 오류 수정 — 완료

| 문제 | 개선 | 코드 확인 |
|---|---|---|
| 분석 완료 후 원본 파일이 삭제되어 재분석 실패 | 원본 보존, 임시 파일만 `finally`에서 삭제 | `analyzeImage` `finally` 블록 |
| 취소된 코루틴에서 토큰 조회가 예외를 던져 UI 복구 미실행 | `recoverUiAfterGeneration()` 분리, 토큰 조회는 내부 `try/catch` | L945 |
| 후속 질문 취소도 동일 원칙 필요 | `sendQuestion`도 동일 구조 적용 | L899 |
| 이전 작업의 늦은 결과가 새 작업을 덮어씀 | `generationId` 도입, 모든 결과 반영에 `currentGeneration == generationId` 검사 | L794, L899 |
| 사진 교체·과목 변경 시 파일 불일치 | `confirmCapture` / `resetForNewSubject`에서 이전 파일 정리 | L566, L598 |

### 9.2 PaddleOCR 인식 품질 및 결과 구조 개선 — 완료 (2건 보류)

| 영역 | 개선 | 코드 확인 |
|---|---|---|
| 결과 구조 | `List<String>` + `joinToString` → `List<OcrBlock>` 구조화 | `OcrTextRecognizer` 인터페이스 변경 |
| 유형 분류 | `OcrBlockType` 5종 + `inferBlockType()` 패턴 감지 | `MyAITutorApplication` L118 |
| 표 구조 | 파이프/탭/3공백 다단 셀 탐지 + `[표]` 포맷팅 | `formatOcrBlocksForPrompt` |
| 좌표·신뢰도 | SDK 제공 시 보존 | `OcrTextCandidate` |
| 저신뢰도 안내 | `status_ocr_low_confidence` 연동 | `analyzeImage` |
| JVM 테스트 가능성 | `Rect`를 쓰지 않는 `OcrTextCandidate` 별도 도입 | `OcrTextPostProcessor` |

**보류 2건** (사용자 요청): 전처리 변경 전후 인식률·처리 시간 비교, 한국어 수학·과학·국어 테스트 세트 확보.

> [!WARNING]
> 이 작업에서 "Logcat에 문제 전문이 기본 출력되지 않는다 — 완료(`ocrLogger` 분리)"로 기록되어 있으나, 실제로는 **여전히 기본값이 Logcat 출력**이다. `ocrLogger`를 인자로 분리한 것은 맞지만 기본값이 `{ Log.d(TAG, "OCR 결과:\n$it") }`이고 호출도 된다. 13.1-4 참조.

### 9.3 모델 다운로드 및 SHA-256 검증 안정화 — 완료

| 요구사항 | 구현 |
|---|---|
| 6종 상태 (`NOT_INSTALLED`/`QUEUED`/`DOWNLOADING`/`VERIFYING`/`READY`/`FAILED`) | `ModelDownloadStatus` |
| 크기·SHA-256 모두 확인해야 READY | `QwenModelVerification.decide()` (Qwen), `ModelDownloadManager` (Gemma) |
| 재다운로드 시 이전 해시 기록 초기화 | `startDownload()`에서 `KEY_HASH_*` 리셋 |
| 앱 재실행 후 검증 상태 복구 | SharedPreferences 유지 |
| 검증 완료 후 인터넷 차단해도 실행 | 온디바이스 추론 |

> [!NOTE]
> Qwen의 READY 판정은 `QwenModelVerification`으로 순수 로직 분리되어 JVM 테스트 18개를 확보했다. Gemma는 Android 의존 코드 내부에 있어 단위 테스트가 없다.

---

## 10. Qwen 추가 구현 완료도

### 10.1 단계별 진행

| 단계 | 작업 | 상태 |
|---|---|---|
| 0 | Gemma 기준선 고정 | **완료** — 허용된 4개 변경만 적용, 실기기 검증된 확정값 |
| 1 | Qwen 선택 저장소 + 다운로드/검증 관리자 | **완료** — `ModelSelectionRepository`, `QwenModelDownloadManager`, `QwenModelVerification`, 0.6 정규화 |
| 2 | Qwen Chat Template | **완료** — `Qwen3ChatTemplate` + 테스트 2개 (Jinja 실렌더링 미검증) |
| 3 | QwenAssistant + Qwen 어댑터 | **완료** |
| 4 | Coordinator / SwitchableTutorEngine | **완료** — `EngineRuntime` seam 포함 |
| 5 | Compose 모델 선택 UI + 자유 대화 칩 연동 | **완료** — `ModelSelectionCard`, `NoModelRecoveryDialog` |
| 6 | Gemma 회귀 + Qwen 실기기 테스트 | **단위 완료(149개) / 실기기 미착수** — `androidTest` 디렉터리 없음 |
| 7 | 릴리스 정리 | **해당 없음 (개발 단계)** — 배포는 미정. README, `THIRD_PARTY_NOTICES.md`, 설정 화면 라이선스 고지는 Qwen 반영 완료. AAB 생성·`isMinifyEnabled`·서명 키는 배포 시점에 검토 |

> [!NOTE]
> 단계 7은 **아직 할 단계가 아니다.** 배포 계획이 없으므로 미완성 항목이 아니라 범위 밖 작업이다.
> 배포 시점에 필요한 것: AAB 생성(`bundleRelease`), `isMinifyEnabled` 활성화 여부 결정, ProGuard 규칙 점검(현재 LiteRT-LM keep 2줄뿐), 서명 키 준비.

### 10.2 최우선 항목 진행 상황

> [!IMPORTANT]
> **현재 개발 단계.** 배포는 미정이며, 10.1 단계 7(배포 정리)은 범위 밖이다.
> 아래는 Qwen 기능 자체의 완료도이다.

| 항목 | 상태 |
|---|---|
| (1) 단일 모델 메모리 로딩/전환 보장 | **완료** |
| (2) Qwen Chat Template 호환성 | **완료** (Jinja 실렌더링 검증 제외) |
| (3) 파일 크기·SHA 검증 | **완료** |
| (4) 2,048 토큰 예산 관리 | **부분 완료** — 상수·초기 예산 완료. 출력 예산 동적 계산, OCR 블록 축약은 미구현 |
| (5) 실기기 GPU/CPU 메모리 검증 | **미착수** |

### 10.3 미완성 항목

| 항목 | 현재 상태 | 영향 |
|---|---|---|
| **Qwen 엔진 초기화 전 SHA 검증 미강제** | `QwenAssistant.ensureInitializedOnRuntimeThread()`가 크기만 `require`로 확인하고 SHA 검증 완료(`KEY_HASH_VERIFIED`)는 확인하지 않는다 (13.2-3) | 크기만 같은 손상된 파일로 엔진이 초기화될 수 있다. UI 경로(`setSelectedModel`이 `READY` 요구)를 통해서만 진입하므로 임팩트는 제한적이나, 초기화 경로 자체에는 검증이 없다 |
| peak RSS 모니터링 | `QwenAssistant.PEAK_RSS_LIMIT_BYTES`(5.5GB)가 **선언만** 되어 있고 참조 0건 | 5.5GB 초과 시 자동 중단 규칙이 동작하지 않는다 |
| OCR 입력 블록 축약 | 미구현. 출력 예산 ≤ 0 시 블록 경계 축약 없음 | 긴 OCR 입력에서 2,048 초과 가능 |
| `error_context_overflow` 문자열 | 정의만, 참조 0건 | 위 미구현의 부수 증거 |
| `error_cpu_fallback_blocked` 문자열 | 정의만, 참조 0건 | CPU 차단이 `IllegalStateException` 메시지로만 전파되고 UI 안내로 연결되지 않는다 |
| 검증 캐시 fingerprint | 불리언만 저장 (5.3 참조) | 파일 교체 시 이전 검증 결과 재사용 가능 |
| 컨텍스트 토큰 표시 | `HomeUiState.maxContextTokens = MAX_CONTEXT_TOKENS`(4,096)이 모델 분기 없이 표시 | Qwen 활성 시 화면 수치가 실제 한도의 2배 |
| Thinking 카드 | `SettingsScreen`에서 선택 모델과 무관하게 항상 표시. 제목 "Gemma 4 생각 모드" | 동작 오류는 없으나(QwenAssistant가 false 강제) Qwen에서도 반영되는 것처럼 보임 |
| Qwen 다운로드 중 메모리 확인 | `startDownload()`이 저장 공간만 확인, `availMem` 미확인 | 3GB 미만일 때 다운로드 일시 중단 규칙 미구현 |
| `home_stage_gemma_detail` | 진행 단계 **제목**은 분기 완료, **상세 문구**는 "Gemma 4 E4B에 전달해 풀이하고 있습니다"로 고정 | Qwen 분석 중에도 상세 문구는 Gemma 지칭 |
| 미사용 함수 | `HomeViewModel.startSelectedModelDownload()`(L481), `deleteSelectedModel()`(L493) — 실제 UI는 `startModelDownload(model)` / `deleteModelFile(model)` 사용 | 죽은 코드 |
| LiteRT-LM 버전 표시 | 설정 화면에 앱 버전·백엔드(GPU/CPU)는 표시, LiteRT-LM 버전 문자열 미표시 | 요구사항 대비 미완성 |

---

## 11. 테스트

### 11.1 단위 테스트 현황

총 **149개** (13개 파일). 상세는 2.2 참조.

**잘 커버된 영역:** UI 상태 로직, 스트림 파서, 모델 검증 판정, 선택 저장소 정규화, 런타임 불변식, OCR 후처리, 마크다운/수식 분리.

**커버되지 않은 영역:**
- 카메라 (`CameraPreview`, `ImageCropper`, `ExifImageTransform`)
- OCR 이미지 보정 (`OcrImageEnhancer`)
- 다운로드 관리자 (Android `DownloadManager` 의존)
- ppocr-sdk 전체 (JVM 단위 테스트 없음)
- 실제 Jinja 템플릿 렌더링
- UI 컴포저블 (Compose 테스트 없음)

### 11.2 Qwen 관련 테스트 커버

| 항목 | 테스트 |
|---|---|
| Chat Template string/typed parts 렌더링 | `Qwen3ChatTemplateTest` (2) |
| 모델 상수(파일명·revision·크기·SHA) 승인값 일치 | `QwenModelVerificationTest` (3) |
| 정확 크기만 통과, 1바이트 차이도 실패 | `QwenModelVerificationTest` (4) |
| 크기 동일·해시 상이 시 실패 | `QwenModelVerificationTest` (3) |
| `activeEngineCount <= 1` 불변식 | `ModelRuntimeStateTest` (11) |
| 저장값 정규화 | `ModelSelectionRepositoryTest` (10), `HomeViewModelTest` (1) |
| 0.6 모드 가드 | `HomeViewModelTest` (3) |
| 전환 위임 규칙 | `SwitchableTutorEngineTest` (13) |
| 모델별 다운로드/삭제 격리 | `HomeViewModelTest` (2) |

### 11.3 미작성 테스트

- 검증 완료 후 파일 변경 시 캐시 무효화
- 여러 `Content.Text` 조각 순서·multi-turn 경계 유지
- Qwen에서 자유 대화 프롬프트 미생성
- 출력 예산이 2,048을 넘지 않는지
- Thinking OFF 시 Thought 텍스트가 답변에 섞이지 않는지
- 전환 실패 시 3선택지 다이얼로그 표시
- CPU fallback 차단 메시지 표시
- Qwen 활성 시 Thinking 카드/자유 대화 칩 비활성화 (UI 테스트)
- 모델 전환 중 백그라운드 진입
- Qwen 모델 삭제 시 캐시 정리
- 진단 로그에 OCR 원문·질문·시스템 프롬프트 미기록
- 해시 검증 I/O 오류 시 실패 상태 전이 및 재시도 백오프 (13.3 참조)

### 11.4 실기기 테스트 (전 항목 미착수)

`app/src/androidTest` 디렉터리가 없다.

- Wi-Fi 다운로드 재개·완료
- 앱 종료 후 재실행 시 상태 복구
- 실제 파일 SHA-256 검증 통과
- GPU 초기화 후 한국어 스트리밍
- GPU 실패 기기 CPU fallback 안전성
- 초기 로딩 시간, 첫 토큰 시간, 응답 속도, peak RAM, 발열
- 수학·과학·국어·영어·사회 문제 및 자유 대화
- 짧은/긴 OCR, 후속 질문 연속, 취소, 새 문제
- 잘못된 OCR 수식·숫자를 임의 확정하지 않고 확인 요청하는지
- 프롬프트 공격에도 아동·청소년 보호 규칙 유지

### 11.5 출시 합격 조건

> [!IMPORTANT]
> **개발 단계 기준선.** 아래는 Qwen 기능 완성도와 실기기 검증 기준이며, **배포 승인 기준이 아니다.**
> 배포는 미정이며 AAB 생성·`isMinifyEnabled`·서명 키는 배포 시점에 별도 검토한다.
> 조건 8의 "Release 빌드"는 배포용 AAB가 아니라 **릴리스 변형 빌드 검증**을 뜻한다.

1. 사용자 질문이 Qwen 템플릿에서 비어 있지 않게 전달된다.
2. 파일 크기와 SHA-256을 모두 확인한 경우에만 READY가 된다.
3. 모든 요청이 2,048 컨텍스트 제한을 준수한다.
4. 한국어 추론·스트리밍·취소·후속 대화가 실기기에서 정상 동작한다.
5. GPU/CPU 선택이 테스트된 메모리 범위 안에서 안전하다.
6. 화면·알림에 현재 선택 또는 다운로드 중인 모델의 정확한 이름·용량·상태가 표시된다.
7. Qwen 진단 로그에 OCR 원문·사용자 질문·시스템 프롬프트가 남지 않는다.
8. Release 빌드, 전체 단위 테스트(149개), 핵심 계측 테스트가 통과한다.
9. 기존 Gemma 기능(촬영→OCR→답변, Thinking, 후속 질문, 취소, 새 문제, 자유 대화)이 변경 전과 동일하게 동작한다.
10. Qwen GPU 초기화 16~21초를 UI에 반영해 대기 상태를 명확히 표시한다.

---

## 12. 통합 과정에서 정정한 내용

네 원본 문서: 계획서(`MyAITutor_Qwen3_Implementation_Plan.md`), 코드리뷰(`코드리뷰_전수조사결과.md`), 개선안(`MyAITutor_개선안.md`), kilo(`코드리뷰kilo.md`).
`코드리뷰kilo.md`는 다른 세 문서보다 정확도가 높았으며, 파일 수(Git 추적 131개, Kotlin 97개)와 클래스 목록은 실측과 일치했다.

### 12.1 코드에 존재하지 않는 항목 (삭제)

| 원본 | 서술 | 실제 코드 | 처리 |
|---|---|---|---|
| 코드리뷰 | "R-04" 등 추적 ID 표기 | 코드에 해당 표기 없음 | 삭제 |
| 코드리뷰 | `FocusAiApp.kt` 309라인 | 실제 346라인 | 실측값으로 교체 |
| 코드리뷰 | `HomeScreen.kt` 462라인 | 실제 472라인 | 실측값으로 교체 |
| 코드리뷰 | `strings.xml` 214라인 | 실제 217라인 | 실측값으로 교체 |
| 코드리뷰 | 앱 프로덕션 소스 44개 파일 | 실제 56개 | 실측값으로 교체 |
| 코드리뷰 | "전체 소스 파일 83개 (프로덕션 70 + 테스트 13)" | 실제 Kotlin 97개 (앱 56+13, ppocr 26+2) | 실측값으로 교체 |
| 코드리뷰 | Gemma 예상 크기 "3,659,530,240 바이트 (~3.66 GB)" | 코드 `EXPECTED_MODEL_BYTES = 3_659_530_240L` — 일치 | 유지 (단 `MIN_MODEL_BYTES = 3_500_000_000L`과 별개임을 명시) |
| 개선안 | "`ppocr-sdk`는 검토용 ZIP에서 의도적으로 제외된 것으로 간주", "내부 구현은 통합 시 별도 확인" | 저장소에 **모듈이 포함되어 있고** 26개 파일 전수 확인됨 | 서술 삭제. 실제 구조(2.3)로 대체 |
| 개선안 | "`HomeViewModelTest` 25개 케이스, 25/25 통과" | 현재 44개 (다른 테스트 파일 포함 총 149개) | 실측값으로 교체 |
| 개선안 | "Logcat에 문제 전문이 기본 출력되지 않는다 — 완료" | `ocrLogger` 기본값이 `Log.d(TAG, "OCR 결과:\n$it")`이고 L705에서 호출됨 | **미완성으로 재분류** (13.2-4, 10.3) |
| kilo | 앱 단위 테스트 "17개 파일" | 실제 **13개 파일** (149개 테스트). 목록에 13개만 열거되어 본문과 불일치 |
| kilo | `HomeViewModel.kt` 1187줄 | 실제 **1188줄** |
| kilo | `FakeGemmaTutorEngine`를 `LlmInferenceEngine.kt`에 포함 | 실제 `LlmInferenceEngine.kt`(246줄) 내부에 존재 — 정확함 |
| kilo | `SpecialTokenFilter`를 순수 JVM 로직으로 나열 | 실제는 `TutorStreamParser`의 `internal class`(L280) 중첩 클래스. 독립 파일 아님 |
| kilo | "`HomeViewModel` `deleteModelFile` vs `deleteSelectedModel` 동작 불일치(코드 확인됨)" | **정확함.** 13.3에 등재 |
| kilo | ppocr-sdk `assets/models`에 det/rec ONNX + rec YAML | **정확함.** `det/inference.onnx`, `rec/inference.onnx`, `rec/inference.yml`(11,989줄) |

### 12.2 오류 정정

| 원본 | 서술 | 정정 |
|---|---|---|
| 코드리뷰 | "리뷰 날짜: 2026년 1월 22일" | 코드 기준 HEAD의 커밋일은 2026-11-14. 기준 HEAD 기준으로 변경 |
| 코드리뷰 | "`ModelRuntimeStateTest` — 테스트 대상: QwenModelVerification" | 실제 대상은 `RuntimeStatus`/`ModelRuntimeState` 불변식 |
| 코드리뷰 | "테스트 13개 클래스" (강점 항목) | 13개 **파일**, 149개 **테스트**로 구분 |
| 코드리뷰 | "`HomeViewModel.close()` 메서드 부재로 컴파일 오류 위험" (6.1.1, 8.2.1, 8.3.1) | `close()`는 L174에 **존재한다**. "컴파일 오류 위험"은 근거 없음. 실제 이슈는 `runBlocking`으로 [13.1](#131-critical)로 이동 |
| 코드리뷰 | 부록 "삭제: 기존 6.1.1 이슈(close runBlocking) - 해당 메서드 미존재로 무효" | `close()`가 존재하므로 "미존재로 무효"는 오류. runBlocking 이슈는 유효하다 |
| 코드리뷰 | "`CameraPreview` DisposableEffect 내 `providerFuture.get()` — 메인 스레드 차단" | `get()`은 `Handler.post`된 `Runnable` **내부**에서 호출된다. 직접 블로킹은 아니다. 미완료 Future 대기 문제로 재기술 (13.2-5) |
| 코드리뷰 | "`BackHandler` 중복 — 예상치 못하게 작동" | 두 핸들러의 `enabled` 조건이 `showCamera` / `!showCamera && !showSettings`로 **상호 배타적**이다. 동시에 활성화되지 않는다. 버그 근거 없음 → 13.3 "정리 여지"로 강등 |
| 코드리뷰 | "Qwen CPU 폴백 임계값 5GB는 과도 — 2~3GB로 하향" | 모델 카드 실측(CPU peak 4.58~6.09GB)에 근거해 **의도적으로 설정한 값**이다. 임의 하향은 OOM 위험. 정책 결정 사항으로 재분류, 코드 변경 권고 아님 |
| 코드리뷰 | 개선안 "PaddleOCR 결과 구조 `List<String>` + `joinToString`" | 현재는 `List<OcrBlock>` 구조화 완료. 개선안은 **개선 전 상태를 문제로 기술한 것** — 7.3 참조 |
| 코드리뷰 | "Gemma 저장소가 Qwen과 동일 디렉토리" | 정확함. 단 파일명이 다르므로 충돌 없음을 명시 |

### 12.3 중복 제거

| 중복 | 처리 |
|---|---|
| 모델 다운로드 URL 표 (코드리뷰 4.1 + 9장, 계획서 2장) | 5.1로 통합 |
| 모델별 컨텍스트/토큰/샘플러 값 (코드리뷰 4.2 + 6.5, 계획서 1장 + 5.2 + 5.4) | 4.1 표 하나로 통합 |
| 테스트 목록 (코드리뷰 2.2 + 7.1) | 2.2로 통합, 커버 항목은 11.2로 분리 |
| 보안 표 (코드리뷰 6.4) + 성능 표 (코드리뷰 6.5) | 14장으로 통합 |
| 계획서의 8.1 체크리스트 3~4회 중복 나열 | 11.2(커버됨) / 11.3(미작성)로 분리 |
| 계획서 11장·12장(개정 이력) | 타임라인 정보이므로 본문에서 제거, 필요한 내용만 12장에 반영 |
| 계획서 13장(구현 완료 요약) + 9장(구현 순서) | 10.1로 통합 |
| 개선안의 완료 판정 체크리스트(29항목) + 4.1 기능 테스트 표 | 9장(개선 이력)로 통합, 중복 서술 제거 |
| 개선안 2.4의 `OcrBlock` 코드 블록 + 2.3 요구사항 표 | 7.3으로 통합 |
| kilo 12장의 Kotlin 파일 인벤토리 전체 나열 | 2장의 표로 통합 (내용은 동일) |
| kilo 5장의 클래스/인터페이스 요약 27개 행 | 2장·3장·4장에 분산 통합 |
| kilo 6장의 데이터 흐름 (사진/후속/전환/다운로드) | 3.3·7.2·7.4·6장으로 통합 |
| kilo 9.2의 14개 관찰 중 이미 등재된 항목(1·2·6·7·8·9·13·14) | 13장 기존 항목과 통합, 중복 제거 |

### 12.4 추측성 서술 정리

| 원본 | 서술 | 처리 |
|---|---|---|
| 코드리뷰 | "경쟁 조건 가능성" (HomeViewModel Job 관리) | 구체적 시나리오 없는 가능성 서술. 검증되지 않은 추측이므로 삭제, 실제 복잡성을 13.2-6에 사실만 기술 |
| 코드리뷰 | "OOM 위험" (OcrImageEnhancer 샤프닝) | `MAX_SHARPEN_PIXELS = 5_000_000` 가드가 실재한다. 최대 약 40MB 할당은 사실이나 OOM 여부는 미검증. 사실만 기술 (13.3) |
| 코드리뷰 | "파일명 충돌 가능" (ImageCropper) | 밀리초 단위 충돌은 이론적으로 가능하나 실측 근거 없음. 13.3으로 강등 |
| 코드리뷰 | "대부분의 보급형/중급기에서 CPU 폴백 차단" | 기기별 분포 근거 없음. 삭제 |
| 개선안 | "전체 진행률 100% 완료 (보류 제외) 29/29" | 항목 수가 실제 코드 이행 범위와 대응하지 않는다. 개별 작업별 상태(9장)로 대체 |
| 개선안 | 모든 항목에 "✅ 테스트 통과" 일괄 표기 | 테스트 실행 근거가 제시되지 않았다. 코드로 확인된 사실만 남기고 "테스트 통과" 표기는 제거 |
| kilo | 9.2-3 "OCR 실패 시에도 `generationJob` `finally`에서 UI 복구 수행 — 정상 흐름" | 관찰은 정확하나 이슈가 아니므로 별도 등재하지 않음 |
| kilo | 9.2-5 "`GemmaAssistant` legacy API 학년 하드코딩 — 앱 동작 영향 없음" | 정확함. 단 **미 등재 상태**였으므로 13.2-8로 추가 |
| kilo | 9.2-10 "`HomeViewModel` 1187줄 단일 파일 집중" | 정확함 (실제 1188줄). 13.2-7로 추가 |
| kilo | 9.2-11 "다크 테마 미지원" | 정확함. 13.3으로 추가 |
| kilo | 9.2-14 "카메라 저사양 기기 메모리 부담 가능(미측정)" | 측정 근거 없음. 추측이므로 삭제 |
| kilo | "Git 추적 파일 수 131개" | **정확함.** `git ls-files` 기준 131개 확인 |
| kilo | "Kotlin 소스 97개(앱 56+13, ppocr 26+2)" | **정확함.** 2장 실측과 일치 |

### 12.5 원본에 없던 항목 추가

| 항목 | 출처 |
|---|---|
| Qwen Chat Template typed parts 문제 및 해결 | 계획서 5.3 |
| 모드 정합 규칙 3경로 | 계획서 0.6 |
| 2단계 복구 정책 및 3선택지 | 계획서 0.4 |
| `RuntimeStatus` 불변식 3개 | 계획서 4.1 |
| 단일 엔진 상주 불변식 | 계획서 0.4 |
| 미완성 항목 12개 | 네 문서 어디에도 없던 것 (10.3) |
| `OcrBlock` 좌표가 `Rect`(Android 타입)라 JVM 테스트 불가 → `OcrTextCandidate` 별도 도입 | 코드 확인 결과 (7.3) |
| 이미지 파일 수명주기 분리 (원본 보존 / 임시 삭제) | 코드 확인 결과 (7.4) |
| `deleteSelectedModel`의 주석/구현 불일치 (엔진 미해제) | kilo 9.2-4 (13.3) |
| `GemmaAssistant`/`QwenAssistant` legacy API의 학년 하드코딩 | kilo 9.2-5 (13.2-8) |
| `HomeViewModel` 단일 파일 비대 | kilo 9.2-10 (13.2-7) |
| 다크 테마 미지원 | kilo 9.2-11 (13.3) |
| CI 파이프라인 상세 (`.github/workflows/android.yml`) | kilo 2.1 (1.3) |
| `gradle.properties` 설정값 | kilo 2.1 (1.2) |
| ppocr-sdk 에셋 구성 (`det/rec ONNX`, `rec YAML`) | kilo 3.3 (2.3) |

---

## 13. 발견된 문제점 및 개선 후보

### 13.1 Critical

#### 1. `HomeViewModel.close()`에서 `runBlocking` 사용 — **[완료]**

종료 시 최대 3초간 UI 스레드를 대기시켜 ANR 위험이 있었던 `runBlocking`을 별도 daemon 스레드 스코프(`shutdownScope`)에서 비동기로 처리하는 것으로 수정했다. 호출 스레드를 막지 않으면서 scope 취소와 무관하게 엔진 해제를 끝까지 보장한다. 단위 테스트(`close는 UI 스레드를 막지 않고 엔진을 한 번만 해제한다`)로 결정적으로 검증했다.

#### 2. Gemma 모델 크기 검증값 중복 정의 — **[완료]**

`ModelDownloadManager.MIN_MODEL_BYTES`를 `internal`로 노출하고 `GemmaAssistant`가 이 단일 원본을 참조하도록 했다. 동작 변화 없는 가시성 변경 1줄 + 참조 교체로 완료했다.

### 13.2 Major

#### 3. `QwenAssistant` 초기화 시 SHA 검증 미강제 — **[완료]**

`QwenModelDownloadManager.isFileVerified()`를 추가하고 `QwenAssistant.ensureInitializedOnRuntimeThread()`에서 크기뿐 아니라 저장된 SHA-256 검증 완료 상태까지 요구하도록 했다. 크기만 일치하는 손상된/부분 파일로 엔진이 초기화되는 경로를 차단했다.

#### 4. OCR 원문 Logcat 출력 — **[완료]**

OCR 문제 전문이 DEBUG 빌드에서만 Logcat에 기록되도록 `BuildConfig.DEBUG` 조건부로 변경했다. Release 빌드에서는 개인정보가 로그에 남지 않도록 했다. `// TODO` 주석도 제거했다.

#### 5. `CameraPreview`의 카메라 초기화 대기 — **[완료]**

실제 코드에서 `providerFuture.addListener(listener, ContextCompat.getMainExecutor(context))` 패턴으로 이미 전환되어 있다. `Future.get()`이 핸들러 Runnable 내부에서 호출되므로 UI 스레드를 직접 블로킹하지 않으며, `disposed` 플래그로 화면 이탈 후 대기 스레드 해제도 처리한다. 문서 상의 이슈는 해결됨.

#### 6. `HomeViewModel`의 취소/재시도 상태 변수 다수

`generationJob`, `generationId`, `userCancelledGeneration`, `queuedQuestion`, `warmupJob`, `isClosed`가 함께 사용된다.

**영향:** 상태 간 상호작용이 복잡하며, 경쟁 조건 가능성을 정적으로 배제하기 어렵다. `HomeViewModelTest` 44개가 이 영역을 비교적 두텁게 커버한다.

**개선 방향:** 즉시 조치는 불필요. 신규 기능 추가 시 상태 머신 도입을 검토한다.

#### 7. `HomeViewModel` 단일 파일 비대 (1,188줄)

상태·OCR·LLM·다운로드·전환·복구·캐시 정리가 한 파일에 집중되어 있다. `HomeViewModelHost`가 런타임 코디네이터를 주입해 일부 관심사는 분리되었으나 ViewModel 자체는 미분리.

**개선 방향:** 사용자 요청 시 별도 작업으로 진행. 분리 시 `HomeViewModelTest` 44개가 회귀 기준선이 된다.

#### 8. `GemmaAssistant` legacy 콜백 API의 학년 하드코딩 — **[완료]**

```kotlin
// GemmaAssistant L191, L208 — Assistant 인터페이스 호환용
override suspend fun analyzeImage(...) = collectLegacy(
    request = AssistantRequest(
        textParts = listOf("[OCR 인식 텍스트]\n$ocrText",
            analyzeImagePrompt(subject, TutorGrade.ELEMENTARY))))
override suspend fun askFollowUp(...) = collectLegacy(
    request = AssistantRequest(
        textParts = listOf(followUpPrompt(question, subject, TutorGrade.ELEMENTARY))))
```

`TutorGrade.ELEMENTARY`가 하드코딩되어 있다.

**실제 영향 없음:** 앱의 실제 경로는 `RealGemmaTutorEngine.stream()` → `request.grade`를 사용하며, 이 콜백 API를 호출하지 않는다. `Assistant` 인터페이스 호환용으로만 존재. 직접 호출·테스트 시에만 학년이 ELEMENTARY로 고정된다.

**개선 방향:** `QwenAssistant`에도 동일한 패턴이 있다. 두 Assistant의 legacy API를 삭제하거나 `grade` 파라미터를 추가하는 것을 검토한다.

### 13.3 Minor

| 항목 | 내용 | 비고 |
|---|---|---|
| `deleteSelectedModel` 미사용 + `deleteModelFile`과 동작 불일치 | `deleteModelFile(model)`은 활성 모델 삭제 시 `awaitGenerationStopped` → `release()` → `warmupJob` 취소를 수행. `deleteSelectedModel()`은 주석에 "먼저 엔진을 해제한다"고 적혀 있으나 **실제로는 `manager.deleteModel()`만 호출**한다. 두 함수 모두 UI에서 미사용이며 실제 경로는 `deleteModelFile(model)` | 죽은 코드 + 주석/구현 불일치. 정리 필요 |
| 다크 테마 미지원 | `Theme.kt`가 `lightColorScheme`만 정의한다. `isSystemInDarkTheme()`은 `AssistantAnswer`(L31)·`FormulaText`(L35)에서 LaTeX 렌더러 색상 선택에만 사용 | 시스템이 다크 모드여도 앱 UI는 라이트 유지 | **보류** — 도입 계획 없음. 사용자 요청으로 당분간 미진행.
| Linux `gradlew` / Wrapper 버전 불일치 | `gradle-wrapper.properties`는 9.6.0, Linux `gradlew` 스크립트는 자체 부트스트랩으로 `GRADLE_VERSION="9.3.1"`을 다운로드 | Windows/Android Studio 경로와 Linux CI 경로가 다른 Gradle을 사용 |
| `ImageCropper` 출력 파일명 | `"focus_crop_${System.currentTimeMillis()}.png"` — 같은 밀리초에 다중 크롭 시 이론적 충돌 가능 | 실측 근거 없음. UUID/카운터 추가 검토 가능 |
| `FocusAiApp` BackHandler 2개 | `enabled` 조건이 상호 배타적이므로 동시 활성화되지 않는다. 라우팅 의도로 보이며 버그 근거 없음 | 단일 핸들러 통합 시 가독성 향상 여지 |
| ppocr-sdk JVM 단위 테스트 부재 | `DetPreprocessor`, `RecPreprocessor`, `CTCDecoder`, `DBPostProcessor`, `BoxSorter` 등 순수 로직이 `object`로 구현되어 테스트 가능하나 검증 안 됨 | 개선 가치 높음 |
| `ModelSelection` 미사용 프로퍼티 | `isFreeChatAllowed`, `isConsistent`는 저장소 내부에서만 사용 | 유지 가능 |
| `OcrImageEnhancer.sharpen()` 메모리 | `MAX_SHARPEN_PIXELS = 5_000_000` 가드 하에서 `IntArray` 2개 = 약 40MB | 가드가 있으므로 OOM 근거는 불충분. 타일 처리 검토 여지 |
| `HomeUiState.maxContextTokens` 모델 미분기 | Qwen에서 4,096으로 표시 (10.3 참조) | 코드 수정 필요 |

### 13.4 Gemma 해시 검증의 I/O 오류 처리 (결정됨: 완화 적용)

> [!IMPORTANT]
> **사용자 결정:** I/O 오류 처리 개선을 **완화(완전 보존)** 방향으로 진행한다.
> `ModelDownloadManager.kt`는 계획서 0.1의 Gemma 보존 대상이다. 현재 동작(실기기 검증 완료)을 유지하고,
> 아래 미비를 해결하지 않는 것으로 결정했다. 별도 승인 시 재검토.
>
> 검토 보류된 개선 방향:
> - `catch`에서 I/O 오류를 별도 키로 기록해 `snapshot()`이 재시도 가능/불가능을 구분
> - 검증 재시도에 백오프(예: 5초 → 30초 → 5분) 적용
> - Gemma 크기 판정을 Qwen과 동일한 정확 일치로 통일

**현재 동작** ([verifyModelHash()](file:///C:/Users/donian/Desktop/android_app/MyAITutor/app/src/main/java/com/donian/myaitutor/model/ModelDownloadManager.kt#L194))

```kotlin
val match = actual.equals(EXPECTED_MODEL_SHA256, ignoreCase = true)
preferences.edit {                                    // 성공 경로에서만 실행
    putBoolean(KEY_HASH_VERIFIED, match)
    putBoolean(KEY_HASH_MISMATCH, !match)
}
match
} catch (e: Exception) {
    false                                               // prefs 미갱신
}
```

| 관찰 | 판정 |
|---|---|
| `catch`가 prefs를 갱신하지 않는다 | 사실. 다만 재시도를 유도하는 의도로 볼 수 있다 |
| I/O 오류가 `KEY_HASH_MISMATCH`와 구분되지 않는다 | 사실. 전자는 영구 실패(재다운로드 필요), 후자는 일시적일 수 있다 |
| 폴링마다 전체 파일을 재해싱한다 | 사실. `HomeViewModel` 폴링(L134)이 5초 간격으로 `needsHashVerification()` → `verifyModelHash()`를 호출 |
| 영구 VERIFYING 루프 | **아님.** 재시도로 복구된다. 앱 재시작·파일 정상화 시 해소 |
| 무한 로딩으로 화면 갇힘 | **아님.** `ModelLoadingScreen`이 `VERIFYING`에서도 건너뛰기(`onSkip`)를 제공한다 |

**실질 영향**

1. **사용자 피드백 부재** — 검증이 실패해도 `FAILED`로 전이하지 않아 사유를 볼 수 없다.
2. **불필요한 I/O** — 실패 상태에서 5초마다 2.6~3.6GB를 다시 읽는다. 백오프가 없다.
3. **정책 불일치** — Gemma는 크기를 `>= 3_500_000_000` 임계값으로 비교하고, Qwen은 `!= EXPECTED_MODEL_BYTES` 정확 일치로 비교한다(5.3 참조). SHA-256이 최종 방어선이므로 실질 위험은 낮다.

**검토 가능한 개선 방향** (착수 시 별도 승인 필요 — Gemma 보존 원칙과 충돌 검토 선행)

- `catch`에서 I/O 오류를 별도 키로 기록해 `snapshot()`이 재시도 가능/불가능을 구분
- 검증 재시도에 백오프(예: 5초 → 30초 → 5분) 적용
- Gemma 크기 판정을 Qwen과 동일한 정확 일치로 통일

---

## 14. 보안 및 성능 고려사항

### 14.1 보안

| 항목 | 상태 | 비고 |
|---|---|---|
| `INTERNET` | 선언됨 | Hugging Face 다운로드 |
| `ACCESS_NETWORK_STATE` | 선언됨 | Wi-Fi 확인용 |
| `CAMERA` | 선언됨 | 문제 촬영 |
| `usesCleartextTraffic` | `false` | HTTPS 강제 |
| `allowBackup` | `false` | 백업 비활성 |
| `dataExtractionRules` | 설정됨 | `data_extraction_rules.xml` |
| `isMinifyEnabled` | `false` | release에서도 난독화·최적화 미적용 |
| ABI | `arm64-v8a`, `x86_64` | 32비트는 LiteRT-LM 미제공으로 제외 |
| 모델 파일 저장 | 앱 전용 외부 디렉터리 | APK 미포함, 런타임 다운로드 |
| 모델 URL | HTTPS + 고정 revision | 파일이 교체되어도 승인 해시 미일치 시 설치 미인정 |
| LLM 실행 | 온디바이스 | 외부 전송 없음 |
| OCR 로그 | **개선 필요** | 13.2-4 참조 |

**권장 사항:** 릴리즈 빌드에서 R8/ProGuard 활성화 여부를 검토한다. 단 LiteRT-LM 네이티브 호출·Jinja 템플릿 문자열이 keep rule에 영향을 받지 않는지 확인이 선행되어야 한다.

### 14.2 성능

| 항목 | 값 | 비고 |
|---|---|---|
| Gemma 컨텍스트 | 4,096 | KV 캐시 예산 |
| Qwen 컨텍스트 | 2,048 | 더 보수적 |
| 이미지 정규화 장변 | 2,400px | OCR 입력 상한 |
| OCR 디텍션 상한 | 4,000px (`detMaxSideLimit`) | 32의 배수 정렬 |
| 미리보기 장변 | 1,600px | OOM 방지 |
| 샤프닝 픽셀 상한 | 5,000,000 | 약 40MB |
| 다운로드 폴링 | 1초(전송 중) / 5초 | |
| 취소 확인 대기 | 15,000ms | `CANCEL_CONFIRM_TIMEOUT_MS` |
| 엔진 해제 대기 | 3,000ms | `ENGINE_RELEASE_WAIT_MS` |
| Qwen CPU 폴백 최소 availMem | 5,368,709,120 bytes | 모델 카드 실측 근거 |
| Qwen peak RSS 감시 한도 | 5,905,580,032 bytes (5.5GB) | **상수만 선언, 미적용** |
| Qwen GPU 초기화 | 약 16~21초 (프로세스당) | 모델 카드 값, UI 반영 필요 |

---

## 15. 검증 범위와 한계

### 15.1 검증한 것

Android Studio 인덱스 기반 도구로 HEAD `5bd9189` 코드를 직접 읽어 확인했다.

- Gemma 보존 항목: `ModelDownloadManager`의 URL·revision·파일명·`MIN_MODEL_BYTES`·SHA·SharedPreferences 키 무변경
- 허용된 Gemma 변경 4개 항목 적용 상태
- 전환 순서(`ModelRuntimeCoordinator.switchTo`)와 불변식(`RuntimeStatus.init` `require` 3개)
- 0.6 정규화 3경로
- Qwen 전용 상수 3종, `ThinkingConfig(false)`, CPU 폴백 5GB 임계값
- 신규 파일 13개의 존재와 배선
- 단위 테스트 149개 (13파일, `@Test` 정적 계수)
- 미완성 항목의 실제 미구현 여부 (`PEAK_RSS_LIMIT_BYTES`, `error_context_overflow`, `error_cpu_fallback_blocked` 참조 0건 확인)
- 이 문서에 인용된 모든 코드 스니펫의 실제 존재

### 15.2 검증하지 못한 것

- **빌드와 테스트를 실행하지 않았다.** 149개는 `@Test` 개수이며 통과 여부는 미확인이다.
- 모델 카드의 파일 크기·SHA-256이 실제 다운로드 파일과 일치하는지
- 2,048 토큰 한도와 학습 모드 지시문의 실제 토큰 점유율
- GPU/CPU 백엔드별 실제 피크 메모리 (기기 편차 큼)
- `chatTemplate` 지정 시 typed parts가 실제 user 본문으로 렌더링되는지
- ANR·OOM·경쟁 조건 등 런타임 거동 (12장의 항목 중 상당수는 정적 추론이다)
- UI 컴포저블의 실제 렌더링 결과

위 항목은 Windows Android Studio 빌드, 단위 테스트 실행, 대상 기기 실측으로 확인해야 한다.

---

## 16. 참고 자료

### 16.1 외부 문서

1. LiteRT Community Qwen3-4B-Instruct-2507 모델 카드: https://huggingface.co/litert-community/Qwen3-4B-Instruct-2507
2. Qwen3-4B revision 고정 파일: https://huggingface.co/litert-community/Qwen3-4B-Instruct-2507/blob/6ee6014fa9193ea4dbb309f3305d6ee0d78db5a0/qwen3_4b_instruct_2507_mixed_int4.litertlm
3. LiteRT-LM 0.18.0 Kotlin `ConversationConfig.chatTemplate`: https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.0/kotlin/java/com/google/ai/edge/litertlm/Config.kt
4. Qwen3 Instruct-2507 권장 sampling 값: https://github.com/QwenLM/Qwen3/blob/main/docs/source/getting_started/quickstart.md
5. LiteRT-LM 공식 Android 샘플·스킬(수명주기·백엔드 규정): https://github.com/google-ai-edge/litert-samples — `skills/android/litert-lm/SKILL.md`, `references/get-a-model.md`, `samples/litert_lm/voice_assistant`

### 16.2 프로젝트 내 API 검증 출처

Gradle 캐시의 `litertlm-android-0.18.0.jar`에서 확인: `ConversationConfig.chatTemplate`, `EngineConfig.maxNumTokens`, `SamplerConfig(topK, topP, temperature, seed)`. `SamplerConfig`에 `minP` 필드는 없다.

### 16.3 관련 문서

| 문서 | 용도 |
|---|---|
| `README.md` | 사용자용 기능 설명 (Qwen 반영됨) |
| `나만의_AI쌤_개발기획서_v2.4.md` | 제품 요구사항 |
| `THIRD_PARTY_NOTICES.md` | 오픈소스 라이선스 (4개 원본 중 4번째까지 검증 완료, 정확함) |

> [!NOTE]
> 다음 네 문서는 이 문서에 통합되어 **삭제되었다**. 모두 Git 추적되지 않은 상태였으므로 복구할 수 없다.
> 삭제된 내용 중 보존된 기록이 필요하면 12장(정정 내역)을 참조한다.
>
> - `MyAITutor_Qwen3_Implementation_Plan.md`
> - `코드리뷰_전수조사결과.md`
> - `MyAITutor_개선안.md`
> - `코드리뷰kilo.md`

---

**문서 끝.** 코드 수정 없이 세 문서를 통합·정리했다. 10.3의 미완성 항목과 13장의 개선 후보 중 착수할 항목을 알려주면 별도 작업으로 진행한다.