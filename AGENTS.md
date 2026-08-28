# 가보자GO 작업 규칙

이 저장소에서 작업하는 에이전트와 자동화 도구는 아래 규칙을 따른다.

## 작업 전 읽는 순서

작업을 시작하기 전에 순서대로 읽는다.

1. `docs/L-1-operating-principles.md` — 어떻게 판단할지
2. `docs/L0-project-context.md` — 이미 무엇이 결정됐는지
3. 아래 이 저장소의 규칙 — 실제로 어떻게 작업할지
4. `data-import/` 작업이면, 먼저 `L0` 6절에서 **현재 프로젝트 단계와 허용 범위**를 확인한 뒤 해당 단계의 data-import 문서를 읽는다. 실측·정제·검증처럼 결과를 남기는 작업이면 `data-work-recording-rules.md`를 함께 읽는다.
  - 원천 탐색·실측(P1): `other-source-survey.md` **0절부터** → `source-downloads.md` → `attribute-coverage.md`
  - 분류·매핑 계약(P2): `classification-mapping.md` → 필요한 근거 문서(`other-source-survey.md`, `attribute-coverage.md`)
  - 구현·적재(P3 이후): 위 문서에서 해당 원천·매핑 계약을 확인한다.

   `data-import/docs/`는 이 영역의 세부 계약과 근거를 소유한다. 프로젝트 전체 단계·현재 우선순위는 여기서 정하지 않으며 `L0`과 Jira를 따른다. `mappings/*.csv`는 **값만 갖고 범위는 이 문서들이 소유한다.** csv의 `measured_scope`가 비어 있는 것은 범위 미기록이 아니라 소유 문서가 따로 있다는 뜻일 수 있으므로, 수치가 이상해 보이면 먼저 그 값의 소유 문서를 찾는다.

## 저장소 구성

- `gabojago-backend/`: Java 21 · Spring Boot · Gradle · MySQL/Redis/OSRM
- `gabojago-frontend/`: Flutter · Dart
- `data-import/`: 공공 원천 데이터 수집·적재 도구
- `gabojago-backend/infra/osrm/`: OSRM 실행·전처리 문서와 스크립트

## 변경 원칙

1. 요청 범위 밖의 리팩터링·포맷 대량 변경·의존성 업그레이드는 하지 않는다.
2. 기존 작업 트리 변경은 사용자 작업으로 간주한다. 충돌 가능성이 있으면 **해당 파일 수정을 중단하고 충돌 범위를 보고한다. 사용자의 명시적 지시 없이 기존 변경을 덮어쓰지 않는다.** 무인 실행에서는 확인을 기다릴 수 없으므로 전체를 멈추지 말고 해당 파일만 건너뛴 뒤 보고한다.
3. 새 HTTP endpoint에는 OpenAPI 설명·예시와 서비스 테스트를 함께 추가한다.
4. 외부 연동은 아래 항목 중 무엇이 필요한지 검토한다. 전부 넣으라는 뜻이 아니라 빠뜨리지 말고 판단하라는 뜻이다.
  ```
   timeout · retry · backoff · idempotency · fallback
  ```
5. OSRM 경로가 바뀌면 프론트의 `routePaths`도 다시 계산한다.
6. 빌드 산출물, 캐시, 임시 export, 로컬 DB dump, OSRM 원본·그래프는 **명시적 목적이 없으면 커밋하지 않는다.** 대용량 원본·그래프는 내용을 출력하지도 않는다.

보안·파괴적 작업 관련 제한은 아래 **YOLO 실행 환경 안전선**을 따른다. 이 절에서 반복하지 않는다.

## YOLO 실행 환경 안전선

에이전트가 sandbox·명령 승인 없이 실행되더라도 아래 제한은 반드시 지킨다.

1. `.env`, API 키, 토큰, 인증서, 개인 로컬 설정 파일은 읽거나 출력하거나 수정하거나 커밋하지 않는다.
2. `rm -rf`, `git reset --hard`, `git clean`, 강제 push, `docker compose down -v`처럼 복구가 어려운 명령은 사용자가 명시적으로 승인한 경우에만 실행한다.
3. 배포, 결제, 계정·조직 권한 변경, 외부 서비스 데이터 변경은 사용자의 명시적 승인 없이는 실행하지 않는다.
4. 작업 시작 전과 완료 전에는 `git status`로 변경 범위를 확인한다. 기존 변경은 사용자 작업으로 취급한다.
5. 커밋, push, PR 생성·병합은 사용자가 요청한 경우에만 수행한다.

## 검증

루트에서 실행한다.

```bash
make verify
make format-check
```

- backend 변경: 최소 관련 Gradle 테스트를 실행하고, 가능하면 `./gradlew check --no-daemon`을 실행한다.
- frontend 변경: `flutter analyze`와 관련 `flutter test`를 실행한다.
- Docker/OSRM 변경: Compose healthcheck와 실제 API 응답을 확인한다.

**검증 명령 통과만으로 완료로 간주하지 않고, 변경 목적에 맞는 실제 결과를 확인한다.** 명령이 성공한 것과 결과가 맞는 것은 다르다. 판단 기준은 `docs/L-1-operating-principles.md` 8절을 따른다.

## 문서 정책

- 운영 원칙: `docs/L-1-operating-principles.md`
- 프로젝트 컨텍스트: `docs/L0-project-context.md`
- 결정·검증 이력: `docs/decision-log.md`
- 개발 규칙: `docs/development-rules.md`
- data-import 데이터 작업 기록 규칙(raw·staging·reports·docs 역할과 저장 위치): `data-import/docs/data-work-recording-rules.md`
- 협업·브랜치 규칙: `CONTRIBUTING.md`
- OSRM 환경·운영: `gabojago-backend/infra/osrm/README.md`, `DEVELOPMENT.md`
- 구현과 함께 갱신할 수 없는 장문 기획 문서는 새로 만들지 않는다. 의사결정과 실행 방법만 짧게 남긴다.
- `cg-1119` 작업자의 개인 기록(작업 메모, 조사·검증 과정, 개인 브리핑)은 Obsidian vault에 남기며 저장소 `docs/`에는 남기지 않는다. 에이전트는 Obsidian MCP를 사용해 vault를 조회·수정하지 않는다.
- Jira 이슈의 댓글·설명·상태·담당자 변경은 사용자가 명시적으로 요청한 경우에만 수행한다. 에이전트가 작업 시작·진행·완료를 이유로 임의 갱신하지 않는다.
- 협업자가 함께 참조해야 하는 공유 기록(확정된 설계 결정, 개발·운영 규칙, 구현·운영 방법)은 저장소 `docs/`에 남긴다.

## 커밋과 브랜치

- `main`, `develop`에는 직접 push하지 않는다.
- **현재 브랜치가 `main` 또는 `develop`이면 사용자 요청 없이 새 브랜치를 만들거나 push하지 않는다.** 작업만 하고 현재 브랜치를 보고한다.
- 기능, 버그 수정, 하네스/문서는 서로 다른 커밋으로 분리한다.
- 커밋·push·PR 생성은 사용자가 요청한 경우에만 수행한다.
