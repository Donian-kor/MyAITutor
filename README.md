# MyAITutor Android — v0.0.1

MyAITutor는 카메라로 교과서·문제집을 비추고, 기기 안에서 실행되는 Gemma 4 E4B-IT에게 이미지와 질문을 전달해 개념 설명과 풀이를 받는 Android 앱의 초기 프로젝트입니다.

## 0.0.1에 포함된 기능

- Jetpack Compose 기반 학습 및 AI 답변 화면
- 별도 전체 화면 후면 카메라 촬영과 사진 확인·재촬영
- 사진 확인 후 선택 과목을 반영한 AI 풀이 자동 시작
- 과목 선택 메뉴와 넓은 AI 답변·추가 질문 영역
- LaTeX 수식의 인라인·블록 렌더링 및 긴 수식 자동 줄바꿈
- 설정 화면과 Gemma 모델 다운로드 상태 표시
- Hugging Face에서 Gemma 4 E4B `.litertlm` 파일 다운로드
- LiteRT-LM Kotlin API를 통한 GPU 우선 / CPU 대체 초기화
- 촬영 이미지 + 과목 + 교사 지침을 모델에 전달
- 생성 중인 답변을 화면에 표시
- 같은 대화 세션에서 추가 질문 전송
- 분석이 끝나면 임시 촬영 이미지 파일 삭제

## 아직 0.0.1에 포함하지 않은 기능

- Room 기반 학습 기록 및 대화의 앱 재시작 후 복원
- OCR 전용 보조 모델
- 음성 입력 및 음성 답변
- 이미지 영역 자르기·여러 문제 선택
- 기기별 성능 프로파일과 자동 백엔드 선택

첫 버전은 별도 OCR 모델 없이 Gemma 4 E4B의 이미지 이해 기능으로 문제를 직접 분석합니다. AI 답변의 `$...$` 및 `$$...$$` 형식 수식은 LaTeX 렌더러로 표시합니다. 모델이 출력하는 수식의 정확도와 렌더링되지 않는 비표준 문법은 별도 검증이 필요합니다.

## 개발 환경

- Android Studio: AGP 9.1.1 및 Gradle 9.3.1을 지원하는 버전
- JDK: 17 이상
- Android SDK Platform: API 37
- Android Build Tools: Android Studio SDK Manager가 AGP에 맞는 버전을 설치하도록 설정
- 최소 Android: API 24 (Android 7.0)
- Kotlin Gradle Plugin: 2.4.21
- LiteRT-LM Android: 0.18.0
- CameraX: 1.6.2
- Compose LaTeX Renderer: 1.7.2 (MIT)

## 프로젝트 열기

1. ZIP 파일을 Windows PC에 압축 해제합니다.
2. Android Studio에서 **Open**을 선택합니다.
3. 압축 해제한 `MyAITutor_0.0.1` 폴더를 엽니다. `app` 폴더가 아니라 `settings.gradle.kts`가 있는 최상위 폴더를 선택합니다.
4. Gradle Sync가 끝날 때까지 기다립니다. 최초 동기화에는 Gradle 및 라이브러리 다운로드를 위해 인터넷이 필요합니다.
5. Android 휴대폰을 USB로 연결하고 개발자 옵션의 USB 디버깅을 활성화합니다.
6. 실행 장치로 휴대폰을 선택하고 **Run**을 누릅니다.

### Gradle 부트스트랩 스크립트

프로젝트에는 Gradle 9.3.1을 준비하는 `gradlew.bat` / `gradlew` 스크립트를 포함했습니다. 표준 Gradle Wrapper JAR를 포함하는 대신, PC에 Gradle 9.3.1이 없으면 Gradle 공식 배포 서버에서 ZIP을 내려받아 사용자 Gradle 캐시에 풀고 실행합니다. Android Studio에서 Gradle Wrapper를 실행할 때도 동일하게 사용됩니다.

Windows에서 직접 실행하려면 프로젝트 최상위 폴더의 터미널에서:

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
3. 약 3.66GB 모델 파일이므로 Wi-Fi 연결과 충분한 저장 공간을 확인합니다.
4. 다운로드 완료 후 홈으로 돌아가 문제를 카메라에 비추고 **AI 문제 풀이**를 누릅니다.

모델 원본:

- Hugging Face: https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm
- 파일: `gemma-4-E4B-it.litertlm`
- 모델 라이선스: 저장소 모델 카드 기준 Apache 2.0

모델은 APK에 포함되지 않습니다. 앱 전용 다운로드 디렉터리에 저장하고 절대 경로로 LiteRT-LM에 전달합니다. 모델을 받은 뒤 이미지 분석과 답변 생성은 기기 내부에서 수행합니다. 모델 다운로드 및 Gradle 의존성 다운로드는 인터넷이 필요합니다. 이 버전은 파일 크기 기반 기본 검사만 수행하며, SHA-256 체크섬 검증은 아직 연결하지 않았습니다.

## 기기 성능 주의

모델 파일 크기 3.66GB는 실행 중 메모리 사용량과 다릅니다. Google의 모델 카드에는 Galaxy S26 Ultra의 특정 테스트 조건에서 CPU 메모리 약 3.28GB, GPU 메모리 약 710MB가 기록되어 있지만, 이는 다른 기기의 성능이나 메모리 사용량을 보장하지 않습니다.

- GPU 백엔드로 먼저 초기화하고 실패하면 CPU 백엔드를 시도합니다.
- GPU 및 CPU 초기화가 모두 실패하면 오류를 표시합니다.
- 기기의 RAM, GPU 드라이버, Android 버전 및 발열 제한에 따라 지원 여부가 달라집니다.
- 실제 출시 전에 삼성·픽셀 등 목표 기기에서 카메라와 모델을 동시에 실행하는 테스트가 필요합니다.

## 개인정보 처리

- 카메라 권한은 사용자가 허용해야 합니다.
- 사진은 분석 요청을 처리하기 위한 임시 파일로 저장하고 추론 후 삭제합니다.
- 이 버전은 외부 AI API나 클라우드 추론을 호출하지 않습니다.
- 모델 다운로드 외 네트워크 사용은 하지 않습니다.
- 현재 버전은 별도 클라우드 계정이나 회원가입이 없습니다.

## 패키지 정보

- Application ID: `com.donian.myaitutor`
- Version Name: `0.0.1`
- Version Code: `1`

## 알려진 제한

- 이 실행 환경에는 Android SDK와 Gradle 배포 파일이 설치되어 있지 않아 여기서 APK 컴파일이나 실제 휴대폰 실행 검증을 수행하지 못했습니다. Android Studio에서 첫 Gradle Sync 및 `assembleDebug` 확인이 필요합니다.
- LiteRT-LM은 공식 Kotlin API를 사용하지만, Gemma 4 E4B의 이미지 입력 및 GPU/CPU 백엔드는 실제 대상 기기에서 검증해야 합니다.
- 모델 답변의 수학적 정확성이나 모든 과목 문제 인식률을 보장하지 않습니다.
- 수식은 표준 LaTeX 문법을 사용하는 경우 렌더링됩니다. 미지원 명령이나 잘못된 LaTeX 문법은 올바르게 표시되지 않을 수 있습니다.
- 대화 문맥은 현재 앱 프로세스의 메모리에 유지됩니다. 앱 프로세스 종료 후 대화 복원은 후속 버전에서 구현합니다.
