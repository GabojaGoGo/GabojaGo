# OSRM 개발 환경 이전·작업 안내

이 문서는 가보자GO의 OSRM 개발 환경을 다른 Mac 또는 Linux 개발 장비로 옮길 때 사용한다. 운영 구성과 대중교통 확장 설계는 [README.md](README.md)를 참고한다.

## 현재 범위

- 차량: 영남권(부산·울산·경남) 자동차 프로필
- 도보: 영남권 도보 프로필, Compose `walking` 프로필로 별도 실행
- 백엔드: OSRM `table`로 실제 거리·시간 행렬을 계산하고, `route`로 지도 폴리라인을 반환
- 대중교통: 상태 안내용 스텁만 존재하며 경로 계산 대상이 아니다.

## Git에 포함되지 않는 데이터

아래는 용량이 큰 생성물이라 Git에서 제외된다. 새 환경에는 **기존 데이터를 안전한 채널로 복사**하거나, 원본 OSM PBF에서 다시 전처리해야 한다.

```text
infra/osrm/data/       # 전국 원본·전처리 결과
infra/osrm/yeongnam/   # 영남권 car/foot 그래프
infra/osrm/foot/       # 이전 도보 그래프
```

필요한 결과 디렉터리:

```text
infra/osrm/yeongnam/car/yeongnam-car.osrm.*
infra/osrm/yeongnam/foot/yeongnam-foot.osrm.*
```

`.osm.pbf`, `.osrm.*` 파일은 커밋하거나 메신저에 무단 공유하지 않는다. 팀의 승인된 파일 공유 경로를 사용한다.

## 새 개발 환경 준비

### 1. 필수 도구

```bash
docker --version
docker compose version
osmium --version        # 재전처리할 때만 필요
```

Mac에서는 Docker Desktop의 메모리를 최소 6GB 이상으로 설정한다. 영남권 도보 전처리 피크는 약 1.75GB지만, Docker·DB·백엔드를 함께 실행하면 여유가 필요하다.

### 2. 환경 파일

`backend/.env`는 Git에 포함되지 않는다. 기존 개발 환경의 값은 별도 보안 채널로 전달받아 생성한다. 다음 OSRM 값은 필요 시에만 바꾼다.

```dotenv
OSRM_BASE_URL=http://osrm:5000
OSRM_FOOT_BASE_URL=http://osrm-foot:5000
OSRM_CONNECT_TIMEOUT_MS=1000
OSRM_READ_TIMEOUT_MS=3000
OSRM_MAX_TABLE_SIZE=100
```

같은 Compose 네트워크에서는 위 기본값을 유지한다. 호스트에서 백엔드를 직접 실행하는 경우에만 `http://localhost:<포트>`로 교체한다.

### 3. 그래프 준비

기존 그래프를 복사한 경우에는 디렉터리·파일명이 Compose 설정과 일치하는지만 확인한다.

```bash
ls infra/osrm/yeongnam/car/yeongnam-car.osrm.properties
ls infra/osrm/yeongnam/foot/yeongnam-foot.osrm.properties
```

그래프를 새로 만들 경우:

```bash
brew install osmium-tool
chmod +x scripts/osrm-preprocess.sh

./scripts/osrm-preprocess.sh car yeongnam
./scripts/osrm-preprocess.sh foot yeongnam
```

스크립트는 `extract → partition → customize` 순서로 실행한다. 기존 결과가 있을 때는 덮어쓰지 않으므로, 데이터 갱신은 새 디렉터리에서 검증한 뒤 교체한다.

## 실행 순서

저장소의 `gabojago-backend`에서 실행한다.

```bash
# 차량 OSRM, DB·Redis·백엔드
docker compose up -d

# 도보 OSRM까지 필요할 때
docker compose --profile walking up -d osrm-foot
```

상태 확인:

```bash
docker compose ps
docker compose logs --tail=50 osrm
docker compose logs --tail=50 backend
```

