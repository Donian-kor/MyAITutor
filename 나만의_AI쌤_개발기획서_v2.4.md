# 나만의 AI쌤 — 기획·개발서 v2.4

- **앱 표시 이름:** 나만의 AI쌤
- **영문 브랜드명:** My AI Tutor
- **내부 프로젝트명:** `MyAITutor`
- **초기 기준 버전:** `0.0.1` (현재 앱 버전은 Git 커밋 수 기반 자동 산출)
- **플랫폼:** Android
- **문서 상태:** 개발 기준서
- **기준 모델:** [Gemma 4 E4B-IT LiteRT-LM](https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm)

---

## 1. 프로젝트 개요

### 1.1 서비스 정의

**나만의 AI쌤**은 사용자가 교과서, 문제집, 학습지 등의 문제를 촬영하면 스마트폰 안에서 실행되는 PaddleOCR이 글자를 인식하고, Gemma 4 E4B가 인식 텍스트를 바탕으로 개념부터 단계별 풀이를 제공하는 자기주도 학습 앱이다.

학생은 AI가 제공한 설명을 확인한 뒤 추가 질문을 이어갈 수 있다. 학습의 주체는 사용자이며 AI는 필요할 때 활용하는 개인 학습 도구로 동작한다.

### 1.2 핵심 목표

1. 카메라로 문제를 간편하게 입력하고 필요한 문제 영역을 선택한다.
2. PaddleOCR이 사진의 글자를 인식하고, Gemma가 인식 텍스트를 바탕으로 문제를 풀이한다.
3. 정답만 제시하지 않고 관련 개념부터 설명한다.
4. 단계별 풀이와 최종 답을 제공한다.
5. 추가 질문에서 앞선 문제와 대화 맥락을 유지한다.
6. AI 추론은 스마트폰 내부에서 수행한다.
7. 모델 최초 다운로드 후에는 인터넷 없이 주요 학습 기능을 사용할 수 있도록 한다.
8. 회원가입, 자체 서버, 외부 AI API에 의존하지 않는다.

### 1.3 이번 버전에 제외하는 기능

- 공부 시간 타이머
- 집중 모드 및 화면 고정
- 앱 이탈 감지 및 공부 실패 기록
- 방해 금지 모드 제어
- 음성 대화 및 음성 답변
- 오답 노트 자동 생성 및 학습 통계 고도화
- 클라우드 AI로 자동 전환하는 기능

---

## 2. 앱 이름 및 프로젝트 식별 정보

| 구분 | 값 |
|---|---|
| 앱 표시 이름 | 나만의 AI쌤 |
| 영문 브랜드명 | My AI Tutor |
| 내부 프로젝트명 | `MyAITutor` |
| 프로젝트 폴더명 | `MyAITutor` |
| Android 모듈 | `app`, `ppocr-sdk` |
| 버전 산출 | `app/build.gradle.kts`에서 Git 커밋 수 기반 자동 산출 |
| applicationId | `com.donian.myaitutor` |

`versionCode`는 Git 커밋 수이며, `versionName`은 `0.<커밋 수 / 10>.<커밋 수 % 10>` 형식이다. `applicationId`와 `namespace`는 서로 다른 목적의 식별자다. Google Play에 최초 등록한 뒤에는 `applicationId`를 변경하지 않는 것을 원칙으로 한다.

---

## 3. 주요 사용자 흐름

1. 사용자가 앱을 실행한다.
2. 앱이 카메라 사용 권한과 AI 모델 설치 상태를 확인한다.
3. 모델이 설치되지 않았다면 모델 다운로드 화면으로 안내한다.
4. 사용자가 학습 과목을 선택하고 별도 카메라 화면에서 문제를 촬영한다.
5. 사진 확인 화면에서 필요한 경우 사각형으로 문제 영역을 선택한다.
6. 확인 후 PaddleOCR이 선택 영역 또는 전체 사진의 글자를 인식한다.
7. 인식 텍스트와 선택 과목, 학습 지침을 Gemma 4 E4B 모델에 전달한다.
8. Gemma가 텍스트 문제를 분석하고 개념 설명 및 단계별 풀이를 생성한다.
9. OCR 진행 상태, 인식 텍스트 및 Gemma 답변을 화면에 표시한다.
10. 사용자는 후속 질문을 입력하여 대화를 이어간다.
11. 새 문제를 촬영하면 새 문제 세션을 시작한다.

---

## 4. 화면 및 UI 명세

### 4.1 메인 화면 구성

메인 화면은 다음 네 영역으로 구성한다.

1. **상단 헤더:** 앱 이름 및 우측 상단 설정 아이콘
2. **카메라 영역:** 앱 내부 사각형 실시간 미리보기
3. **AI 영역:** AI 문제 풀이 버튼 및 풀이·대화 결과
4. **하단 과목 탭:** 과목 선택용 고정 내비게이션 영역

### 4.2 상단 헤더

- 좌측 또는 중앙에 `나만의 AI쌤` 앱 이름을 표시한다.
- 우측 상단에는 설정 아이콘 하나를 배치한다.
- 메인 화면에 불필요한 메뉴와 장식 요소를 추가하지 않는다.

### 4.3 카메라 미리보기 영역

- CameraX로 구현한다.
- 현재 구현은 별도 카메라 화면에서 후면 카메라 미리보기와 촬영을 제공한다.
- 후면 카메라를 기본으로 사용한다.
- 자동 초점과 자동 노출을 사용한다.
- 플래시 켜기/끄기 및 확대/축소는 UI 공간과 사용성을 검토해 제공한다.
- 카메라 프레임을 매번 AI에 보내지 않는다.
- 촬영 후 사진 확인 화면에서 분석할 문제 영역을 선택할 수 있다.
- 앱이 백그라운드로 이동하면 카메라 리소스를 해제하고, 복귀 시 다시 연결한다.

### 4.4 촬영 및 분석

- 촬영 후 사진을 확인하고 재촬영하거나 분석을 진행한다.
- 선택한 영역이 있으면 원본 사진에서 해당 영역을 잘라 PaddleOCR에 전달한다.
- 선택 영역이 없으면 전체 사진을 정규화한 뒤 PaddleOCR에 전달한다.
- Gemma에는 사진이 아니라 OCR 인식 텍스트를 전달한다.
- 분석 중에는 진행 상태를 표시하고 중복 요청을 방지한다.
- 사용자가 취소할 수 있는 안전한 취소 동작을 제공한다.
- 분석 실패 시 원인을 안내하고 재촬영 또는 재시도를 제공한다.

### 4.5 AI 답변 및 대화 영역

- AI 풀이를 카메라 아래쪽에 표시한다.
- 긴 답변은 세로 스크롤로 확인한다.
- 답변은 개념, 풀이, 최종 답변 등 내용별로 구분한다.
- 최초 풀이 후 추가 질문 입력창을 제공한다.
- 추가 질문에서도 현재 문제와 이전 대화 문맥을 유지한다.
- 새 문제를 인식하면 이전 문제와 현재 문제를 구분한다.

### 4.6 하단 과목 탭

하단에 고정하고 가로 스크롤 또는 적절한 과목 탐색 UI로 모든 과목에 접근할 수 있도록 한다.

초기 과목 분류:

| 대분류 | 세부 분야 예시 |
|---|---|
| 국어 | 문학, 비문학, 문법, 독해 |
| 수학 | 산수, 대수, 기하, 미적분, 확률과 통계 |
| 영어 | 단어, 문법, 독해, 작문 |
| 과학 | 물리, 화학, 생명과학, 지구과학 |
| 사회 | 정치, 경제, 지리, 사회문화 |
| 역사 | 한국사, 세계사 |
| 도덕·윤리 | 도덕, 윤리 |
| 기술·가정 | 기술, 가정 |
| 정보 | 컴퓨터, 정보과학 |
| 기타 | 제2외국어 및 기타 학습 분야 |

