# 로컬 환경 설정과 전체 실행

새 Mac 또는 새 작업 환경에서 GabojaGO의 프론트·백엔드·데이터 적재 도구를 실행하는 기준 문서다. 실제 키·토큰·원본 데이터는 커밋하거나 채팅에 붙여 넣지 않고, 별도 보안 채널에서만 전달한다.

## 1. 준비물

- Docker Desktop 실행
- JDK 21
- Flutter 3.44.8 및 Xcode(iOS 실행 시)
- Python 3.11 이상(data-import 실행 시)
- OSRM 그래프 파일: `gabojago-backend/infra/osrm/yeongnam/{car,foot}`

### Orca worktree 준비 점검

새 worktree에서는 루트의 아래 스크립트를 실행한다. 기본 실행은 도구·환경 파일의
존재만 확인하며, `.env` 값을 읽거나 복사하지 않고 Docker·빌드를 자동 실행하지 않는다.

```bash
./scripts/setup-worktree.sh
```

Flutter 의존성이 없는 새 worktree에서만 아래 옵션을 추가한다.

```bash
./scripts/setup-worktree.sh --install-frontend
```

Orca Repository Hook에는 기본 명령인 `./scripts/setup-worktree.sh`만 등록한다.

## 2. 환경 파일

환경 파일은 모두 Git에서 제외한다. 예시 파일만 커밋한다.

```bash
cd GabojaGo
cp gabojago-backend/.env.example gabojago-backend/.env
cp gabojago-frontend/.env.example gabojago-frontend/.env
cp data-import/.env.example data-import/.env
cd gabojago-frontend && sh tool/generate_ios_env.sh
```

환경 파일은 실행 단위별 단일 원본이다. 기기명·네트워크명 파일(`.env.imac`, `.env.tailscale`)은 사용하지 않는다. 빈 환경 파일은 Flutter SDK 미초기화의 원인이 된다.

### 백엔드: `gabojago-backend/.env`

| 변수 | Docker Compose 개발값/설명 | 필수 |
| --- | --- | --- |
| `DB_PASSWORD` | Compose MySQL root 계정과 backend가 함께 쓰는 로컬 DB 비밀번호 | 예 |
| `TOUR_API_SERVICE_KEY` | 한국관광공사 TourAPI 키 | 외부 관광 데이터 사용 시 |
| `KAKAO_REST_API_KEY` | 카카오 REST API 키 | 카카오 연동 사용 시 |
| `JWT_SECRET` | 32바이트 이상 임의 문자열 | 예 |

`DB_URL`, `REDIS_HOST`, OSRM 주소는 비밀값이 아니라 실행 토폴로지다. Docker Compose는 `mysql`, `redis`, `osrm`, `osrm-foot`을 고정 주입한다. 따라서 backend `.env`에 이 주소를 넣지 않는다. 호스트에서 Spring Boot를 직접 실행해야 하면 `gabojago-backend/scripts/run-local.sh`을 사용한다. 이 스크립트가 `127.0.0.1` 토폴로지를 명시하고 같은 backend `.env`의 비밀값만 읽는다.

MySQL의 root 비밀번호는 데이터 볼륨을 **처음 만들 때만** 적용된다. 기존 `mysql-data`가 있다면 backend `.env`의 `DB_PASSWORD`를 기존 MySQL 비밀번호와 같게 유지한다. 비밀번호를 바꾸려면 MySQL 내부에서 변경하거나, 개발 데이터를 지워도 되는 경우에만 볼륨을 초기화한다.

### Flutter 원본 설정: `gabojago-frontend/.env`

| 변수 | 설명 |
| --- | --- |
| `API_BASE_URL` | iOS Simulator/Mac은 `http://localhost:8080/api`, Android Emulator는 `http://10.0.2.2:8080/api`, 실기기는 Mac의 LAN IP 사용 |
| `KAKAO_NATIVE_APP_KEY` | Kakao SDK와 Kakao 지도 SDK 초기화에 사용 |
| `GOOGLE_WEB_CLIENT_ID` | Google 로그인 서버 클라이언트 ID |
| `TOUR_API_KEY` | 프론트 TourAPI 호출이 필요한 화면에서 사용 |
| `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET`, `NAVER_CLIENT_NAME`, `NAVER_URL_SCHEME` | 네이버 로그인 Android/iOS 설정에 사용 |
| `GOOGLE_IOS_CLIENT_ID`, `GOOGLE_IOS_REVERSED_CLIENT_ID` | iOS Google 로그인 설정에 사용 |

### iOS 생성 설정: `gabojago-frontend/ios/Flutter/Env.xcconfig`

`Info.plist`의 `$(...)` 값을 채우는 Xcode 전용 생성 파일이다. 직접 편집하지 말고 Flutter `.env`를 수정한 뒤 아래 명령으로 덮어쓴다.

