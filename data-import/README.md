# GabojaGO Data Import

> **실행 전 확인 — 이 문서는 실행 권한이 아니다.**
>
> 아래 수집·적재 절차는 **P3 이후에 해당하는 구현 방법**이다. 현재 프로젝트 단계에서
> 이 절차를 실행해도 되는지는 이 문서가 정하지 않는다.
>
> 1. [`AGENTS.md`](../AGENTS.md) — 작업 규칙과 문서 진입 순서
> 2. [`docs/L0-project-context.md`](../docs/L0-project-context.md) **6절** — 현재 단계와 허용·금지 범위
> 3. [`docs/other-source-survey.md`](docs/other-source-survey.md) **0절** — 원천 탐색의 단위와 순서
>
> **현재 단계는 P1(상세 실측)이며 P3 구현·적재는 금지 범위다.** 3절의 `--write` 적재는
> 실행하지 않는다. 이 단계에서 원본을 받는 목적은 DB 적재가 아니라 raw/staging 품질 측정이다.
>
> 또한 이 문서는 TourAPI 단일 원천 기준으로 쓰였다. **TourAPI는 여러 원천 중 하나이며
> 전체 PlaceType의 기준 원천이 아니다.** 원천별 역할은 위 3번 문서가 소유한다.

GabojaGO의 초기 장소 데이터를 구축하는 Python 프로그램이다.

서비스가 실행될 때 외부 API를 호출하는 구조가 아니다. 필요한 원본 데이터를 먼저
JSON 파일로 수집하고, 별도의 적재 프로그램으로 MySQL에 저장한다.

```text
한국관광공사 TourAPI
        ↓
JSON 원본 파일
        ↓
데이터 검증 및 변환
        ↓
MySQL places 테이블
```

초기 구축이 끝난 후 서비스는 MySQL에 저장된 장소 데이터만 사용한다.

부산 도시철도 그래프·역 좌표·출입구·열차 시간표도 같은 방식으로 `mappings/`의
검증된 정적 원본에서 MySQL로 적재한다. 서비스 실행 중에는 공공데이터 API나 OSM을
호출하지 않는다.

## 프로젝트 구조

```text
data-import/
├── scripts/
│   ├── collect_tour_api.py
│   ├── collect_tour_api_detail.py
│   ├── import_tour_api_to_mysql.py
│   ├── import_busan_metro.py
│   └── export_busan_metro_access_points.py
├── data/
│   ├── raw/
│   │   └── tourism/
│   │       └── tour-api/
│   ├── rejected/
│   └── reports/
├── src/
│   └── data_import/
│       └── tour_api/
│           ├── collector.py
│           └── importer.py
├── tests/
├── .env
└── pyproject.toml
```

`scripts/`는 실행 진입점(CLI)만 모아둔 얇은 껍데기이고, 실제 로직은 전부
`src/data_import/`에 있다. 각 파일의 역할:

| 파일 | 역할 |
|---|---|
| `scripts/collect_tour_api.py` | TourAPI 데이터를 JSON으로 수집하는 실행 파일 |
| `scripts/collect_tour_api_detail.py` | 수집한 목록에서 부울경 표본을 뽑아 상세정보를 추가 수집하는 실행 파일 |
| `scripts/import_tour_api_to_mysql.py` | 수집한 JSON을 MySQL에 적재하는 실행 파일 |
| `collector.py` | API 호출, 페이지 처리, 원본 파일 저장 |
| `importer.py` | JSON 읽기, 검증, 유형 변환, MySQL upsert |
| `scripts/import_busan_metro.py` | 백엔드 도시철도 적재 명령을 순서대로 실행하는 진입점 |
| `scripts/export_busan_metro_access_points.py` | 현재 DB의 검수된 OSM 출입구를 재사용 CSV로 export |

## 실행 준비

`data-import` 디렉터리에서 실행한다.

```bash
python3 -m venv .venv
.venv/bin/pip install -e ".[dev]"
cp .env.example .env
```

`.env`에 다음 값을 설정한다.

```dotenv
TOUR_API_SERVICE_KEY=공공데이터포털_인증키

DB_USERNAME=root
DB_PASSWORD=root
IMPORT_DB_HOST=127.0.0.1
IMPORT_DB_PORT=3306
IMPORT_DB_NAME=gabojago
```

백엔드가 한 번 실행되어 Java Enum의 Category 정의가 MySQL에 동기화되어 있어야 한다.