## 기능 확인

OSRM 컨테이너 직접 확인:

```bash
curl 'http://localhost:5000/route/v1/driving/129.0756,35.1796;129.0820,35.1840?overview=false'
```

백엔드 코스 경로 확인:

```bash
curl 'http://localhost:8080/api/courses?purposes=&duration=1n2d'
```

응답의 `routePaths`에 도로 좌표가 있어야 한다. OSRM API의 좌표 순서는 항상 `경도,위도`다.

인터랙티브 플래너 후보 API 확인:

```bash
curl -X POST 'http://localhost:8080/api/planner/next-options' \
  -H 'Content-Type: application/json' \
  --data '{
    "regionKey":"busan",
    "travelMode":"CAR",
    "departureAt":"2026-08-10T10:00:00",
    "slots":[
      {"order":1,"day":1,"time":"10:00","slotType":"SIGHT","selectedPlaceId":15407},
      {"order":2,"day":1,"time":"13:00","slotType":"MEAL","selectedPlaceId":null}
    ]
  }'
```

`targetSlot`, 후보 목록, 후보별 `previewPath`·`fromPrevious`·`estimatedArrivalAt`가 반환되면 정상이다. 예시 장소 ID는 현재 적재 데이터에 의존하므로, 데이터셋을 교체했다면 실제 ID로 변경한다.

## 현재 플래너와 후속 작업

현재 `POST /api/planner/slot-options`는 사용자가 선택한 임의 슬롯에 후보 최대 5개를 반환한다. `next-options`는 첫 번째 빈 슬롯을 찾아 같은 서비스에 위임한다.

두 API의 OpenAPI 명세와 요청 예시는 실행 중인 백엔드의 `/swagger-ui.html`에서 확인한다. 후보 응답의 `scoreBreakdown`은 다음 키를 사용한다.

- `distance`: 확정된 앞뒤 일정 기준의 즉시 동선 점수
- `lookAhead`: 뒤의 미확정 슬롯 최대 두 개까지 연결했을 때의 동선 점수
- `lookAheadApplied`: look-ahead 경로를 계산했으면 `1`, 후보 부족·경로 단절 등으로 기본 점수로 폴백했으면 `0`
- `lookAheadSlots`: 점수 계산에 포함한 후속 슬롯 수

후속 미확정 슬롯이 없는 요청은 기존 즉시 동선 점수만 사용한다. 지도 미리보기 경로는 사용자가 아직 확정하지 않은 미래 후보를 포함하지 않고, 현재 선택 후보까지만 표시한다.

현재 후보 점수:

1. 슬롯 대분류·하위 카테고리로 후보 검색
2. 확정된 앞·뒤 장소를 기준으로 OSRM 실제 거리·시간 계산
3. 우회 거리와 이후 확정 장소까지의 거리로 후보 정렬
4. 후보별 지도 미리보기 폴리라인 반환

완료된 고도화:

- 미확정 다음 슬롯 1~2개까지 Beam Search look-ahead를 후보 점수에 반영

다음 고도화 작업:

1. 출발지·여행지를 부산 고정값에서 좌표·지역 선택으로 확장
2. 프론트에서 후보 선택 즉시 핀·폴리라인을 누적 렌더링
3. 선택한 슬롯 상태를 임시 저장하거나 사용자 코스로 영속화
4. OSRM 장애 시 재시도·관측 지표·명확한 사용자 안내 강화

## 테스트

로컬 JDK가 21이 아니면 Docker JDK 환경에서 실행한다.

```bash
docker run --rm \
  -v "$PWD":/app \
  -w /app \
  eclipse-temurin:21-jdk \
  ./gradlew test --tests com.gabojago.tourism.planner.service.InteractivePlannerServiceTest --no-daemon
```

현재 테스트는 다음 빈 슬롯 추천, 선택 시간 반영, 잘못된 시간 형식 거절을 검증한다.