과목 선택값은 AI 요청에 포함한다. 다만 이미지 속 문제 내용과 선택한 과목이 다르면 문제 내용을 우선 분석하고 불일치를 사용자에게 알려준다.

### 4.7 설정 화면

우측 상단 설정 아이콘을 통해 진입한다.

- AI 모델 관리
- AI 선생님 설정
- 설명 수준 및 풀이 상세도
- 카메라 설정
- 언어 및 응답 설정
- 학습 기록
- 개인정보 및 로컬 데이터 관리
- 앱 정보 및 오픈소스 라이선스

---

## 5. AI 모델 및 실행 환경

### 5.1 확정 모델

**풀이 모델: Gemma 4 E4B-IT LiteRT-LM**

- Hugging Face 저장소: <https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm>
- 실행 엔진: Google LiteRT-LM
- Android 연결: LiteRT-LM Android/Kotlin API
- 모델 목적: PaddleOCR이 인식한 텍스트와 질문을 바탕으로 문제를 이해하고 답변 생성
- 사용 범위: 문제 분석, 설명 생성, 후속 대화

Gemma는 이미지 속 글자를 직접 읽는 OCR 역할을 하지 않는다. 사진 텍스트 인식은 아래의 PaddleOCR 단계에서 처리한다.

### 5.2 OCR 모델 및 SDK

