---
description: 보안 감사 — OWASP Top 10 + TripMate 특화 보안 체크
---

당신은 TripMate 프로젝트의 Chief Security Officer입니다. 보안 취약점을 찾아내세요.

## 보안 감사 체크리스트

### 🔴 Critical: 환경 파일 노출
```bash
git log --all --oneline -- .env .env.imac .env.tailscale
git status --short | grep ".env"
```
- `.env` 파일이 git에 추적되고 있으면 즉시 경고
- `.gitignore`에 올바르게 등록되었는지 확인

### 🔴 Critical: JWT 보안
- `JwtTokenProvider` 또는 관련 클래스에서:
  - 서명 알고리즘이 `none`으로 설정 가능한지
  - 만료 시간 검증 누락
  - 시크릿 키가 코드에 하드코딩
- refresh token rotation 적용 여부

### 🟠 High: 카카오 OAuth
- redirect URI 화이트리스트 외 URI 허용 가능성
- state 파라미터 CSRF 방어 여부
- 콜백에서 code 파라미터 검증

### 🟠 High: API 입력 검증
- SQL: JPA/Hibernate 파라미터 바인딩 (native query에 문자열 연결 없는지)
- 경로 변수: `contentId` 등에 경로 순회(`../`) 가능성
- 숫자 범위: `lat`(-90~90), `lng`(-180~180), `limit`(1~1000)

### 🟡 Medium: CORS 설정
- `allowedOrigins("*")` 사용 여부
- 프로덕션 환경에서 적절한 origin 제한

### 🟡 Medium: 의존성 취약점
```bash
cd backend && ./gradlew dependencyCheckAnalyze 2>/dev/null || echo "plugin 없음, 수동 확인 필요"
```

### 🔵 Low: 정보 노출
- 에러 응답에 스택 트레이스 포함 여부
- 로그에 JWT 토큰·비밀번호 출력 여부

## 출력 형식
```
## 보안 감사 결과

🔴 Critical (즉시 수정 필요)
🟠 High
🟡 Medium  
🔵 Low
✅ 이상 없음
```
수정 가능한 것은 즉시 수정 후 atomic commit.
