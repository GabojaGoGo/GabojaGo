# 가보자GO MVP 추천 데이터 계약

> 기준: 한 요청은 한 지역, 이동수단은 자차/대중교통, 기간별 고정 슬롯 템플릿 + Beam Search
> 목표: DB의 검수된 장소를 읽어 추천하고, 프론트 임시 상태는 저장하지 않는다.

## 1. 저장 원칙

DB에는 다음 두 종류만 저장한다.

1. 알고리즘이 반복해서 읽는 장소 기준 데이터
2. 사용자가 최종적으로 저장한 코스 스냅샷

다음 데이터는 저장하지 않는다.

- 슬롯별 Top-N 후보 목록
- Greedy 탐색 중간 경로와 탈락 후보
- 후보별 임시 점수
- 지도 확대/중심/선택 마커 등 프론트 화면 상태
- 장소 간 이동시간 캐시
- 사용자가 저장하지 않은 추천 루트
- 프론트가 보유한 추천 주문서와 슬롯 편집 상태

추천 실행 중 같은 장소 쌍의 이동 계산이 반복되면 요청 범위의 메모리 Map으로만 중복 호출을 막는다.
인증 토큰 관리에는 기존 Redis를 사용하지만 추천 데이터와 이동시간은 Redis에 저장하지 않는다.

## 2. 파일 구조

```text
tourism/place
  domain                 장소 기준 엔티티
  domain/enums           장소 도메인 타입
  repository             후보·속성·주차 조회
  service                TourAPI 적재

tourism/recommendation
  dto/request            프론트가 보내는 주문서와 순서형 슬롯
  dto/response           루트와 슬롯별 Top-N 후보
  domain                 추천 슬롯 타입과 슬롯 모델
  service                후보 조회, 점수 계산, Beam Search

tourism/route
  domain                 사용자가 저장한 최종 루트
  domain/enums           실제 이동정보 출처
  repository
```

도메인 enum은 DTO 패키지에 두지 않는다. 프론트 요청/응답 DTO는 이후 각 기능의 `dto/request`,
`dto/response`에 별도로 둔다.

## 3. 자동·수동 데이터 구분

### 자동 수집

TourAPI 적재기가 채우며 재수집할 때 갱신한다.

| 테이블 | 데이터 |
|---|---|
| `regions` | TourAPI 지역 코드와 지역명 |
| `places` | 이름, 주소, 좌표, 이미지, contentid, 원본 카테고리, 동기화 시각 |

API 재수집은 사람이 검수한 표준 카테고리, 체류시간, 가격, 영업시간을 덮어쓰지 않는다.

### 수동 검수

운영자가 부산·창원 MVP 장소를 확인하면서 채운다.

| 테이블 | 데이터 |
|---|---|
| `places` | FOOD/CAFE/BAR 등 확정 타입, 표준 대·중·소분류, 평균 체류시간, 가격대, 영업시간 |
| `place_attributes` | enum 속성 코드, 장소별 점수, 신뢰도, 근거, 검수 시각 |
| `parking_info` | 주차 가능 대수, 요금, 높이 제한 |
| `place_relations` | 목적지 카드에 붙일 추천 주차장 |

검수가 끝난 장소는 `curation_status=REVIEWED`로 바꾼다. 알고리즘은 REVIEWED만 후보로 읽는다.

### 자동 파생

| 테이블 | 데이터 |
|---|---|
| `place_suitabilities` | 속성으로 계산한 DATE, FAMILY, HEALING 등의 적합도 |

파생 적합도에는 `rule_version`과 `evidence_json`을 남긴다. 사람의 예외 보정은
`source_type=CURATED`로 저장한다.

### 사용자 최종 저장

| 테이블 | 저장 시점 |
|---|---|
| `routes`, `route_stops`, `route_segments` | 사용자가 최종 코스를 저장할 때만 |

## 4. 알고리즘 입력

### `places`

후보 기본 조회 조건:

```text
region_id = request.region_id
primary_type = slot.slot_type
status = ACTIVE
curation_status = REVIEWED
```

알고리즘이 사용하는 주요 값:

- `category_small`: CHINESE 같은 슬롯별 제외 조건
- `latitude/longitude`: Haversine 근사 거리
- `average_stay_minutes`: 도착·출발 시각 누적
- `operating_hours_json`: 예상 도착시각 영업 여부
- `price_level`: 예산·가성비 점수

### `place_attributes`

QUIET, PHOTO_SPOT, DESSERT_GOOD, PARKING_EASY 등의 장소 자체 특징이다.
사용자 선호 속성과 교집합을 구해 슬롯 점수에 반영한다.

### `place_suitabilities`

DATE, FAMILY, HEALING, RAINY_DAY처럼 목적·동행·상황에 대한 파생 점수다.
엔진은 현재 유효한 한 행의 `score * confidence`를 읽는다.

### 추천 요청 DTO

- 선택 지역과 시작 좌표
- AUTO/GUIDED
- CAR/PUBLIC_TRANSIT
- 출발 일시와 예산
- 전체 가중치 프로파일과 동행 등 공통 조건

프론트가 다음 정보를 요청마다 전달하며 DB에는 저장하지 않는다.

### 슬롯 요청 DTO

- `slot_order`, `slot_type`
- `selected_place_id`, `fixed`
- `excluded_categories_json`
- `preferred_attributes_json`
- `stay_minutes_override`

고정 장소는 거리나 점수가 나빠도 제거하지 않는다. 경고만 응답 DTO에 포함한다.

## 5. 추천 실행 흐름