## MySQL Docker 실행

MySQL과 백엔드는 백엔드의 `docker-compose.yml`로 실행한다.

```bash
cd ../gabojago-backend
docker compose up -d
```

실행 상태를 확인한다.

```bash
docker compose ps mysql
```

`STATUS`에 `Up`과 `healthy`가 표시되면 사용할 수 있다.

```text
NAME             STATUS
gabojago-mysql   Up ... (healthy)
```

데이터 수집과 적재 명령은 다시 `data-import` 디렉터리에서 실행한다.

```bash
cd ../data-import
```

## 1. TourAPI 원본 수집

전체 유형을 수집한다.

```bash
.venv/bin/python scripts/collect_tour_api.py
```

이미 저장된 페이지는 다시 다운로드하지 않고 재사용한다. 수집이 중단돼도 같은 명령을
다시 실행하면 이어서 진행된다.

연결 확인을 위해 관광지 한 페이지만 수집하려면 다음과 같이 실행한다.

```bash
.venv/bin/python scripts/collect_tour_api.py \
  --content-type 12 \
  --max-pages 1
```

특정 유형만 전체 수집할 수도 있다.

```bash
.venv/bin/python scripts/collect_tour_api.py --content-type 39
```

음식점 세부 분류(`lclsSystm3`)별 표본도 수집할 수 있다. 각 코드는 별도 하위
디렉터리에 저장되므로 기존 전체 음식점 수집 원본과 페이지 이름이 충돌하지 않는다.

```bash
.venv/bin/python collect_tour_api.py --content-type 39 --num-of-rows 40 --max-pages 1 \
  --classification-code FD010100 \
  --classification-code FD020100 \
  --classification-code FD020200 \
  --classification-code FD020300 \
  --classification-code FD030100 \
  --classification-code FD050100 \
  --classification-code FD050200
```

TourAPI는 세부 분류 필터에 `lclsSystm1`, `lclsSystm2`, `lclsSystm3`를 함께 요구한다.
수집기가 코드에서 상위 두 값을 자동으로 유도한다.

수집 결과는 다음 위치에 저장된다.

```text
data/raw/tourism/tour-api/
├── 12_관광지/
├── 14_문화시설/
├── 15_축제공연행사/
├── 25_여행코스/
├── 28_레포츠/
├── 32_숙박/
├── 38_쇼핑/
└── 39_음식점/
```

각 디렉터리에는 페이지별 원본 응답이 저장된다.

```text
12_관광지/
├── page-00001.json
├── page-00002.json
└── ...
```

## 2. MySQL 적재 전 검사

먼저 dry-run을 실행한다.

```bash
.venv/bin/python scripts/import_tour_api_to_mysql.py
```

dry-run은 JSON을 읽고 다음 내용을 검사하지만 DB를 변경하지 않는다.

- 전체 원본 건수
- 전국 적재 대상 건수
- 신규 등록 예정 건수
- 기존 데이터 갱신 예정 건수
- 지원하지 않는 유형
- 필수값 및 좌표 오류
- 새로 생성할 광역지역 건수

적재 지역을 별도로 제한하지 않는다. 원본의 `lDongRegnCd`와 주소를 기준으로 전국
광역지역을 자동으로 구성하고 모든 장소를 연결한다. 기존 시군구 지역이 있으면 주소가
일치하는 장소는 더 구체적인 시군구에 연결한다.

## Canonical Taxonomy 표본 검증

`RESTAURANT`와 `CAFE`의 설계는 이름만 보고 자동 분류하지 않는다. 실제 Provider
장소 100건을 표본으로 뽑아, 검토자가 원본 카테고리·메뉴·상세 페이지 등 근거와
제안한 facet을 기록하고 오프라인 검증한다. 이 과정은 DB를 읽거나 변경하지 않는다.

먼저 공식 TourAPI 음식점 원본을 한 페이지(최대 100건) 이상 수집한다.

```bash
.venv/bin/python collect_tour_api.py \
  --content-type 39 --max-pages 1 \
  --reports data/reports/restaurant
```

그 원본에서 100건의 검토용 CSV를 만든다. CSV의 `source_place_id`와 원본 분류는
자동으로 채워지고, taxonomy와 facet 및 evidence는 사람이 입력한다.

```bash
.venv/bin/python validate_food_taxonomy.py extract \
  --output data/reports/restaurant/food-taxonomy-samples.csv
```

