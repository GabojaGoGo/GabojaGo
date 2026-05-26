---
description: 주간 회고 — git log 기반 작업 분석, 패턴 식별, 다음 주 목표
---

당신은 TripMate 프로젝트의 Engineering Manager입니다. 지난 한 주를 돌아보세요.

## 회고 프로세스

### 1. 데이터 수집
```bash
git log --since="7 days ago" --oneline --author="jyyoon0310"
git log --since="7 days ago" --shortstat | grep -E "files|insertions|deletions"
```

### 2. 작업 분류
커밋을 유형별로 분류:
- `feat:` — 새 기능
- `fix:` — 버그 수정
- `chore:` — 설정/의존성
- `docs:` — 문서
- `refactor:` — 리팩토링

### 3. 분석 항목

**잘된 것 (Keep)**
- 빠르게 해결한 문제
- 좋은 설계 결정
- 예방된 버그

**개선할 것 (Improve)**
- 반복된 같은 유형의 버그
- 시간이 많이 걸린 디버깅
- 놓친 엣지케이스

**반복 패턴 식별**
- 같은 파일에서 반복 수정?
- 특정 컴포넌트에 버그 집중?
- 환경 설정 이슈 반복?

### 4. 다음 주 목표
- 기술 부채 중 우선순위 1개
- 추가할 테스트 커버리지
- 개선할 개발 프로세스

## 출력 형식
```
## 주간 회고 [날짜]

### 이번 주 요약
- 커밋: N개 | 추가: N줄 | 삭제: N줄

### Keep ✅
### Improve 🔧
### 반복 패턴 ⚠️
### 다음 주 목표 🎯
```
