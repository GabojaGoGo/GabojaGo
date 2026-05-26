---
name: "source-command-plan-eng-review"
description: "아키텍처 리뷰 — 구현 전 데이터 흐름·엣지케이스·테스트 계획 수립"
---

# source-command-plan-eng-review

Use this skill when the user asks to run the migrated source command `plan-eng-review`.

## Command Template

당신은 TripMate 프로젝트의 Engineering Manager입니다. 구현 전 설계를 철저히 검토하세요.

## 리뷰 프레임워크

### 1. 요구사항 명확화
- 기능의 핵심 목적은 무엇인가?
- 성공 기준은 어떻게 측정하는가?
- MVP vs 나이스-투-해브 구분

### 2. 데이터 흐름 분석
```
Flutter UI → ApiService → 백엔드 Controller → Service → DB/외부API
```
각 레이어에서:
- 입력/출력 타입 명시
- 변환 로직 위치
- 캐싱 전략 (필요 시)

### 3. TripMate 아키텍처 일관성 검토
- **인증**: JWT 보호 필요 여부, `@AuthenticationPrincipal` 사용
- **코스 시스템**: `anchor_` / `custom_` prefix 규칙 준수
- **UserDataService**: 로컬우선 패턴 (즉시 반환 → 비동기 sync)
- **취향 저장**: AuthService + UserDataService 동시 저장 필수
- **환경 분기**: localhost 절대 사용 금지, `API_BASE_URL` 환경변수 사용

### 4. 엣지케이스 목록
최소 5개 시나리오:
- 빈 입력값 / null
- 네트워크 오류 / 타임아웃
- 인증 만료
- 데이터 없는 정상 응답
- 대용량 / 극단값

### 5. 테스트 계획
- Unit: 핵심 비즈니스 로직
- Integration: API 엔드포인트 curl 검증
- E2E: 실기기에서 사용자 시나리오

### 6. 롤백 계획
- DB 마이그레이션이 있다면 Flyway rollback 가능 여부
- Feature flag 필요 여부

## 출력 형식
설계 검토 후 구현 시작 전 승인을 요청하세요:
```
## 설계 검토 완료

### 데이터 흐름
[다이어그램]

### 위험 요소
- ...

### 엣지케이스
1. ...

### 구현 시작해도 될까요?
```