원천 코드 기반의 1차 제안은 아래 명령으로 만든다. 이 명령은 안전한 코드 매핑만 채우며,
`ASIAN` Variant·베이커리·기타 외국식처럼 근거가 불충분한 값은 `NEEDS_MANUAL`로 남긴다.

```bash
.venv/bin/python validate_food_taxonomy.py propose \
  --output data/reports/restaurant/food-taxonomy-proposals.csv
```

`primary_taxonomy`에는 `RESTAURANT`, `CAFE`, `BAR`, `SHOPPING`, `ACTIVITY`,
`POINT_OF_INTEREST`, `ACCOMMODATION` 중 하나를 입력한다. 여러 값인 facet은
`|`로 구분한다. `cuisine`, `cuisine_variant`, `dining_format`, `signature_menu`는 음식점 전용이고,
`primary_beverage`, `offering`, `cafe_service_style`은 카페 전용이다. `evidence`에는
메뉴·Provider 카테고리·상세 페이지에서 확인한 근거를 적는다.

검토를 마친 뒤 검증과 JSON 보고서 생성을 실행한다.

```bash
.venv/bin/python validate_food_taxonomy.py validate \
  --input data/reports/restaurant/food-taxonomy-samples.csv \
  --report data/reports/restaurant/food-taxonomy-validation.json
```

검증기는 다음을 확인한다.

- 표본 수가 최소 100건인지
- 원천 식별자와 분류 근거가 있는지
- `RESTAURANT`/`CAFE`에 맞지 않는 facet 혼합이 없는지
- 정의되지 않은 facet 값 및 기존 subtype (`GENERAL_CAFE`, `SNACK_FAST_FOOD` 등)이 없는지
- `primary_beverage`가 단일값인지

근거 또는 facet이 빠진 표본은 `검토 필요`로 보고하며, taxonomy 규칙 위반은 오류로
처리해 명령이 실패한다. 이 보고서는 후속 data-import 매핑 전환의 회귀 검증 표본으로
재사용한다.

### POINT_OF_INTEREST 표본 검증

`POINT_OF_INTEREST`는 TourAPI `관광지`라는 원천 이름과 동일하지 않다. 먼저 TourAPI
관광지(12)·문화시설(14) 원본을 수집한 뒤, 확인된 유적·자연·박물관·공원 등 `poi_kind`
별로 층화 표본을 만든다. 체험시설·테마파크·포괄 관광지 코드는 원천 코드만으로 POI를
확정할 수 없으므로 이 표본 자동 제안에서 제외한다.

```bash
.venv/bin/python collect_tour_api.py --content-type 12 --max-pages 1
.venv/bin/python collect_tour_api.py --content-type 14 --max-pages 1

.venv/bin/python validate_poi_taxonomy.py extract \
  --output data/reports/poi/poi-taxonomy-samples.csv \
  --per-kind 10
```

생성된 행은 `primary_taxonomy=POINT_OF_INTEREST`, `activity_offering_state=UNKNOWN`,
`event_state=UNKNOWN`인 **제안**이다. `UNKNOWN`은 체험이나 행사가 없다는 뜻이 아니다.
검토자는 공식 상세 근거로 `poi_kind`를 확정하고, 실제 체험 또는 시간성 행사가 확인된
경우에만 각각 `CONFIRMED_PRESENT`와 전용 evidence 열을 채운다. 이벤트 자체는 Place가
아니므로 이 CSV에서 Event 행을 만들지 않는다.

```bash
.venv/bin/python validate_poi_taxonomy.py validate \
  --input data/reports/poi/poi-taxonomy-samples.csv \
  --report data/reports/poi/poi-taxonomy-validation.json
```

검증기는 `poi_kind` 어휘, 원천 유형(12·14), 최종 primary와 `poi_kind`의 일치, Offering·Event
존재를 주장할 때의 별도 근거를 검사한다. 아직 검토하지 않은 제안은 오류가 아니라
`검토 필요`로 보고한다.

### 인터넷 장소 표본 수집

TourAPI 원천 분류와 독립된 검증 표본은 OpenStreetMap에서 실제 음식점·카페 후보를
각각 50건씩 수집해 만든다. 수집기는 10개 도시의 후보를 순환 선택하며, timeout 또는
실패 시 보조 Overpass endpoint로 fallback한다. OSM의 `amenity` 태그는 후보 모집과
원천 URL 보존용일 뿐 Canonical 정답이 아니다.

