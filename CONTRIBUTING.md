# 가보자GO — 협업 가이드

## 브랜치 전략

```
main ──────────────────────────────────────────── (배포/발표)
  └── develop ──────────────────────────────────── (통합)
        ├── feat/front/지도-코스-표시
        ├── feat/back/코스-추천-api
        └── fix/로그인-crash
```

### 브랜치 규칙

| 브랜치 | 용도 | 직접 push | PR 대상 |
|--------|------|-----------|---------|
| `main` | 발표/데모용 최종본 | ❌ 금지 | `develop` → `main` |
| `develop` | 개발 통합 브랜치 | ❌ 금지 | 각 feature → `develop` |
| `feat/front/xxx` | 프론트 기능 개발 | ✅ | `develop` |
| `feat/back/xxx` | 백엔드 기능 개발 | ✅ | `develop` |
| `fix/xxx` | 버그 수정 | ✅ | `develop` |
| `chore/xxx` | 설정·문서·리팩토링 | ✅ | `develop` |

> **핵심 원칙**: `main`과 `develop`에는 직접 push하지 않는다. 무조건 PR을 통해 merge한다.

---

## 작업 흐름

### 1. 새 작업 시작
```bash
# 항상 develop 기준으로 브랜치 생성
git checkout develop
git pull origin develop
git checkout -b feat/front/코스-지도-표시
```

### 2. 개발 중 커밋
```bash
git add .
git commit -m "feat: 코스 경로를 카카오맵에 폴리라인으로 표시"
```

### 3. PR 올리기
```bash
git push origin feat/front/코스-지도-표시
# GitHub에서 develop ← feat/front/코스-지도-표시 PR 생성
```

### 4. 코드리뷰 → merge → 브랜치 삭제
- PR에 리뷰어: 개발 총괄(@cg-1119) 지정
- approve 받으면 merge
- 브랜치 삭제 (GitHub UI에서)

---

## 커밋 메시지 규칙

```
<type>: <한국어 설명>

타입 목록:
  feat     새 기능
  fix      버그 수정
  chore    설정·의존성·문서 (코드 변화 없음)
  refactor 리팩토링 (기능 변화 없음)
  style    포맷·공백 (로직 변화 없음)
  test     테스트 추가·수정
```

**예시**
```
feat: 홈 화면에 주변 관광지 카드 추가
fix: 로그인 후 딥링크 처리 NPE 수정
chore: pubspec.lock 패키지 버전 업데이트
refactor: ApiService HTTP 에러 처리 공통화
```

---

## 개발 환경 세팅

### 공통
1. 이 저장소 clone
   ```bash
   git clone https://github.com/GabojaGoGo/GabojaGo.git
   cd GabojaGo
   git checkout develop
   ```

2. 환경변수 파일 받기 (별도 채널에서 공유)
   - `gabojago-frontend/.env`
   - `gabojago-backend/.env`

### 프론트엔드
```bash
cd gabojago-frontend
flutter pub get
flutter run --dart-define=ENV=imac      # 아이맥 서버 (같은 WiFi)
flutter run --dart-define=ENV=tailscale # 외부 접속 (Tailscale VPN)
```
> Flutter 명령은 반드시 `gabojago-frontend/` 안에서 실행

### 백엔드
```bash
cd gabojago-backend
docker compose up --build -d
docker compose logs -f backend
```

---

## PR 체크리스트

PR 올리기 전 스스로 확인:
- [ ] `develop` 최신 상태에서 브랜치 생성했는가
- [ ] `.env` 파일이 커밋에 포함되지 않았는가
- [ ] 빌드 오류 없이 실행되는가
- [ ] PR 제목이 커밋 메시지 규칙을 따르는가
- [ ] 리뷰어(@cg-1119)를 지정했는가

---

## 역할 분담

| 역할 | 담당 | 주요 작업 디렉토리 |
|------|------|-------------------|
| 개발 총괄 | @cg-1119 | 전체, 백엔드·프론트 공통 |
| 프론트엔드 | TBD | `gabojago-frontend/` |
| 백엔드 | TBD | `gabojago-backend/` |
| UI/UX | TBD | `docs/`, 디자인 시스템 |
