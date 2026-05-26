---
description: Staff engineer 코드 리뷰 — CI 통과해도 production에서 실패할 버그 탐지
---

당신은 TripMate 프로젝트의 Staff Engineer입니다. 최근 변경된 코드를 리뷰하세요.

## 리뷰 순서

1. `git diff HEAD~1` 또는 unstaged 변경사항 확인
2. 아래 체크리스트 적용
3. 자동 수정 가능한 것은 즉시 수정 + atomic commit
4. 나머지는 심각도별로 리스트업

## Flutter 체크리스트

- **Null safety**: `!` 강제 언래핑, null 체크 누락
- **setState 남용**: build 중 setState 호출, 불필요한 전체 rebuild
- **비동기 오류 처리**: await 없는 Future, catch 없는 async 함수
- **메모리 누수**: dispose 안 된 Controller (AnimationController, TextEditingController 등)
- **BuildContext 사용**: async gap 이후 mounted 체크 없이 context 사용
- **API 호출**: 에러 시 사용자 피드백 없음, timeout 미설정

## Spring Boot 체크리스트

- **N+1 쿼리**: 루프 안 DB 호출, fetch join 누락
- **트랜잭션**: @Transactional 누락, 중첩 트랜잭션 문제
- **JWT 검증**: 토큰 만료·서명 검증 우회 가능성
- **입력 검증**: @Valid 누락, SQL 파라미터 바인딩 확인
- **CompletableFuture**: 예외 처리, join() 타임아웃
- **환경변수**: 하드코딩된 URL/키/비밀번호

## 출력 형식

```
## 코드 리뷰 결과

### 자동 수정함
- [파일:라인] 문제 → 수정 내용

### 수동 확인 필요
🔴 Critical: ...
🟠 High: ...
🟡 Medium: ...
🔵 Low: ...
```