```bash
.venv/bin/python collect_taxonomy_validation_samples.py \
  --output data/reports/internet-food-taxonomy-samples.csv
```

생성 CSV는 `review_status=PENDING`이며 taxonomy와 facet을 비워 둔다. 검토자는 공식
매장 홈페이지·공식 메뉴·공식 지도 상세의 재확인 가능한 근거를 기입한 뒤,
`validate_food_taxonomy.py validate`로 검사한다. 리뷰 본문은 수집하지 않는다.

## 3. MySQL 실제 적재

dry-run 결과를 확인한 후 `--write`를 붙여 실행한다.

```bash
.venv/bin/python scripts/import_tour_api_to_mysql.py --write
```

먼저 100건만 시험 적재하려면 다음과 같이 실행한다.

```bash
.venv/bin/python scripts/import_tour_api_to_mysql.py --write --limit 100
```

적재 프로그램은 다음 순서로 처리한다.

```text
JSON 읽기
    ↓
전국 광역지역 생성 및 연결
    ↓
필수값 및 좌표 검증
    ↓
서비스 PlaceType으로 변환
    ↓
하나 이상의 SUBTYPE Category 연결
    ↓
contentid 중복 확인
    ↓
MySQL INSERT 또는 UPDATE
```

## 유형 변환

| TourAPI 코드 | 원본 유형 | PlaceType |
|---:|---|---|
| 12 | 관광지 | `TOURIST_SPOT` |
| 14 | 문화시설 | `TOURIST_SPOT` |
| 15 | 축제·공연·행사 | `TOURIST_SPOT` |
| 28 | 레포츠 | `ACTIVITY` |
| 32 | 숙박 | `ACCOMMODATION` |
| 38 | 쇼핑 | `SHOP` |
| 39 | 음식점 | `RESTAURANT` |

음식점 중 TourAPI 카페 분류 코드에 해당하는 장소는 `CAFE`로 저장한다.

## SUBTYPE 다중 분류

장소 하나에는 SUBTYPE을 여러 개 연결할 수 있다. CSV에서 같은 원본 분류 코드를
여러 행으로 작성하면 각 대상 SUBTYPE이 모두 `place_categories`에 저장된다.

```csv
source_content_type_id,source_lcls3_code,target_place_type,target_subtype_code,target_subtype_name,mapping_status
12,EX020200,TOURIST_SPOT,EXPERIENCE_CENTER,체험시설,CONFIRMED
12,EX020200,TOURIST_SPOT,EXHIBITION_CENTER,전시·컨벤션시설,NEEDS_REVIEW
```

동일한 `(원본 유형, 원본 분류, 대상 SUBTYPE)` 조합은 중복 작성할 수 없고, 같은
원본 분류에 연결된 모든 SUBTYPE은 동일한 PlaceType이어야 한다. 재실행하면 해당
Place의 SUBTYPE 연결을 새 매핑 집합으로 교체한다. PURPOSE 연결은 삭제하지 않는다.

SUBTYPE 정의의 원본은 Java `PlaceSubtypeCode` Enum이다. Python은 Category를
생성하거나 이름을 수정하지 않는다. CSV의 코드가 Enum에 없거나 한글 이름이 다르면
적재를 시작하기 전에 즉시 실패한다.

`25 여행코스`는 하나의 장소가 아니라 여러 장소로 구성된 코스이므로 `places` 적재
대상에서 제외한다.

## 중복 처리

TourAPI 장소는 다음 조합으로 식별한다.

```text
source_type = TOUR_API
source_place_id = TourAPI contentid
```

처음 실행하면 새로운 장소를 INSERT한다. 같은 원본을 다시 실행하면 중복 행을 만들지
않고 기존 장소 정보를 UPDATE한다.

## MySQL 데이터 확인

MySQL 콘솔에 접속한다.

```bash
docker exec -it gabojago-mysql \
  mysql --default-character-set=utf8mb4 -uroot -p gabojago
```

## 부산 도시철도 정적 데이터

도시철도 적재는 Python이 DB를 직접 수정하지 않는다. 백엔드의 CSV 파서와 전체 교체
트랜잭션을 호출하므로, 서비스가 사용하는 역명 보정·시간표 검증 규칙과 동일하게
처리된다.

원본은 `mappings/`에 두며 제공기관·인코딩·기준일은
[`mappings/busan_metro_manifest.json`](mappings/busan_metro_manifest.json)에서 관리한다.