```text
1. 프론트에서 request 수신
2. duration으로 기간별 고정 슬롯 템플릿 생성
3. 슬롯 유형별 REVIEWED 장소 후보 조회
4. 개발 테스트 옵션이면 IMPORTED 장소도 후보에 포함
5. 속성·적합도·가격·기본 품질 점수 계산
6. Haversine으로 이동거리와 이동시간 근사
7. Beam Search로 슬롯 순서에 맞는 상위 루트 유지
8. Route DTO와 점수 breakdown을 프론트에 반환
9. 기존 프론트 호환을 위해 `/api/courses` 응답도 DB 추천 결과로 변환
10. 사용자가 저장할 때만 routes/stops/segments 생성
```

Top-N 대안과 이동 API 응답은 추천 응답 DTO의 생명주기만 가진다. DB에는 남기지 않는다.

## 5.1 현재 구현 상태

현재 1단계 구현은 다음 범위까지 완료되어 있다.

```text
POST /api/recommendations/routes
GET  /api/courses
```

`/api/recommendations/routes`는 테스트용 상세 응답을 반환한다.
`/api/courses`는 기존 Flutter 화면 호환을 위해 Beam Search 결과를 `CourseDto` 형태로 변환한다.
따라서 기존 코스 결과 화면과 상세 화면은 큰 구조 변경 없이 새 추천 엔진 결과를 표시한다.

기본 요청값은 다음과 같다.

```text
regionKey = TOUR:6
duration = 1n2d
travelMode = CAR
departureAt = 내일 10:00
debugUseImported = true
```

`debugUseImported=true`는 아직 수동 검수된 REVIEWED 장소가 없는 개발 단계에서만 사용한다.
운영 또는 품질 검증 단계에서는 REVIEWED만 후보로 사용해야 한다.

### 슬롯 템플릿

현재는 사용자 직접 슬롯 입력을 받지 않고 duration 기반 템플릿을 사용한다.
나중에 사용자가 직접 슬롯 순서를 보낼 수 있도록 내부 모델은 `RecommendationSlot` 리스트로 분리되어 있다.

```text
day:
SIGHT -> MEAL -> CAFE -> SIGHT

1n2d:
DAY 1: SIGHT -> MEAL -> CAFE -> SIGHT -> LODGING
DAY 2: CAFE -> SIGHT -> MEAL -> SIGHT
```

추천 슬롯 타입과 DB 장소 타입 매핑은 다음과 같다.

| 추천 슬롯 | DB PlaceType |
|---|---|
| SIGHT | ATTRACTION, CULTURE, ACTIVITY, SHOPPING |
| MEAL | FOOD |
| CAFE | CAFE |
| LODGING | LODGING |

### Beam Search 파라미터

```text
slotCandidateLimit = 12
beamWidth = 5
resultLimit = 3
```

장소 중복은 같은 루트 안에서 제거한다.
이동시간은 아직 외부 길찾기 API를 호출하지 않고 Haversine 기반 근사값을 사용한다.

### 점수식 초안

장소 점수는 다음 항목을 100점 스케일로 계산한 뒤 가중합한다.

```text
placeScore =
  suitability * 0.35
+ attribute   * 0.25
+ categoryFit * 0.15
+ price       * 0.10
+ quality     * 0.10
+ mobility    * 0.05
```

루트 점수는 장소 점수 합계에 이동 페널티와 시간/균형 보정을 더한다.

```text
routeScore =
  placePreference
+ movementPenalty
+ timeFit
+ routeBalance
```

응답에는 총점뿐 아니라 `scoreBreakdown`을 함께 내려 테스트 중 판단 근거를 확인할 수 있게 한다.

## 6. 시간 모델

시간은 별도 테이블을 만들지 않고 다음 값으로 계산한다.

```text
request.departure_at
  + segment.duration
  = stop.arrival_time

stop.arrival_time
  + slot.stay_minutes_override 또는 place.average_stay_minutes
  = stop.departure_time
```

후보 탐색 중에는 메모리 값이고, 사용자가 코스를 저장하면 `route_stops`에 최종 시각을 동결한다.

## 7. 주차장 처리

주차장도 `places.primary_type=PARKING`인 장소다.

- 주차장 크기와 요금: `parking_info`
- 목적지와 추천 주차장 연결: `place_relations`
- 프론트 표시: 목적지 카드의 부가 데이터
- RouteStop 승격: MVP에서는 하지 않음

목적지의 `PARKING_EASY` 점수가 낮을 때만 관계가 연결된 주차장을 응답 DTO에 첨부한다.

## 8. 저장된 루트

`routes`, `route_stops`, `route_segments`는 추천 결과 캐시가 아니라 사용자 기록이다.

- `route_stops.place_snapshot_json`: 장소명이 바뀌어도 저장 당시 표시 보존
- `route_segments.snapshot_json`: 저장 당시 이동 설명 보존
- `routes.score_breakdown_json`: 추천 이유와 점수 재현
- `routes.warnings_json`: 고정 장소 우회 등 당시 경고 보존
- `routes.request_snapshot_json`: 저장 당시 주문 조건 보존

저장된 루트는 외부 API 데이터가 바뀌어도 과거 기록으로 유지한다.

## 9. MVP 테스트 데이터 최소 조건

한 지역에서 다음 조건을 만족해야 추천 품질을 테스트할 수 있다.

- REVIEWED 장소 50~80개
- FOOD 20개 이상, CAFE 10개 이상, LODGING 5개 이상
- 각 장소 평균 체류시간과 영업시간
- 핵심 속성 5~10개와 장소별 점수
- DATE/HEALING/FAMILY 등 적합도 파생값
- 자차 테스트용 주차장 5개 이상과 추천 주차 관계

부산과 창원 데이터는 함께 보유할 수 있지만 한 추천 요청의 `region_id`는 하나만 사용한다.