```bash
cd gabojago-frontend
sh tool/generate_ios_env.sh
```

카카오만 먼저 설정하는 경우에는 `KAKAO_NATIVE_APP_KEY`만 채워도 생성할 수 있다. 네이버·Google 값은 해당 로그인을 도입할 때 `.env`에 추가하고 생성 명령을 다시 실행한다.

Google iOS Client ID와 Reversed Client ID, Kakao·Naver URL scheme은 각 제공자 콘솔에 등록한 현재 iOS Bundle ID와 일치해야 한다. `flutter_export_environment.sh`은 Flutter가 빌드 중 생성하는 파일이며, 이 설정 파일을 대신하지 않는다.

네이버 로그인은 `SceneDelegate.swift`가 `NidOAuth.shared.handleURL(url)`로 callback을 처리한다. `NAVER_URL_SCHEME`을 바꿀 때는 콘솔의 iOS URL Scheme, `.env`, 생성된 `Env.xcconfig`을 함께 바꾼 뒤 재빌드한다.

### 데이터 적재: `data-import/.env`

| 변수 | 설명 |
| --- | --- |
| `TOUR_API_SERVICE_KEY` | 원본 수집에 사용할 TourAPI 키 |
| `DB_USERNAME`, `DB_PASSWORD` | MySQL 계정. `DB_PASSWORD`는 backend `.env`의 값과 같게 설정 |
| `IMPORT_DB_HOST`, `IMPORT_DB_PORT`, `IMPORT_DB_NAME` | 적재 대상 DB. 로컬 Compose는 `127.0.0.1`, `3306`, `gabojago` |

## 3. 서버 전체 기동

OSRM 그래프가 이미 준비된 개발 환경에서는 아래 한 명령으로 MySQL, Redis, 차량 OSRM, 도보 OSRM, Spring Boot를 빌드·실행한다.

```bash
cd gabojago-backend
docker compose --profile walking up -d --build
docker compose ps
```

정상 상태는 MySQL·Redis가 `healthy`, backend가 `Up`이다. 확인용 명령은 다음과 같다.

```bash
docker compose logs -f backend
curl http://localhost:8080/v3/api-docs
```

백엔드 코드 변경 뒤에는 반드시 이미지를 다시 만든다. Docker 캐시가 의심되면 `--no-cache`를 사용한다.

```bash
docker compose up -d --build backend
docker compose build --no-cache backend
docker compose up -d backend
```

서비스를 내릴 때는 데이터를 유지하는 `docker compose down`을 사용한다. 볼륨 삭제(`down -v`)는 MySQL 데이터를 지우므로 명시적으로 초기화할 때만 사용한다.

### OSRM 그래프가 없는 첫 환경

OSRM 그래프 생성에는 시간이 걸리고 별도 원본이 필요하다. 먼저 `gabojago-backend/infra/osrm/README.md`의 전처리 절차를 수행한 후 위 Compose 명령을 실행한다. 차량만 먼저 띄우려면 `docker compose up -d --build`를 사용한다.

## 4. Flutter 실행

환경 파일과 iOS `Env.xcconfig`을 수정한 뒤에는 핫 리로드가 아니라 전체 재빌드가 필요하다.

```bash
cd gabojago-frontend
flutter pub get
sh tool/generate_ios_env.sh
flutter run
```

기본 로컬 환경은 아래처럼 실행한다.

```bash
flutter run # .env 사용
```

앱 코드만 수정했을 때에는 실행 중인 Flutter 터미널에서 `r`로 핫 리로드한다.

## 5. 데이터 적재

> **단계 게이트 확인 — 환경 구성과 적재 실행은 다르다.**
>
> 아래 명령은 환경이 동작하는지 확인하는 절차가 아니라 **실제 DB를 갱신하는 P3 적재**다.
> 실행 전에 [`AGENTS.md`](../AGENTS.md)와
> [`L0-project-context.md`](L0-project-context.md) **6절**의 현재 단계·허용 범위를 확인한다.
>
> **현재 단계는 P1(상세 실측)이며 P3 구현·적재는 금지 범위다.** P1에서는
> `import_tour_api_to_mysql.py --write`를 실행하지 않는다.

MySQL이 `healthy`인 뒤에 실행한다.

```bash
cd data-import
python3 -m venv .venv
.venv/bin/pip install -e ".[dev]"
.venv/bin/python collect_tour_api.py
.venv/bin/python import_tour_api_to_mysql.py
.venv/bin/python import_tour_api_to_mysql.py --write
```

원본 수집·적재의 상세 옵션은 `data-import/README.md`를 따른다.

## 6. 검증

루트에서 실행한다.

```bash
make verify
make format-check
```