```text
정적 그래프 → 공식 역 중심 좌표 → OSM 출입구 → 열차 시간표
```

### 1. 현재 DB의 출입구 CSV export

현재 로컬 DB에 이미 검수한 출입구 데이터가 있다면 먼저 export한다. 이 명령은
`metro_station_access_points`를 읽기만 하며, 생성된 CSV를 다음 적재의 정본으로 쓴다.

```bash
.venv/bin/python scripts/export_busan_metro_access_points.py
```

출력 경로는 `mappings/busan_metro_access_points.csv`이며, 팀원이 같은 결과를 재현할 수
있도록 원본 CSV와 함께 커밋한다.

### 2. 적재 전 전체 검증

기본 명령은 DB를 수정하지 않고 정적 그래프·역 좌표·출입구·시간표를 모두 검증한다.

```bash
.venv/bin/python scripts/import_busan_metro.py
```

출입구 CSV를 아직 export하지 않은 초기 상황에서만 아래 옵션으로 나머지 세 종류를
검증할 수 있다. 이 경우 출구 번호 안내는 재현되지 않는다.

```bash
.venv/bin/python scripts/import_busan_metro.py --without-access-points
```

### 3. 실제 전체 교체 적재

검증을 통과한 뒤에만 `--write`를 붙인다. 각 단계는 현재 데이터 전체를 교체하므로
부분 원본으로 운영 DB를 갱신하지 않는다.

```bash
.venv/bin/python scripts/import_busan_metro.py --write
```

실행 보고서는 `data/reports/busan-metro-import-*.json`에 남는다. 이 보고서에는 원본별
SHA-256만 기록하며 DB 비밀번호나 환경 변수 값은 기록하지 않는다.

비밀번호 입력 안내가 나오면 `.env`의 `DB_PASSWORD` 값을 입력한다.

접속 후 전체 장소 건수를 확인한다.

```sql
SELECT COUNT(*) AS total_places
FROM places;
```

TourAPI로 적재한 장소 건수를 확인한다.

```sql
SELECT COUNT(*) AS tour_api_places
FROM places
WHERE source_type = 'TOUR_API';
```

지역별 적재 건수를 확인한다.

```sql
SELECT
    r.name AS region,
    COUNT(*) AS count
FROM places p
JOIN regions r ON r.id = p.region_id
WHERE p.source_type = 'TOUR_API'
GROUP BY r.id, r.name
ORDER BY r.id;
```

장소 유형별 건수를 확인한다.

```sql
SELECT
    place_type,
    COUNT(*) AS count
FROM places
WHERE source_type = 'TOUR_API'
GROUP BY place_type
ORDER BY place_type;
```

실제 저장된 장소 일부를 확인한다.

```sql
SELECT
    id,
    name,
    place_type,
    address,
    latitude,
    longitude,
    source_place_id
FROM places
ORDER BY id DESC
LIMIT 20;
```

원본 ID가 중복 저장됐는지 확인한다. 조회 결과가 없으면 중복이 없는 것이다.

```sql
SELECT
    source_place_id,
    COUNT(*) AS duplicate_count
FROM places
WHERE source_type = 'TOUR_API'
GROUP BY source_place_id
HAVING COUNT(*) > 1;
```

확인이 끝나면 MySQL 콘솔을 종료한다.

```sql
exit
```

## 실행 보고서

수집 및 적재 결과는 다음 디렉터리에 JSON 보고서로 저장된다. Canonical 중분류별
검증 보고서는 하위 폴더에 분리한다.

```text
data/reports/
├── restaurant/  # RESTAURANT 원본 수집·taxonomy 검증 보고서
├── cafe/        # CAFE 검증 보고서
└── bar/         # BAR 검증 보고서
```

보고서에는 전체 건수, 신규 건수, 갱신 건수, 제외 건수와 오류 건수가 기록된다.

## Git 관리

다음 파일은 Git에 포함하지 않는다.

```text
.env
.venv/
data/raw/**
data/reports/*
data/rejected/*
```

원본 JSON은 다시 수집할 수 있는 생성 데이터이므로 Git에 올리지 않는다. Git에는 Python
코드, 테스트, 설정 예시와 빈 디렉터리를 유지하기 위한 `.gitkeep`만 포함한다.

## 테스트

```bash
.venv/bin/pytest -q
.venv/bin/ruff check .
```
