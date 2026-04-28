# CLAUDE.md

이 파일은 Claude Code (claude.ai/code)가 이 저장소(`rehearsal/`)의 코드를 다룰 때 참고할 가이드를 제공한다. 이 문서는 독립적으로 완결되어 있으며, 외부 문서를 추가로 읽지 않아도 된다.

---

## 미션

이 프로젝트는 배우가 AI 상대역과 자연스럽게 리허설할 수 있도록 돕는 풀스택 서비스의 Spring Boot 구현체다.

핵심 목표:
- 실제 상대 배우와 연습하는 느낌
- 클릭을 최소화한 자연스러운 진행
- 과장된 TTS 데모가 아니라 리허설 친화적 UX
- 빠른 실험이 가능하지만 코드베이스는 계속 확장 가능해야 함
- 프론트/백엔드/프롬프트/TTS가 하나의 제품 흐름으로 일관되게 동작해야 함

---

## 제품 우선순위

1. 전체 대본을 끝까지 연습 가능해야 한다
2. TTS 실패가 rehearsal 범위를 줄이면 안 된다
3. source-of-truth는 항상 전체 대본이다
4. 자연스러움이 화려함보다 중요하다
5. 작은 diff와 reviewability를 유지한다

---

## 기술 스택 및 프로젝트 구조

- 스택: Spring Boot 4.0.6, Java 17 (Gradle toolchain), Gradle wrapper
- 빌드 도구: Gradle (`./gradlew`)
- `build.gradle`에 연결된 스타터:
  - `spring-boot-starter-webmvc`
  - `spring-boot-starter-data-jpa`
  - `spring-boot-starter-thymeleaf`
  - 대응되는 테스트 스타터, `junit-platform-launcher`
- 패키지 루트: `com.mvp.rehearsal`
- 진입점: `com.mvp.rehearsal.RehearsalApplication`
- 기본 테스트: `RehearsalApplicationTests.contextLoads()`
- 런타임 설정: `src/main/resources/application.yaml` (현재는 `spring.application.name=rehearsal`만 설정됨)

새 컴포넌트(컨트롤러/서비스/리포지토리 등)는 모두 `com.mvp.rehearsal` 하위에 두어 별도의 `@ComponentScan` 설정 없이 자동 스캔되도록 한다.

권장 디렉토리 책임 분리:
- `controller/` — HTTP 엔드포인트
- `service/` — 비즈니스 로직
- `dto/` — 요청/응답 객체
- `repository/` — 영속성 접근
- `config/` — 설정 클래스
- `prompt/` — LLM 프롬프트 템플릿
- `tts/` — TTS 어댑터 및 캐시

컨트롤러에 로직을 몰아넣지 말고 위 책임 분리를 유지한다.

---

## 자주 쓰는 명령어

이 디렉토리(`rehearsal/`)에서 실행한다. Gradle wrapper가 toolchain 프로비저닝을 처리한다.

```bash
./gradlew bootRun                 # Spring Boot 앱 실행
./gradlew build                   # 컴파일 + 테스트 + jar 빌드
./gradlew test                    # 모든 JUnit Platform 테스트 실행
./gradlew test --tests com.mvp.rehearsal.RehearsalApplicationTests.contextLoads   # 단일 테스트 실행
./gradlew bootJar                 # 실행 가능한 jar를 build/libs/에 빌드
./gradlew clean                   # build/ 디렉토리 삭제
```

아직 Checkstyle/Spotless 플러그인이 설정되어 있지 않으므로 "lint"는 `./gradlew build` (컴파일러 + 테스트)로 대체한다.

---

## 엔지니어링 원칙

### 1. 자연스러움 우선 (Natural over dramatic)
- "더 극적"보다 "더 자연스러움"을 우선한다.
- 명시적으로 강한 장면이 아닌 이상 절제된 감정 표현을 선호한다.
- narrator-like delivery보다 actor-like delivery를 선호한다.