- Android 모듈: `:ppocr-sdk` (PaddleOCR Android SDK 소스 포함)
- 한국어 인식 모델: `korean_PP-OCRv5_mobile_rec` (`ppocr-sdk/src/main/assets/models/rec/inference.yml`)
- 검출 모델: `ppocr-sdk/src/main/assets/models/det/inference.onnx` (세부 모델 ID는 저장소 설정에서 확인되지 않음)
- 인식 모델 파일: `ppocr-sdk/src/main/assets/models/rec/inference.onnx`
- 런타임: ONNX Runtime Android 1.21.1, OpenCV Android 4.13.0
- 출처: [PaddleOCR Android SDK](https://github.com/PaddlePaddle/PaddleOCR/tree/main/deploy/ppocr-android), [한국어 텍스트 인식 모델 문서](https://www.paddleocr.ai/latest/version3.x/module_usage/text_recognition.html)
- OCR 모델 파일은 앱 assets에 포함되며, 분석은 기기 안에서 수행한다.

모델 정확도를 보장하지 않는다. 실제 문제 사진으로 검출·인식 오류를 확인하고, 사용자가 인식 텍스트를 검토할 수 있도록 한다.

### 5.3 모델 사용 시 주의사항

- PaddleOCR은 복잡한 손글씨, 작은 글씨, 흐린 이미지, 겹친 문장, 수식, 도형·그래프를 잘못 인식할 수 있다.
- Gemma는 인식 결과가 불완전하면 문제를 오해하거나 잘못된 답을 생성할 수 있다.
- AI는 불확실한 문제를 임의로 보완해 정답을 만들어내지 않아야 한다.
- 실제 한국어 교과서와 문제집으로 인식률 및 풀이 정확도를 측정한다.
- 문제가 충분히 읽히지 않으면 재촬영을 요청한다.
- 모델 파일 크기, 입력 해상도, 컨텍스트 길이, 메모리 필요량은 실제 배포 파일 및 기기 테스트 결과로 확정한다. 다운로드 안내에는 확인된 파일 크기를 사용한다.

### 5.4 추론 방식

- 온디바이스 추론을 기본으로 한다.
- 외부 AI API를 호출하지 않는다.
- 카메라 이미지를 클라우드로 전송하지 않는다.
- 모델 최초 다운로드와 업데이트에는 인터넷이 필요하다.
- 모델 설치 후 이미지 분석과 AI 답변 생성은 오프라인으로 작동하도록 한다.
- 네트워크 장애 시 클라우드 모델로 자동 전환하지 않는다.

### 5.5 AI 모델 관리

최초 실행 또는 AI 사용 전 다음 절차를 제공한다.

1. 모델 설치 상태 확인
2. 저장 공간 확인
3. 모델 다운로드 안내
4. 다운로드 진행률 표시
5. 다운로드 중단 및 재개 지원
6. 다운로드 완료 후 파일 무결성 확인
7. 모델 로딩 및 간단한 추론 점검
8. 준비 완료 표시

설정에서는 모델 버전, 설치 상태, 저장 공간, 업데이트 및 삭제 기능을 제공한다.

모델이 설치되지 않아도 메인 UI와 카메라 미리보기는 사용할 수 있다. AI 풀이를 요청하면 모델 설치를 안내한다.

---

## 6. 이미지 입력 및 문제 분석 흐름

사진에서 PaddleOCR이 텍스트를 추출한 뒤, 추출된 텍스트와 과목·학습 지침을 Gemma 4 E4B에 전달한다. Gemma에는 원본 이미지가 전달되지 않는다.

### 6.1 처리 단계

1. CameraX `ImageCapture`로 정지 이미지를 촬영한다.
2. 사용자는 사진 확인 화면에서 문제 영역을 사각형으로 선택할 수 있다. 선택하지 않으면 전체 사진을 사용한다.
3. 선택한 경우 EXIF 방향을 적용한 원본 사진에서 선택 영역을 그대로 잘라 OCR에 전달한다. 선택하지 않은 경우 이미지 방향을 보정하고 장변 1600px 기준으로 정규화한다.
4. PaddleOCR이 이미지에서 글자를 검출·인식한다. 결과가 비어 있으면 Gemma를 호출하지 않고 재촬영을 안내한다.
5. 앱은 OCR 인식 텍스트를 화면에 표시하고, 해당 텍스트와 과목·학습 지침을 LiteRT-LM 대화 입력으로 전달한다.
6. Gemma 답변을 스트리밍으로 수신해 화면에 표시한다. 사용자가 위쪽을 읽고 있으면 스크롤 위치를 유지한다.
7. 촬영 및 중간 이미지는 분석 종료 후 정리한다.

### 6.2 사진 영역 선택과 품질 처리

- 사진 확인 화면에서 사용자가 드래그한 사각형을 기준으로 OCR 입력을 자른다.
- 선택 영역은 원본 픽셀 해상도를 유지하며 별도 리사이즈하지 않는다.
- 사용자가 선택 영역을 지정하지 않으면 전체 사진을 대상으로 OCR을 실행한다.
- OCR 결과를 확인할 수 있게 제공하며, 잘못 인식된 경우 재촬영 또는 영역 재선택을 안내한다.

다음 상황을 사용자에게 안내한다.

- 사진 흔들림
- 초점 불량
- 과도한 반사 및 그림자
- 문제 일부 잘림
- 글자 크기 부족
- 이미지 해상도 부족

이미지 품질을 AI가 판단할 수 있도록 지시하되, 앱 차원에서 초점·해상도 등 명확하게 확인할 수 있는 항목은 카메라 상태와 함께 점검한다.

### 6.3 OCR 확장 및 정확도 관리

현재 PaddleOCR을 사용한다. OCR 교체·추가 시 다음 기준을 적용한다.

추가 모듈은 다음 기준을 모두 충족해야 한다.

- Android에서 직접 실행 가능한 SDK, 라이브러리 또는 검증된 Android 구현 제공
- 모델 설치 후 오프라인 추론 가능
- 상업적 사용이 가능한 라이선스 확인
- 인쇄된 교과서 및 문제집 입력 지원
- 실제 기기에서 속도와 메모리 검증

PC용 모델이나 Python 전용 구현만 존재하는 후보는 Android 적용 후보로 선정하지 않는다.

---

## 7. AI 선생님 응답 정책

### 7.1 기본 학습 방식

**상세 풀이와 대화형 학습을 결합한다.**

처음 문제를 제출하면 개념부터 정답까지 충분히 설명하고, 이후 사용자가 질문하면 같은 문제의 문맥을 유지하며 답변한다.

### 7.2 기본 답변 순서

1. 문제 파악: 문제에서 요구하는 내용과 조건을 정리한다.
2. 기초 개념: 문제를 풀기 전에 필요한 개념을 쉽게 설명한다.
3. 핵심 원리: 어떤 공식이나 원리가 필요한지와 이유를 설명한다.
4. 문제 적용: 해당 개념을 문제에 적용한다.
5. 단계별 풀이: 계산 또는 논리 전개를 순서대로 설명한다.
6. 최종 답: 정답 또는 핵심 결론을 명확히 표시한다.
7. 검토: 계산, 조건, 근거를 확인하고 불확실성을 표시한다.
8. 이해 확인: 필요하면 핵심 요약이나 확인 질문을 제시한다.

과목 특성상 적절하지 않은 항목은 생략하거나 해당 과목에 맞게 바꾼다. 예를 들어 역사 문제는 계산 검산 대신 사료 근거와 사건의 인과관계를 검토한다.

### 7.3 말투 및 교육 원칙

- 자연스러운 한국어 존댓말
- 친절하고 차분한 선생님 말투
- 학생의 이해 수준에 맞춘 설명
- 전문 용어를 사용하기 전에 쉬운 표현으로 설명
- 정답만 나열하지 않고 풀이 이유 제시
- 과도하게 장황한 반복을 피함
- 불확실한 정보는 확실한 것처럼 말하지 않음
- 잘못 인식한 수식이나 조건을 임의로 추측하지 않음

### 7.4 대화 문맥

- 문제별 대화 세션을 구분한다.
- 후속 질문에는 현재 문제와 이전 답변을 함께 반영한다.
- 새 문제를 제출하면 새 문제 세션을 생성한다.
- 사용자가 이전 문제를 선택하면 해당 대화를 복원한다.
- 긴 대화는 모델 컨텍스트 한도에 맞게 요약하고 핵심 문제 조건과 풀이 기록을 보존한다.

---

## 8. AI 응답 데이터 구조

가능하면 AI 응답은 아래 구조로 정리해 UI가 일정하게 표시되도록 한다.

| 필드 | 설명 |
|---|---|
| `subject` | 과목 |
| `recognized_question` | AI가 읽은 문제 원문 또는 요약 |
| `question_summary` | 문제 요구사항 요약 |
| `core_concepts` | 관련 기초 개념 |
| `principles` | 핵심 원리 및 공식 |
| `solution_steps` | 단계별 풀이 |
| `final_answer` | 최종 답 |
| `verification` | 검산 또는 근거 확인 |
| `uncertainty_notes` | 인식 및 풀이의 불확실성 |
| `needs_retake` | 재촬영 필요 여부 |

모델의 원시 텍스트 응답이 구조화 형식과 일치하지 않을 수 있으므로 파싱 실패 시 원문 답변을 안전하게 표시하는 대체 처리를 제공한다.

---

## 9. 기술 스택

| 영역 | 기술 |
|---|---|
| 개발 언어 | Kotlin |
| UI | Jetpack Compose |
| 디자인 | Material 3 |
| 카메라 | CameraX |
| 사진 촬영 | ImageCapture |
| AI 추론 | LiteRT-LM Android/Kotlin API |
| AI 모델 | Gemma 4 E4B-IT LiteRT-LM |
| OCR | PaddleOCR Android SDK (`:ppocr-sdk`) |
| OCR 런타임 및 이미지 처리 | ONNX Runtime Android 1.21.1, OpenCV Android 4.13.0 |
| 비동기 처리 | Kotlin Coroutines / Flow |
| 화면 상태 | ViewModel |
| 학습 기록 | Room |
| 앱 설정 | DataStore |
| 모델 다운로드 | WorkManager 기반 다운로드 작업 또는 검증된 다운로드 구성 |

LiteRT-LM의 실제 버전, 최소 Android API, 모델 아티팩트 형식 및 의존성은 프로젝트 생성 시점의 공식 Android 문서와 해당 Hugging Face 저장소 안내를 대조해 확정한다. 호환성을 확인하지 않은 버전 번호를 임의로 고정하지 않는다.

---

## 10. 권한 및 개인정보

### 10.1 주요 권한

- `CAMERA`: 카메라 미리보기 및 사진 촬영
- `INTERNET`: 최초 모델 다운로드 및 업데이트
- `POST_NOTIFICATIONS`: 선택적 다운로드 완료 또는 오류 알림

실제 구현에 필요한 권한만 선언한다. 마이크, 위치, 연락처, 접근성 서비스 등 불필요한 권한은 사용하지 않는다.

### 10.2 데이터 저장 정책

| 데이터 | 저장 위치 | 기본 정책 |
|---|---|---|
| AI 모델 파일 | 앱 내부 파일 저장소 | 설치 후 로컬 보관 |
| 사용자 설정 | DataStore | 로컬 저장 |
| 문제 및 대화 기록 | Room | 로컬 저장 |
| 촬영 이미지 | 임시 앱 내부 저장소 또는 메모리 | 기본 자동 보관하지 않음 |
| 추론 중간 데이터 | 메모리 및 임시 저장소 | 작업 종료 후 정리 |

- 이미지 및 대화 내용을 외부 AI 서버로 전송하지 않는다.
- 학습 기록은 사용자가 삭제할 수 있어야 한다.
- 임시 촬영 이미지는 분석 완료 후 기본적으로 삭제한다.
- 로그에 문제 사진, 개인정보, 전체 대화 내용을 불필요하게 기록하지 않는다.
- 모델 다운로드 외의 네트워크 접근을 최소화한다.

---

## 11. 오류 및 예외 처리

| 상황 | 앱 동작 |
|---|---|
| 카메라 권한 거부 | 권한 안내 및 설정 이동 안내 |
| 카메라 사용 중 오류 | 오류 안내 및 재시도 |
| AI 모델 미설치 | 모델 다운로드 화면 표시 |
| 저장 공간 부족 | 필요한 공간 안내 및 다운로드 중단 |
| 모델 다운로드 실패 | 재시도 및 다운로드 재개 |
| 모델 로딩 실패 | 오류 로그 저장 및 재시도 안내 |
| 기기 메모리 부족 | 추론 중단, 앱 안정성 보호 및 안내 |
| 이미지가 흐림 | 재촬영 요청 |
| OCR 결과가 비어 있음 | OCR을 건너뛰고 재촬영 안내 |
| OCR 인식 오류 | 인식 텍스트를 확인할 수 있게 하고 사진 영역 재선택 또는 재촬영 유도 |
| 수식·문제 인식 불확실 | 불확실한 부분을 표시하고 확인 요청 |
| AI 응답 생성 실패 | 재시도 및 문제 세션 유지 |
| 앱 프로세스 종료 | 저장된 대화 및 설정 복원 |
| 오프라인 상태 | 설치된 모델로 추론, 미설치 모델은 다운로드 안내 |

클라우드 AI로 자동 전환하지 않는다.

---

## 12. 개발 단계

### 1단계 — 프로젝트 기반

- 프로젝트명 `MyAITutor`
- 앱 표시 이름 `나만의 AI쌤`
- 버전 `0.0.1`
- Gradle 및 모듈 구성
- 테마와 색상 시스템
- 기본 내비게이션

### 2단계 — 메인 UI

- 상단 헤더 및 설정 아이콘
- 카메라 사각형 영역
- AI 문제 풀이 버튼
- AI 결과 영역
- 하단 과목 탭

### 3단계 — 카메라

- CameraX 권한 및 생명주기
- 미리보기
- 사진 촬영
- 초점·노출·플래시 제어
- 이미지 회전 및 크기 조정

### 4단계 — PaddleOCR 및 Gemma 4 E4B 연결

- 모델 다운로드 및 관리
- 모델 무결성 검사
- LiteRT-LM 초기화
- PaddleOCR SDK 및 모델 연결
- 이미지 또는 선택 영역의 텍스트 인식
- OCR 인식 텍스트를 Gemma에 전달
- 스트리밍 응답
- 모델 로딩 및 추론 오류 처리

### 5단계 — AI 선생님

- 과목별 지시문
- 개념 중심 설명
- 단계별 풀이
- 최종 답 및 검토
- 추가 질문과 대화 문맥 유지

### 6단계 — 로컬 데이터

- Room 학습 기록
- 문제별 대화 세션
- 대화 복원
- DataStore 설정
- 삭제 및 개인정보 관리

### 7단계 — 품질 검증

- 한국어 교과서 인식
- 영어 문제 인식
- 수학 수식 및 복잡한 기호
- 그래프·표·도형
- 전 과목 설명 품질
- 추가 질문 문맥 유지
- 메모리·발열·배터리
- 오프라인 실행

### 8단계 — 배포 준비

- 앱 아이콘 및 스토어 이미지
- 개인정보처리방침
- 오픈소스 고지
- 모델 라이선스 및 배포 조건 검토
- 서명 및 AAB 생성
- Google Play 정책 확인

---

## 13. 테스트 및 출시 기준

다음 항목은 실제 기기에서 확인한 뒤 출시 여부를 판단한다.

### 기능 테스트

- 앱 내부 사각형 영역에서 카메라가 표시되는가?
- 카메라 미리보기 중 UI가 정상적으로 반응하는가?
- 버튼을 누른 시점의 이미지가 정확히 전달되는가?
- AI 답변 중 카메라와 화면이 안정적으로 유지되는가?
- 과목 선택이 AI 입력에 반영되는가?
- 추가 질문이 이전 문제의 문맥을 유지하는가?
- 새 문제와 이전 문제가 구분되는가?

### AI 품질 테스트

- 한국어 인쇄 문제 인식
- 영어 문제 인식
- 분수·지수·루트·적분 등 수학 수식 이해
- 표와 그래프 해석
- 과학 삽화와 도형 해석
- 개념 설명의 정확성
- 단계별 계산 및 논리의 정확성
- 근거 없는 답변과 환각 발생 여부
- 인식 실패 시 재촬영 유도

### 기기 성능 테스트

- 모델 로딩 시간
- 첫 응답 시간
- 생성 속도
- 최대 RAM 점유
- GPU/CPU 호환성
- 발열 및 장시간 실행
- 배터리 소모
- 카메라와 AI 동시 사용 안정성
- 모델 설치 후 비행기 모드에서 정상 추론 여부

정확도 및 응답 속도 목표는 임의로 정하지 않는다. 실제 문제 테스트 세트를 만들고 측정한 결과를 기준으로 목표치를 확정한다.

---

## 14. 라이선스 및 배포 유의사항

- Gemma 4 E4B-IT LiteRT-LM 모델 저장소에 포함된 라이선스 파일과 모델 사용 조건을 배포 시점에 다시 확인한다.
- 모델 파일을 앱과 함께 재배포하는지, 최초 실행 시 별도로 다운로드하는지에 따라 배포 의무를 확인한다.
- LiteRT-LM, CameraX, Jetpack Compose 및 기타 종속성의 라이선스 고지를 관리한다.
- PaddleOCR SDK, 포함된 OCR 모델, ONNX Runtime 및 OpenCV의 라이선스 고지를 관리한다.
- 앱에 포함하거나 다운로드하는 모든 제3자 구성요소를 `THIRD_PARTY_NOTICES.md`에 기록한다.
- Google Play 등록 시 AI 생성 결과의 한계와 개인정보 처리 방식을 명확히 고지한다.

---

## 15. 초기 버전 0.0.1의 완료 범위

0.0.1은 기본 사용 흐름과 온디바이스 모델 연결 가능성을 검증하는 초기 버전이다.

### 필수 포함

- `나만의 AI쌤` 앱 이름 및 기본 디자인
- 카메라 미리보기 영역
- AI 문제 풀이 버튼
- 하단 과목 탭
- 우측 상단 설정
- Gemma 4 E4B LiteRT-LM 모델 다운로드 및 설치 관리
- PaddleOCR 이미지 인식 및 인식 텍스트 기반 AI 질의
- 개념 설명 및 단계별 풀이 프롬프트
- 후속 질문 기능의 기본 구조
- 로컬 오류 처리

### 0.0.1에서 완성 보장을 하지 않는 항목

- 모든 과목 및 모든 문제 유형에서 높은 정답률
- 전문 수식 OCR 수준의 완벽한 LaTeX 변환
- 모든 Android 기기에서 동일한 성능
- 모든 기기에서 GPU 가속 지원
- 복잡한 도형·그래프의 완전한 해석

이 항목들은 실기기 및 실제 학습 문제 테스트 결과를 바탕으로 후속 버전에서 개선한다.

---

## 16. 최종 제품 정의

**나만의 AI쌤(My AI Tutor)**은 사용자가 카메라로 문제를 보여주면 PaddleOCR이 글자를 인식하고, 스마트폰 내부의 Gemma 4 E4B 온디바이스 AI가 인식 텍스트를 바탕으로 관련 개념부터 단계별 풀이까지 설명하는 자기주도 학습 앱이다.

사용자는 촬영 사진에서 필요한 문제 영역만 선택할 수 있고, OCR 인식 결과를 검토할 수 있다. 사진과 OCR 결과는 외부 AI 서버로 전송하지 않는다.

**핵심 개발 우선순위:**

1. 정확한 카메라 이미지 입력
2. 안정적인 온디바이스 모델 실행
3. 한국어 문제 및 수식 이해
4. 교육적으로 정확한 개념·풀이 설명
5. 대화 문맥 유지
6. 다양한 Android 기기에서의 안정성

---

# 부록 A. 실시간 AI 선생님 스트리밍 기능 — 구현 계획 및 지시서

> 본 부록은 구현계획서.md의 내용을 개발기획서에 통합한 것입니다.
> 실시간 AI 선생님 스트리밍 채팅 기능의 상세 구현 지시서를 포함합니다.

# MyAITutor 실시간 AI 선생님 스트리밍 기능 — 최종 구현 지시서

현재 Android Studio에서 열려 있는 `MyAITutor` 프로젝트에 실시간 AI 선생님 스트리밍 채팅 기능을 구현한다.

기존 프로젝트의 구조와 기능을 최대한 유지하면서 기존 PaddleOCR, Gemma 4 E4B 및 LiteRT-LM 엔진을 재사용한다.

이 지시서의 제약 조건과 완료 기준을 우선 적용한다.

---

# 0. 현재 프로젝트 기준 정보

다음은 현재 프로젝트에서 확인된 기준이다. 실제 코드가 이 내용과 다르면 해당 파일을 다시 확인하고 변경된 사실을 먼저 보고한다.

| 항목 | 확인된 내용 |
|---|---|
| LiteRT-LM | `com.google.ai.edge.litertlm:litertlm-android:0.18.0` |
| AI 모델 | Gemma 4 E4B-IT `.litertlm` |
| Assistant 인터페이스 | 콜백 방식 `onTextChanged: suspend (String) -> Unit` |
| 스트리밍 | `sendMessageAsync(contents).collect { message -> accumulated.append(message.toString()) }` |
| Thought 채널 | 현재 미사용 |
| Thinking 설정 | 현재 미적용 |
| ConversationConfig | `systemInstruction`, `samplerConfig`, `maxOutputToken = 2048` |
| 대화 문맥 상한 | `MAX_CONVERSATION_TOKENS = 8_000` |
| 답변 단일 원본 | `HomeUiState.answer` |
| 학년별 프롬프트 | 아직 적용되지 않음 |
| 단위 테스트 | JUnit 4.13.2, Coroutines Test 1.11.0 |
| Compose UI 테스트 | 현재 의존성 미설정 |
| SDK | minSdk 26, targetSdk 36, compileSdk 37 |
| ABI | arm64-v8a, x86_64 |

기존 코드의 다음 구조를 유지한다.

- `GemmaAssistant`가 LiteRT-LM Engine을 소유한다.
- 활성화된 Conversation은 하나만 유지한다.
- GPU 초기화 실패 시 기존 CPU 폴백 경로를 유지한다.
- 기존 `cancelGeneration()`의 네이티브 취소 기능을 유지하면서 안전성을 개선한다.
- 기존 `AssistantAnswer`와 LaTeX 렌더링을 재사용한다.

# 1. 절대 변경 금지 사항

다음은 반드시 준수한다.

1. 기존 PaddleOCR 코드, 클래스, 패키지, 초기화, 실행 방식, 반환 데이터 구조를 수정하지 않는다.
2. Gemma 4 E4B 모델을 중복 로딩하지 않는다.
3. LiteRT-LM Engine을 중복 생성하지 않는다.
4. Gemma에는 PaddleOCR가 추출한 텍스트만 전달한다.
5. Gemma에 원본 촬영 이미지나 이미지 데이터를 전달하지 않는다.
6. 클라우드 API 및 원격 AI 추론을 추가하지 않는다.
7. 최초 모델 다운로드 이후 OCR과 AI 추론은 완전 오프라인으로 작동해야 한다.
8. 기존 카메라, 과목 선택, OCR, 모델 다운로드, 대화 이력, 답변 렌더링 기능을 유지한다.
9. 기존 화면 전체를 불필요하게 재작성하지 않는다.
10. 기존 기능을 제거하거나 임의로 단순화하지 않는다.

# 2. LiteRT-LM 0.18.0 공식 API 적용

LiteRT-LM 0.18.0의 공식 Kotlin API를 기준으로 구현한다.

다음 API는 공식 소스에서 확인된 항목이다.

- `Conversation.sendMessageAsync()`
- `Flow<Message>` 반환
- `Message.channels`
- `Message.toString()`
- `ThinkingConfig`
- `ConversationConfig.channels`
- 요청별 `thinkingConfig`
- 요청별 `maxOutputToken`
- `Conversation.cancelProcess()`

참고 소스:

https://github.com/google-ai-edge/LiteRT-LM

https://github.com/google-ai-edge/LiteRT-LM/blob/b2f686e2ed4718fb84ec398a61dd59ca0f0aff27/kotlin/java/com/google/ai/edge/litertlm/Conversation.kt

https://github.com/google-ai-edge/LiteRT-LM/blob/b2f686e2ed4718fb84ec398a61dd59ca0f0aff27/kotlin/java/com/google/ai/edge/litertlm/Config.kt

Google AI Edge Gallery 참고:

https://github.com/google-ai-edge/gallery/blob/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatModelHelper.kt

## 2.1 Thought / Answer 수신 방식

Google AI Edge Gallery와 동일한 개념으로 처리한다.

- `message.toString()`은 일반 답변 텍스트로 사용한다.
- `message.channels[THOUGHT_CHANNEL]`은 Thought 채널 확인에 사용한다.
- Thought 채널의 원문은 사용자 UI에 전달하지 않는다.

중요한 사항:

`Message.channels`의 값은 별도 채널의 텍스트다. 이 텍스트를 해설 답변과 합치거나 화면에 표시하지 않는다.

`message.toString()`과 `message.channels`를 별도로 처리한다.

## 2.2 ConversationConfig 채널 설정

`ConversationConfig.channels`의 동작을 확인하고 적용한다.

- `null`: 모델 메타데이터의 기본 채널 설정 사용
- 빈 목록: 채널 비활성화
- 명시적 채널 목록: 지정한 채널 사용

현재 Gemma 4 E4B 모델 메타데이터에서 Thought 채널이 기본 제공되는지 확인한다.

기본 설정으로 제공된다면 기존 설정을 유지한다.

제공되지 않는다면 모델 메타데이터와 공식 토큰 규격을 확인한 뒤 정확한 `Channel` 설정을 적용한다.

채널 시작·종료 토큰을 추측해서 임의로 작성하지 않는다.

## 2.3 ThinkingConfig

다음 공식 속성을 사용한다.

- `enableThinking`
- `thinkingTokenBudget`

학년별 Thinking 설정은 `Conversation`이나 Engine을 다시 생성하지 않고 `sendMessageAsync()` 요청별 설정으로 전달한다.

Qwen 전용 문법인 `<think>`, `/no_think`를 시스템 프롬프트에 사용하지 않는다.

# 3. 스트리밍 및 상태 관리 아키텍처

전체 구조는 다음을 유지한다.

```text
[카메라]
    |
    v
[PaddleOCR - 기존 코드 그대로]
    |
    v
[OCR 추출 텍스트]
    |
    v
[HomeViewModel (추론 요청 및 진행 상태 단일 소유자)]
    |
    v
[기존 GemmaAssistant]
    |
    v
[기존 LiteRT-LM Engine]
    |
    v
[기존 활성 Conversation]
    |
    v
[Thought / Answer 채널 분리]
    |
    v
[TutorStreamParser]
    |
    +------ Thought 원문 폐기
    |
    +------ 공개 가능한 진행 이벤트
    |
    v
[기존 HomeUiState.answer에 Answer 누적]
    |
    v
[TutorResultScreen]
```

## 3.1 상태 소유 원칙

기존 `HomeUiState.answer`를 실제 해설 텍스트의 유일한 원본으로 유지한다.

새로운 `TutorUiState.accumulatedAnswer`를 만들어 별도로 답변을 누적하지 않는다.

`TutorUiState`에는 다음과 같은 UI 진행 정보만 관리한다.

- `isGenerating`
- `currentStatus`
- `progressStage`
- `progressMessages`
- `isCompleted`
- `errorMessage`
- `isCancelled`

답변 텍스트는 반드시 기존 `HomeUiState.answer`에서 가져온다.

## 3.2 학년 상태 소유 원칙

`selectedGrade`는 `HomeUiState`에 단일 원본으로 둔다.

학년 상태를 별도 클래스나 별도 ViewModel에 중복으로 만들지 않는다.

- 화면의 학년 칩은 `HomeUiState.selectedGrade`를 표시한다.
- 학년 변경은 기존 `HomeViewModel` 메서드로 처리한다.
- 추론 요청 시 선택된 학년을 `HomeViewModel`이 직접 읽어 프롬프트와 Thinking 설정에 반영한다.
- 현재 프로젝트에 학년 설정 저장 기능이 없다면 별도 DataStore를 추가하지 말고 우선 앱 실행 중 상태로 관리한다.

기본 학년을 임의로 정하지 말고 현재 앱의 설정 또는 화면 기본값 정책을 확인해 적용하고 결과를 보고한다.

## 3.3 ViewModel 소유 구조 (TutorViewModel 미도입)

별도의 `TutorViewModel`을 만들지 않는다. 튜터 진행 상태와 추론 요청 제어는 모두 기존 `HomeViewModel`에 통합한다.

이유는 현재 `HomeViewModel`이 이미 추론 제어에 필요한 모든 것을 소유하고 있기 때문이다.

- `generationJob: Job?` — 활성 추론 Job
- `generationId: Int` — 늦은 결과 차단용 키
- `analyzeImage()` — 최초 문제 풀이 호출부
- `sendQuestion()` — 후속 질문 호출부
- `cancelGeneration()` — `generationJob.cancel()` + `assistant.cancelGeneration()`

진행 상태와 요청 제어를 분리하면 하나의 추론 요청에 소유자가 둘이 되어 다음이 깨진다.

- 취소 완료 확인 주체와 활성 요청 추적 주체가 달라져 4항의 "취소 완료 전 다음 추론 금지"를 보장할 수 없음
- 스트림 수집 위치와 답변 누적 위치가 달라져 3.1의 답변 단일 원본이 깨짐
- `isGenerating`과 `answer`가 다른 소유자에게 있어 오류 후 UI 상태 복구가 원자적으로 불가능

따라서 다음을 규칙으로 한다.

- 추론 요청 생성, 활성 Job, `generationId`, 취소 처리는 모두 `HomeViewModel`이 소유한다.
- 튜터 스트림 수집도 `HomeViewModel`이 수행한다.
- `TutorStreamParser`와 `LlmInferenceEngine`은 `HomeViewModel`이 사용하는 순수 컴포넌트로 유지한다.
- `HomeViewModel`은 진행 상태를 `HomeUiState`에 합성해 `TutorResultScreen`에 단방향으로 내보낸다.
- `TutorResultScreen`은 이벤트를 `HomeViewModel` 메서드 호출로 되돌리고, 화면 자체는 상태를 소유하지 않는다.

다음 중복은 어떤 경우에도 만들지 않는다.

- 독립적인 GemmaAssistant 생성
- 별도의 LiteRT-LM Engine 생성
- 별도의 Conversation 생성
- 별도의 답변 문자열 누적
- 동일 요청에 대한 중복 실행 또는 중복 취소 처리

이 구조는 부수적으로 다음을 해결한다. 별도 ViewModel이 없으므로 ViewModel 간 이벤트 전달 경로 자체가 존재하지 않고, 10.1의 튜터 테스트는 이미 JVM 단위 테스트로 구성되어 있는 기존 `HomeViewModelTest`에 통합할 수 있다.

# 4. 반드시 해결해야 하는 취소 및 동시 실행 문제

이 부분은 기존 프로젝트에서 매우 중요하므로 특별히 주의한다.

현재 `cancelGeneration()`은 네이티브 취소 플래그를 즉시 설정하지만 실제 종료는 비동기 방식이다.

또한 LiteRT-LM 0.18.0의 `sendMessageAsync()` Flow 구현은 `callbackFlow`를 사용하며, 해당 Flow의 `awaitClose` 정리 블록이 비어 있다.

따라서 **Flow collector의 Job을 취소하는 것만으로 네이티브 모델 추론까지 자동 중단된다고 가정하면 안 된다.**

공식 구현에서 `Conversation.cancelProcess()`는 별도로 제공된다.

https://github.com/google-ai-edge/LiteRT-LM/blob/b2f686e2ed4718fb84ec398a61dd59ca0f0aff27/kotlin/java/com/google/ai/edge/litertlm/Conversation.kt

## 필수 구현 규칙

1. 사용자가 중단 버튼을 누르면 기존 취소 경로를 통해 `conversation.cancelProcess()`를 실제 호출한다.
2. 단순히 Compose UI 상태나 Coroutine Job만 취소하고 끝내지 않는다.
3. 네이티브 추론이 종료됐다는 신호를 확인한다.
4. 정상 종료 또는 취소 완료 콜백을 확인한 뒤 현재 요청을 종료 처리한다.
5. 동일한 Conversation에 다음 추론을 시작하는 것은 기존 추론의 종료가 확인된 이후에만 허용한다.
6. 취소 중 새 요청이 들어오면 새 요청을 대기시키거나 명시적으로 거부한다.
7. 이전 요청의 늦은 결과가 새로운 결과를 덮어쓰지 못하게 `generationId`를 확인한다.
8. 취소 처리와 일반 오류 처리를 분리한다.

가능하면 `CompletableDeferred`, 단일 요청 `Mutex`, 활성 요청 상태 등을 활용해 안전한 직렬 실행을 구현한다.

단, Mutex만으로 네이티브 종료가 보장된다고 가정하지 않는다. 실제 SDK 콜백의 종료 확인과 연결해야 한다.

사용자가 취소한 경우 다음 요청을 즉시 같은 Conversation에 보내지 않는다.

취소 완료를 확인할 수 없는 경우에는 안전하지 않은 상태에서 새 추론을 시작하지 말고 오류 상태와 복구 절차를 표시한다.

# 5. Conversation 생성 및 교체 정책

현재 `GemmaAssistant`가 Engine과 활성 Conversation을 관리하는 구조를 유지한다.

**정책: Engine 1개 + 활성 Conversation 1개**

- 최초 초기화 시 활성 Conversation 하나 생성
- 일반적인 문제 풀이와 후속 질문에서는 기존 Conversation 재사용
- `resetConversation()` 호출 시 기존 Conversation을 먼저 닫고 새 Conversation 하나로 교체
- 기존 Conversation이 닫히기 전에 새 Conversation을 병렬 생성하지 않음
- RealGemmaTutorEngine은 Conversation을 새로 생성하지 않음
- 추론 요청마다 Conversation을 새로 생성하지 않음

Conversation 교체 중 추론이 진행 중이라면 먼저 해당 추론의 종료 또는 취소 완료를 확인한다.

# 6. 신규 파일 3개

다음 세 파일을 추가한다.

패키지:

`com.donian.myaitutor.tutor`

튜터 요청 제어와 진행 상태는 별도 ViewModel 파일이 아니라 3.3에 따라 기존 `HomeViewModel`에 통합한다. 해당 내용은 ③에 정리한다.
## ① TutorStreamParser.kt

역할:

- 스트림 이벤트 분류
- Thought 활동 이벤트 처리
- Answer 텍스트 이벤트 처리
- 진행 단계 변경
- 완료·취소·오류 상태 처리
- 예외적인 특수 문자열 누출 방지

필요한 데이터 구조:

- `TutorUiState`
- `TutorStreamChunk`
- `TutorProgressStage`
- 필요한 보조 상태 클래스

`TutorStreamChunk`는 다음 개념을 포함한다.

- Thought 활동 이벤트
- Answer 텍스트 조각
- 완료 이벤트
- 취소 이벤트
- 오류 이벤트

**중요:** Thought 이벤트에는 내부 추론 원문을 넣지 않는다.

예를 들어 Thought 채널을 수신하면 `ThoughtActivity`처럼 원문 없는 이벤트만 전달한다.

`TutorStreamParser`는 SDK의 `Message.channels`를 직접 파싱하기보다는 SDK 종속성을 제거한 스트림 이벤트를 입력받는 순수 처리 계층으로 구성한다.

실제 LiteRT-LM Message의 채널 분리는 `GemmaAssistant` 또는 RealGemmaTutorEngine 어댑터에서 수행한다.

특수 토큰 문자열 필터링은 채널 메타데이터 분리가 정상 작동하지 않는 예외에 대한 방어 수단으로만 사용한다.

일반 사용자 텍스트에 포함된 문자열을 무조건 삭제하지 않도록 주의한다.

청크 경계에 걸쳐 특수 구분자가 나뉘어 들어오는 예외도 테스트한다.

`TutorUiState`에는 답변 전체를 중복 저장하지 않는다.

## ② LlmInferenceEngine.kt

역할:

- 추론 엔진 공통 인터페이스
- 실제 Gemma 엔진 어댑터
- Fake 엔진

구성:

- `InferenceOptions`
- `LlmInferenceEngine`
- `RealGemmaTutorEngine`
- `FakeGemmaTutorEngine`

스트리밍은 Kotlin `Flow<TutorStreamChunk>`로 통일한다.

인터페이스는 OCR 최초 문제 풀이뿐 아니라 후속 질문도 처리할 수 있도록 요청 텍스트, 학년, 과목, 추론 옵션을 전달할 수 있게 설계한다.

`RealGemmaTutorEngine`은 기존 `GemmaAssistant` 인스턴스를 전달받아 사용한다.

새로운 모델, Engine, Conversation을 만들지 않는다.

`FakeGemmaTutorEngine`은 실제 모델을 초기화하지 않는 독립 구현체로 만든다.

Fake 엔진에서 다음을 검증할 수 있도록 한다.

- Thinking ON/OFF
- Thought 활동 이벤트
- Answer 실시간 출력
- 상태 단계 변화
- 정상 완료
- 오류
- 취소
- 재시작

Fake Thought 이벤트에도 원문을 포함하지 않는다.

## ③ HomeViewModel 튜터 통합 (기존 파일, 신규 파일 아님)

별도 ViewModel을 만들지 않고 다음을 기존 `HomeViewModel`에 통합한다.

책임:

- 학년별 추론 설정 구성
- 시스템 프롬프트 및 학년별 지침 구성
- 튜터 스트림 수집 및 진행 상태 갱신
- 추론 요청 제어
- 취소·오류 처리 및 UI 상태 복구

기존 자산과의 연결:

- 활성 Job과 `generationId`는 기존 필드를 그대로 사용한다. 새 필드를 만들지 않는다.
- `analyzeImage()`와 `sendQuestion()`의 기존 호출 경로를 유지하고 스트림 처리를 그 안에서 수행한다.
- `cancelGeneration()`은 기존 정의를 유지하고 네이티브 취소 완료 확인을 추가한다.
- 답변은 기존 `HomeUiState.answer`에 그대로 누적한다.
- 진행 상태는 `TutorUiState`를 `HomeUiState`의 필드로 합성해 노출한다.

학년:

- `KIDS`
- `ELEMENTARY`
- `MIDDLE`
- `HIGH`

학년별 Thinking 설정:

| 학년 | Thinking | 설정 |
|---|---|---|
| KIDS | OFF | `ThinkingConfig(enableThinking = false)` |
| ELEMENTARY | OFF | `ThinkingConfig(enableThinking = false)` |
| MIDDLE | ON | `enableThinking = true`, `thinkingTokenBudget = 1024` |
| HIGH | ON | `enableThinking = true`, `thinkingTokenBudget = 1024` |

`ThinkingConfig`는 1인자 생성자 `ThinkingConfig(boolean)`를 제공하므로 Thinking OFF 학년은 토큰 예산 인자를 전달하지 않는다.

학년별 Thinking 설정은 `Conversation`이나 Engine을 다시 생성하지 않고 `sendMessageAsync()` 요청별 설정으로 전달한다.

Qwen 전용 문법인 `<think>`, `/no_think`를 시스템 프롬프트에 사용하지 않는다.

학년:

- `KIDS`
- `ELEMENTARY`
- `MIDDLE`
- `HIGH`

학년별 Thinking 설정:

| 학년 | Thinking | Budget |
|---|---|---:|
| KIDS | OFF | 0 또는 미사용 |
| ELEMENTARY | OFF | 0 또는 미사용 |
| MIDDLE | ON | 1024 |
| HIGH | ON | 1024 |

LiteRT-LM의 실제 설정은 다음 속성에 맞춰 적용한다.

- `ThinkingConfig.enableThinking`
- `ThinkingConfig.thinkingTokenBudget`

학년별 해설 스타일:

- KIDS: 쉬운 단어, 짧은 문장, 친절하고 구체적인 예시
- ELEMENTARY: 초등학생 눈높이의 개념과 단계별 설명
- MIDDLE: 핵심 개념, 공식의 의미, 단계별 계산
- HIGH: 개념적 근거, 공식 적용 이유, 논리적 풀이 및 최종 확인

OCR 오류는 문맥을 바탕으로 이해하도록 하되 수식·숫자·단위의 값을 임의로 변경하지 않는다.

불확실한 정보는 추측하지 않고 사용자에게 확인을 요청하도록 한다.

다음 안전성을 구현한다.

- `generationId` 기반 이전 요청 차단
- 단일 활성 요청 보장
- 네이티브 취소 완료 확인
- 중복 요청 방지
- 취소와 오류 분리
- UI 상태 복구
- 토큰 수 조회 실패와 UI 상태 복구 분리

## ④ TutorResultScreen.kt

Jetpack Compose 및 Material3로 구현한다.

구성:

1. 학년 선택 필터 칩
2. 실시간 상태 카드
3. 접기·펼치기 가능한 풀이 진행 내역
4. 기존 `HomeUiState.answer`를 이용한 실시간 해설
5. 완료·취소·오류 상태

학년 선택 칩:

- 유아
- 초등
- 중등
- 고등

진행 중 문구:

`선생님이 문제를 꼼꼼히 살펴보고 있어요.`

실제 이벤트에 맞춰 상태 문구가 바뀌도록 한다.

Thinking OFF인 KIDS와 ELEMENTARY에서도 Thought 이벤트가 없다는 이유로 로딩 상태가 종료되면 안 된다.

완료 문구:

`✓ 풀이 완성 (눌러서 풀이 진행 내역 보기)`

카드를 눌렀을 때만 진행 내역이 펼쳐져야 한다.

진행 내역은 Thought 원문이나 그 요약이 아니라 실제 앱에서 발생한 이벤트 기록으로 구성한다.

예시:

- OCR 문제 인식 완료
- AI 분석 시작
- 해설 생성 시작
- 해설 생성 완료

검산 기능이나 독립적인 검증 이벤트가 없다면 '검산 완료'라고 표시하지 않는다.

해설은 기존 `AssistantAnswer`를 재사용하고 LaTeX 및 Markdown 렌더링을 유지한다.

자동 스크롤 규칙:

- 사용자가 하단을 보고 있을 때 새 답변에 맞춰 자동 스크롤
- 사용자가 위로 스크롤한 경우 자동 이동 중지
- 사용자가 다시 하단으로 이동하면 자동 스크롤 재개
- 기존 스크롤 로직과 충돌하지 않도록 구현

# 7. 기존 파일 수정 범위

실제 코드에 연결되도록 다음 파일을 수정한다.

| 파일 | 수정 내용 |
|---|---|
| `ai/Assistant.kt` | 기존 호출부 하위 호환성을 고려한 스트리밍 API 확장 |
| `ai/GemmaAssistant.kt` | Thought/Answer 분리, ThinkingConfig 적용, 취소 및 스트림 종료 처리 |
| `ai/PromptTemplates.kt` | 학년별 선생님 프롬프트 추가 및 기존 규칙 충돌 제거 |
| `ui/home/HomeUiState.kt` | `selectedGrade`, 튜터 진행 상태 추가 |
| `ui/home/HomeViewModel.kt` | OCR 결과 연결, 후속 질문 스트리밍, 튜터 스트림 수집 및 진행 상태 합성, 취소·오류 처리 통합 |
| `ui/home/HomeScreen.kt` | TutorResultScreen 및 진행 UI 연결 |
| `ui/home/HomeViewModelHost.kt` | 기존 GemmaAssistant 인스턴스 공유 및 의존성 생명주기 관리 |

기존 테스트 코드 중 다음은 반드시 수정 대상에 포함한다. `Assistant` 인터페이스 시그니처를 변경하면 컴파일이 깨진다.

| 파일 | 수정 내용 |
|---|---|
| `app/src/test/java/com/donian/myaitutor/ui/home/HomeViewModelTest.kt` | `FakeAssistant`의 변경된 인터페이스 반영, 튜터 진행 상태·취소·오류 테스트 추가 |

`Assistant` 인터페이스 변경 시 기존 사용처를 모두 검색하고 기존 기능이 깨지지 않도록 호환성을 유지한다.

# 8. 학년별 프롬프트 및 후속 질문

기존 `PromptTemplates.kt`의 규칙을 확인하고 학년별 지침을 통합한다.

기존 3~5문장 간결 답변 규칙과 새 상세 해설 규칙이 충돌하지 않도록 수정한다.

기본 해설 형식:

1. 문제 조건
2. 핵심 개념
3. 단계별 풀이
4. 최종 정답
5. 필요한 경우 추가 설명

문제 난이도와 학년 수준에 맞춰 설명 길이를 조절한다.

최초 OCR 문제 풀이와 사용자의 후속 질문 모두 다음을 지원해야 한다.

- 동일한 GemmaAssistant 인스턴스
- 동일한 활성 Conversation
- 학년별 Thinking 설정
- 스트리밍 Answer 출력
- 진행 상태 표시
- 취소 및 오류 처리
- 기존 대화 문맥 유지

후속 질문에도 이전 학년 선택값을 적용한다.

# 9. Thinking 및 출력 토큰 관리

LiteRT-LM 공식 문서에 따르면 Thinking 토큰과 최종 답변 토큰 모두 `maxOutputToken` 제한에 포함된다.

현재 2048을 그대로 사용할 경우 Thinking Budget 1024가 적용되면 최종 답변에 사용할 수 있는 공간이 부족할 수 있다.

따라서 다음을 구현한다.

1. `maxOutputToken`의 중복 하드코딩을 제거한다.
2. 기본값을 한 곳에서 관리한다.
3. 필요한 경우 추론 요청별 `maxOutputToken`을 전달한다.
4. Thinking Budget과 최종 답변 예상 길이를 함께 고려한다.
5. 고등·중등의 긴 해설 테스트로 답변 잘림 여부를 확인한다.
6. 무조건 큰 토큰 값으로 변경하지 않는다.
7. 기존 `MAX_CONVERSATION_TOKENS = 8_000`과의 관계도 확인한다.
8. 값이 변경되면 변경 이유와 성능 영향을 보고한다.

목표는 Thinking을 충분히 수행하면서도 학생에게 보여줄 최종 해설이 잘리지 않도록 하는 것이다.

# 10. 테스트 및 검증

## 10.1 Unit Test

기존 JUnit 4.13.2 및 Coroutines Test 1.11.0을 우선 활용한다.

다음 테스트를 작성한다.

`TutorStreamParserTest.kt`

- Thought 이벤트 처리
- Answer 텍스트 누적
- 특수 토큰 누출 방지
- 청크 경계 분리
- 완료·취소·오류 처리

`HomeViewModelTest.kt` (기존 파일에 추가)

별도 ViewModel이 없으므로 튜터 테스트는 기존 `HomeViewModelTest`에 통합한다. 이 파일은 이미 `FakeAssistant`, `FakeRepo`, 테스트 디스패처를 갖춘 JVM 단위 테스트 구조이므로 Robolectric 없이 동작한다.

- 학년별 Thinking 설정 전달
- 학년별 프롬프트 구성
- OCR 텍스트 전달
- Answer 누적 연결
- `generationId` 검증
- 중복 요청 방지
- 취소 및 재시작
- 네이티브 취소 완료 후 진행 상태 복구
- 오류 후 상태 복구

Fake 엔진을 사용해 실제 Gemma 모델을 로딩하지 않고 테스트한다.

`GemmaAssistant`가 `Dispatchers.Main.immediate`로 콜백을 올리는 경로는 기존 테스트와 동일하게 테스트 디스패처 규칙으로 처리한다.

## 10.2 Compose UI Test

현재 Compose UI 테스트 의존성이 없는 것을 확인했다.

UI 자동화 테스트를 적용할 수 있다면 프로젝트의 Compose BOM과 호환되는 버전으로 다음 의존성을 추가한다.

- `androidx.compose.ui:ui-test-junit4`
- `androidx.compose.ui:ui-test-manifest`

필요한 Android 테스트 Runner 설정도 확인한다.

Compose UI 테스트는 일반 JVM Unit Test와 구분한다.

다음 UI 항목을 검증한다.

- 학년 칩 선택
- 진행 카드 상태 변경
- 접기·펼치기
- Answer 스트리밍 표시
- 자동 스크롤
- 위로 스크롤했을 때 자동 이동 억제
- 취소 및 오류 상태

실행 가능한 에뮬레이터가 없으면 UI 테스트를 통과했다고 보고하지 말고 수동 검증 항목으로 분리한다.

## 10.3 기존 기능 회귀 테스트

- 기존 PaddleOCR 작동
- OCR 추출 텍스트 전달
- Gemma 초기화
- GPU 실패 시 CPU 폴백
- 기존 대화 이력
- LaTeX 렌더링
- 후속 질문
- 새 대화 시작 및 Conversation 재생성
- 앱 재실행
- 모델 다운로드 완료 후 오프라인 실행

## 10.4 빌드

다음 명령을 실행한다.

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Compose UI 테스트를 추가했다면 가능한 환경에서 다음도 실행한다.

```bash
./gradlew :app:connectedDebugAndroidTest
```

실행하지 않은 테스트는 통과했다고 보고하지 않는다.

실기기 테스트를 하지 않았다면 실기기 검증 완료라고 보고하지 않는다.

# 11. 최종 작업 결과 보고

작업이 끝나면 다음 표로 보고한다.

| 항목 | 결과 |
|---|---|
| 신규 파일 3개 생성 | 성공 / 실패 |
| 기존 파일 수정 | 파일명 및 변경 내용 |
| GemmaAssistant 재사용 | 확인 결과 |
| Engine 중복 생성 | 여부 |
| 활성 Conversation 정책 | 확인 결과 |
| Thinking ON/OFF | 학년별 적용 결과 |
| Thought 원문 비노출 | 확인 결과 |
| Answer 실시간 출력 | 확인 결과 |
| 후속 질문 스트리밍 | 확인 결과 |
| 네이티브 취소 및 종료 확인 | 확인 결과 |
| PaddleOCR 코드 변경 | 변경 없음 여부 |
| 오프라인 동작 | 검증 범위 |
| Unit Test | 실행 결과 |
| Compose UI Test | 실행 또는 미실행 사유 |
| Debug Build | 성공 / 실패 |
| 미해결 문제 | 구체적인 내용 |

# 12. 최종 실행 체크리스트

## 착수 전

- [ ] 현재 프로젝트 파일과 의존성 재확인
- [ ] LiteRT-LM 0.18.0 API 확인
- [ ] Gemma 4 모델 메타데이터의 Thought 채널 확인
- [ ] ConversationConfig 채널 설정 확인
- [ ] 기존 취소 흐름과 네이티브 종료 콜백 확인
- [ ] 답변 및 학년 상태의 단일 소유자 확인

## 신규 파일

- [ ] `TutorStreamParser.kt`
- [ ] `LlmInferenceEngine.kt`
- [ ] `TutorResultScreen.kt`

## 기존 파일 수정

- [ ] `Assistant.kt`
- [ ] `GemmaAssistant.kt`
- [ ] `PromptTemplates.kt`
- [ ] `HomeUiState.kt`
- [ ] `HomeViewModel.kt` (튜터 스트림 수집 및 진행 상태 통합)
- [ ] `HomeScreen.kt`
- [ ] `HomeViewModelHost.kt`
- [ ] `HomeViewModelTest.kt` (기존 테스트, `FakeAssistant` 포함)

## 필수 제약

- [ ] PaddleOCR 코드 및 동작 무변경
- [ ] Gemma 모델 중복 초기화 없음
- [ ] Engine 하나 유지
- [ ] 활성 Conversation 하나 유지
- [ ] Conversation 교체 시 기존 Conversation 먼저 종료
- [ ] OCR 텍스트만 Gemma에 전달
- [ ] Thought 원문 및 요약 미노출
- [ ] Answer 실시간 스트리밍
- [ ] Thinking 학년별 설정
- [ ] 네이티브 취소 완료 후 다음 추론 실행
- [ ] 클라우드 및 원격 추론 미추가
- [ ] 오프라인 동작 유지

## 최종 검증

- [ ] Parser 단위 테스트
- [ ] HomeViewModel 튜터 통합 단위 테스트
- [ ] Fake 엔진 UI 테스트
- [ ] 취소 및 재실행 안정성
- [ ] 기존 OCR 및 Gemma 회귀 테스트
- [ ] Debug 빌드
- [ ] 실행 결과 보고

---

**최종 핵심 원칙**

기존 PaddleOCR는 절대 수정하지 않는다.

Gemma 4 E4B와 LiteRT-LM Engine은 기존 인스턴스를 재사용한다.

활성 Conversation은 하나만 유지하며, 정상적인 대화 초기화 시 기존 것을 종료하고 교체한다.

Gemma에는 OCR 텍스트만 전달한다.

Thought 원문과 그 요약은 사용자에게 노출하지 않는다.

Answer는 실시간으로 채팅 화면에 출력한다.

네이티브 추론 취소가 완료되기 전에 다음 요청을 실행하지 않는다.

최초 다운로드 이후 모든 AI 추론은 오프라인으로 작동한다.

기존 MyAITutor 프로젝트를 보존하면서 위 기능을 완성해야 한다.