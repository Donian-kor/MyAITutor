# 나만의 AI쌤 (MyAITutor) 0.0.1 — 빌드 및 검증 보고서

> 갱신일: 2026-11-12. STEP 0~8 진행 결과 반영.

## 프로젝트 구조 (17개 Kotlin 파일)

```
com.donian.myaitutor/
├─ MainActivity.kt
├─ ai/
│  ├─ GemmaAssistant.kt            # 엔진/대화/스트리밍/중단/세션 리셋
│  ├─ Assistant.kt                 # 추상 인터페이스 (테스트 주입용)
│  └─ PromptTemplates.kt           # 시스템 지시문·프롬프트 상수
├─ camera/
│  ├─ CameraPreview.kt             # CameraX 프리뷰
│  ├─ CameraScreen.kt              # 카메라 화면 (UI)
│  └─ ImageNormalizer.kt           # EXIF 회전 + 다운스케일
├─ model/
│  ├─ ModelDownloadManager.kt      # 다운로드/상태/무결성 검증
│  ├─ ModelDownloadRepository.kt   # 추상 인터페이스
│  └─ ModelDownloadState.kt        # enum + snapshot
└─ ui/
   ├─ FocusAiApp.kt                # 화면 전환 + 상태 위임 (296줄)
   ├─ home/
   │  ├─ HomeScreen.kt             # 홈 레이아웃
   │  ├─ HomeViewModel.kt          # 상태/생성/취소/폴링
   │  ├─ HomeUiState.kt            # UI 상태 데이터
   │  └─ HomeContent.kt            # 과목·후속 선택 상수
   ├─ camera/CameraScreen.kt
   ├─ settings/SettingsScreen.kt
   ├─ components/InfoCard.kt
   ├─ theme/Theme.kt
   └─ text/
      ├─ AnswerSegments.kt         # LaTeX 분리 (순수)
      ├─ AnswerMarkdown.kt         # Markdown → AnnotatedString
      └─ AssistantAnswer.kt        # 답변 렌더링
```

## 빌드 검증

| 항목 | 결과 |
|---|---|
| `.\gradlew.bat :app:assembleDebug` | BUILD SUCCESSFUL |
| `.\gradlew.bat :app:testDebugUnitTest` | BUILD SUCCESSFUL (17 tests) |
| `.\gradlew.bat :app:lintDebug` | BUILD SUCCESSFUL, 0 error / 26 warning / 1 hint |
| APK ABI | arm64-v8a, x86_64 (32비트 제외) |
| APK 크기 | 70.7MB |

## 테스트 (17개)

| 파일 | 케이스 |
|---|---|
| `AnswerSegmentsTest` | 수식/텍스트 분리 5 |
| `AnswerMarkdownTest` | 굵게/제목/목록 4 |
| `ModelDownloadSnapshotTest` | progress/formatBytes 4 |
| `HomeViewModelTest` | 생성/중단/중복 4 |

## 실기기 검증 (P-01~P-09)

| 항목 | 결과 |
|---|---|
| P-01 답변 중단 | ✅ |
| P-02 입력창 상시 노출 | ✅ |
| P-03 마크다운 렌더링 | ✅ |
| P-04 촬영 버튼 색 | ✅ |
| P-05 후속 선택 칩 | ✅ |
| P-06 모델 다운로드/삭제 | ✅ |
| P-07 LaTeX 렌더링 | ✅ |
| P-08 카메라 흐름 | ✅ |
| P-09 권한 거부 | ✅ |
| C-02 임시 파일 정리 | ✅ (cacheDir 잔여 없음) |

## 모델

- Gemma 4 E4B-IT LiteRT-LM, 약 3.66GB
- Hugging Face `litert-community/gemma-4-E4B-it-litert-lm`
- Wi-Fi 전용 다운로드, 저장 공간 사전 확인
- LiteRT-LM GPU 우선 / CPU 대체 초기화
- SHA-256 무결성 검증 인프라 포함 (해시값 입력 시 활성화)

## 알려진 제한

- LiteRT-LM의 이미지 입력 및 GPU/CPU 백엔드는 실제 대상 기기에서 검증 필요
- 모델 답변의 수학적 정확도·모든 과목 인식률 보장하지 않음
- 대화 문맥은 앱 프로세스 메모리에만 유지 (재실행 시 복원 없음)
- C-03 SHA-256 해시값은 실제 파일 값 입력 전까지 비활성