# 로컬 백엔드 실행 가이드

## 목적

개발 중에는 Docker로 Spring Boot까지 올리지 않고, 로컬 MySQL과 로컬 Redis에 붙여 Spring Boot만 빠르게 재시작한다.
Android Studio나 터미널에서 백엔드만 실행하고 끄는 흐름을 단순하게 만들기 위한 방식이다.

## 기본 구조

```text
MySQL: 127.0.0.1:3306
Redis: 127.0.0.1:6379
Spring Boot: 127.0.0.1:8080
```

`application.yml`의 개발 기본값도 위 구조에 맞춰져 있다.
Docker 컨테이너에서 백엔드만 실행할 때는 `docker-compose.yml`이 `host.docker.internal`로 호스트 DB와 Redis에 붙도록 덮어쓴다.

## 실행

```bash
cd /Users/cg1119/Desktop/code/00.doing/GabojaGo/gabojago-backend
./scripts/run-local.sh
```

스크립트가 처리하는 일은 다음과 같다.

```text
1. 로컬 MySQL 접속 확인
2. gabojago DB가 없으면 생성
3. 로컬 Redis가 없으면 redis-server를 임시 실행
4. .env에서 TOUR_API_SERVICE_KEY, KAKAO_REST_API_KEY 읽기
5. 로컬 개발용 DB/Redis/JWT 환경변수로 ./gradlew bootRun 실행
```

중지와 재시작은 터미널에서 `Ctrl+C` 후 다시 실행한다.

## 확인 URL

```text
Swagger UI: http://localhost:8080/swagger-ui.html
OpenAPI JSON: http://localhost:8080/v3/api-docs
장소 관리자: http://localhost:8080/api/place-admin/places?size=1
코스 추천: http://localhost:8080/api/courses?duration=1n2d
```

## 장소 데이터 적재

서버가 올라간 뒤 다음 API를 호출한다.

```bash
curl -X POST http://localhost:8080/api/place-admin/import/busan-changwon
```

현재 개발 설정에서는 `/api/place-admin/**`가 인증 없이 열려 있다.
운영 배포 전에는 관리자 권한으로 제한해야 한다.

## Docker 사용 시

현재 `docker-compose.yml`은 MySQL/Redis를 띄우지 않고 backend만 실행한다.
백엔드 컨테이너는 호스트의 MySQL/Redis를 사용한다.

```bash
docker compose up -d --build
```

컨테이너에서 호스트를 가리키는 주소는 다음과 같다.

```text
DB_URL=jdbc:mysql://host.docker.internal:3306/gabojago?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
REDIS_HOST=host.docker.internal
REDIS_PORT=6379
```

JetBrains 또는 Android Studio에서 Docker Compose를 사용할 때도 같은 compose 파일을 선택하면 된다.
다만 빠른 반복 개발은 `scripts/run-local.sh` 실행 방식이 더 단순하다.