### 2. 최소 패치 우선 (Minimal patch first)
- 큰 리팩토링보다 최소 패치를 우선한다.
- 관련 없는 코드까지 수정하지 않는다.
- 먼저 작동하는 가장 단순한 버전을 만들고 이후 개선한다.

### 3. 모듈당 하나의 책임 (One responsibility per module)
- 한 파일/함수에 너무 많은 책임을 몰아넣지 않는다.
- prompts / schemas / services / utils / api / frontend state 역할을 분리한다.
- 새 기능 추가 시 기존 파일에 무조건 누적하지 말고 책임 분리를 먼저 검토한다.

### 4. 코드 전 계획 (Plan before code)
- 기능 구현 전 항상 최소 변경 계획을 먼저 제시한다.
- 변경 파일, 상태 흐름, 리스크, 테스트 항목을 먼저 정리한다.
- 계획 승인 후 코드 패치를 진행한다.

### 5. 리뷰 가능성 (Reviewability matters)
- 변경은 작은 diff로 유지한다.
- startup code review에서 바로 읽히는 수준의 단순함을 유지한다.
- 불필요한 추상화, 깊은 클래스 구조, 과한 패턴 도입을 피한다.

---

## 풀스택 규칙

### Frontend (Thymeleaf 템플릿 또는 향후 SPA)
- 사용자가 "지금 무엇을 해야 하는지" 한눈에 이해 가능해야 한다.
- stepper 표시와 실제 phase/state 흐름은 반드시 일치해야 한다.
- canonical script와 scoped subset은 명확히 분리한다.
- session restore, role selection, rehearsal state는 서로 오염되지 않게 관리한다.
- state / reset rules / phase mapping / render responsibilities를 분리한다.

### Backend (Spring Boot)
- API는 프론트의 상태 흐름과 맞물려 예측 가능한 응답을 반환해야 한다.
- 에러 메시지는 원인을 분리해서 반환한다.
- partial success가 가능하면 hard fail보다 usable result를 우선한다.
- controller / service / dto / repository / config 책임을 섞지 않는다.
- 트랜잭션 경계는 service 계층에서만 정의한다.

### Prompt / Parsing
- 프롬프트는 문학 비평용이 아니라 제품 기능용이어야 한다.
- 구조화된 출력(JSON)을 선호한다.
- 자유서술보다 제약된 필드를 선호한다.
- parser는 extraction 문제인지, JSON generation 문제인지, merge 문제인지 구분해서 다룬다.
- parse cache는 prompt/version/source hash 기준으로 안전하게 무효화되어야 한다.

### Audio / TTS
- TTS는 감정 과장보다 발화 목적, 서브텍스트, 템포, pause, ending shape를 반영해야 한다.
- 파일명은 예측 가능하고 디버깅 가능해야 한다.
- 명명 규칙: `session / line index / character / short suffix`를 포함한다.
- TTS instruction은 짧고 실용적이어야 한다.
- provider별 formatting 전략(OpenAI vs ElevenLabs)을 분리할 수 있어야 한다.
- `(사이)`, hesitation, 말줄임표 등은 무조건 삭제하지 말고 pause signal로 검토한다.
- 오디오 자산은 결정적 명명 규칙으로 캐싱하여 재사용한다. 파일 경로 생성은 helper로 중앙화한다.

### Session / Restore
- 복원된 이전 선택값은 default일 뿐 lock이 아니다.
- 이전 세션을 불러와도 사용자는 역할을 다시 선택할 수 있어야 한다.
- role change는 script-level state를 지우면 안 되고, run-level state만 무효화해야 한다.
- 기존 생성 음성은 가능한 한 재사용하고, 재생성은 꼭 필요할 때만 한다.

---

## 제품 규칙

### Rehearsal UX rules
- AI 대사는 자동으로 자연스럽게 이어져야 한다.
- 사용자 턴에서는 listening 상태가 명확해야 한다.
- 사용자가 말 끝나면 자동으로 다음 턴으로 넘어가는 흐름을 지향한다.
- fallback으로 수동 조작 수단도 유지한다.

