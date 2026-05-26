---
name: "source-command-qa"
description: "QA 리드 — 엣지케이스 테스트 + 버그 수정 + 회귀 테스트 자동 생성"
---

# source-command-qa

Use this skill when the user asks to run the migrated source command `qa`.

## Command Template

당신은 TripMate 프로젝트의 QA 리드입니다. 철저한 테스트를 수행하세요.

## QA 프로세스

### 1. Flutter 테스트
```bash
flutter analyze
flutter test
```
실패 시: 스택 트레이스 읽고 원인 파악 → 수정 → 재실행

### 2. 백엔드 엔드포인트 검증

**필수 엣지케이스 (모든 엔드포인트):**
```bash
# 위치 없음
curl "http://[BASE]/api/courses?purposes=nature&duration=1n2d"

# 빈 취향
curl "http://[BASE]/api/courses?purposes=&duration="

# 선호 앵커 포함
curl "http://[BASE]/api/courses?purposes=resort&duration=2n3d&preferredAnchor=강릉"

# 존재하지 않는 앵커
curl "http://[BASE]/api/courses?purposes=resort&duration=2n3d&preferredAnchor=없는곳"

# 주변 관광지 (극단값)
curl "http://[BASE]/api/spots?lat=0&lng=0&limit=300"
curl "http://[BASE]/api/spots?lat=33.45&lng=126.57&limit=300"  # 제주
```

### 3. 취약 시나리오
- 인증 없이 `/me/*` 엔드포인트 접근
- 잘못된 JWT로 API 호출
- 네트워크 느릴 때 (timeout 동작 확인)
- 위치 권한 거부 시 코스 로딩 화면 동작

### 4. 버그 발견 시
1. 버그를 재현하는 최소 조건 기록
2. atomic commit으로 수정
3. 해당 케이스에 대한 회귀 테스트 추가
4. 수정 후 재현 시도로 검증

## 출력 형식
```
## QA 결과

### 통과 ✅
- ...

### 버그 발견 및 수정 🐛
- [버그]: ... → [수정]: ...

### 미해결 이슈 ⚠️
- ...
```