### Source-of-truth rules
- rehearsal의 canonical source-of-truth는 전체 parsed script다.
- subset range는 runtime scope일 뿐 canonical data를 대체하면 안 된다.
- partial generation / partial analysis / restore state가 canonical scope를 축소하면 안 된다.

### Prompt rules
- practical direction > beautiful wording
- structured JSON > long prose
- beat_goal, subtext, delivery cue는 TTS와 rehearsal에 실제로 연결되어야 한다.

---

## 코드 스타일 규칙

- 함수는 가능한 한 하나의 책임만 가진다.
- endpoint 이름은 특별한 이유가 없으면 유지한다.
- pure helper function으로 분리 가능한 로직은 분리한다.
- config 값은 중앙화한다 — `application.yaml`이 런타임 설정의 단일 소스다. 클래스에 하드코딩하지 말 것.
- 파일명, 경로 생성은 하드코딩하지 말고 helper를 사용한다.
- 주석은 필요한 경우에만 작성한다.
- 상태 변경은 명명된 helper/reset rule을 통해 수행한다.

---

## 필수 워크플로우

작업 순서:
1. 문제를 먼저 구조화한다
2. 가장 가능성 높은 원인 2~4개를 제시한다
3. 최소 변경 계획을 제시한다
4. 승인 후 수정한다
5. 테스트/로그/edge case를 확인한다
6. 변경이 있으면 commit/push 조건을 검토한다

---

## Claude Code 출력 규칙

작업 시 반드시 다음 순서를 따른다:

1. 먼저 변경 계획을 짧게 설명
2. 변경 파일 목록 제시
3. 수정 코드만 제시
4. 관련 없는 리팩토링 금지
5. 왜 이렇게 바꿨는지 짧게 설명
6. 수동 테스트 3개 제시
7. edge case 3개 제시

---

## 제약 사항

- 전체 아키텍처 재설계 금지
- 불필요한 클래스/패턴 도입 금지
- 작은 코드베이스에 과도한 enterprise 구조 도입 금지
- async queue, Docker, k8s 등은 지금 단계에서 기본 도입 금지
- 코드가 길어지더라도 이해하기 쉬운 명시적 구조를 우선한다
- main/master 직접 push 허용
- destructive command는 명시적 승인 없이 실행 금지

---

## Git 워크플로우

- 기본 작업 브랜치에서 수정한다.
- 코드 변경이 발생하면 가능한 경우 테스트/빌드/린트(`./gradlew build`)를 먼저 통과시킨다.
- commit 메시지는 변경 목적이 드러나게 짧고 명확하게 작성한다.
- auto push는 main/master 포함 모든 브랜치에서 허용한다.
- remote/origin이 없거나 인증 실패 시 push를 강제하지 않는다.

권장 commit 형식:
- feat: ...
- fix: ...
- refactor: ...
- chore: ...

---

## 검증 체크리스트

코드 수정 전후에 항상 확인:
- stepper와 실제 phase 이동이 일치하는가
- canonical script와 scoped subset이 분리되어 있는가
- restore 이후에도 역할 재선택이 가능한가
- TTS 실패가 rehearsal 범위를 줄이지 않는가
- parser partial failure가 전체 실패처럼 처리되지 않는가
- 로그만 봐도 실패 지점을 추적할 수 있는가
- `./gradlew build`가 성공하는가

---

## 기본 프롬프트 선호

Claude Code는 다음을 선호한다:
- simple module split
- explicit naming
- stable JSON
- practical UX
- minimal patch
- easy future testing
- natural actor rehearsal flow
- frontend, backend, prompts, TTS 사이의 풀스택 일관성

선택지가 있을 때:
- more dramatic vs more natural → more natural
- more abstract vs more explicit → more explicit
- more ambitious refactor vs smaller safe patch → smaller safe patch
- regenerate everything vs reuse safe existing assets → reuse
