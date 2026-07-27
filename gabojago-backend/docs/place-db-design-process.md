# Place 도메인 DB 설계 진행 기록

`place-classification-design.md`가 **최종 설계 스펙**이라면, 이 문서는 **그 스펙에 도달하기까지의 과정과 판단 근거**를 기록한다. 왜 이 구조를 골랐고, 무엇을 검토했다 버렸는지, 지금 무엇이 열려 있는지를 남겨서 나중에 "왜 이렇게 했더라"를 다시 겪지 않기 위함이다.

---

## 1. 출발점

지도 기반 장소 탐색/여행 코스 추천 서비스에서 장소 데이터를 어떻게 분류할지 설계 요청으로 시작했다. 핵심 전제는 처음부터 명확했다.

```text
브랜드명 기반 판단 X (같은 카페 브랜드도 지점마다 환경이 다름)
대분류 기반 판단 X ("카페"로 뭉뚱그리지 않음)
리뷰 많은 순 판단 X

지점별 실제 환경 기반 분류 O
세분화 필드 기반 자동 분류 O
카테고리별 필수 조건 통과 O
중복 없는 보조 필터 O
```

이 전제를 바탕으로 `docs/place-classification-design.md`의 초안을 작성했다: `places` / `place_attributes` / `categories` / `place_categories` 4테이블 구조, EAV 방식의 세분화 필드, 자동 분류 로직.

---

## 2. 설계 자체에 대한 비판적 검토

초안을 그대로 채택하지 않고, 먼저 이 구조의 약점을 스스로 점검했다.

- **EAV 구조의 비용**: `attribute_value`가 문자열이라 타입 안전성이 없고, `MANY > NORMAL > LIMITED` 같은 서열 비교를 SQL로 못 한다. → 이건 감수하기로 했다. Java 배치가 분류를 전담하는 구조라 SQL에서 직접 서열 비교할 일이 없고, 반대로 얻는 유연성(컬럼 추가 없이 속성 확장)이 더 크다고 판단.
- **어휘 통제 부재**: `attribute_key`/`attribute_value`가 자유 문자열이면 오타(`MNAY`)나 같은 뜻의 다른 키가 섞여 데이터가 오염된다. → **Java enum으로 키·값을 강제**하기로 했다. 이게 나중에 `AttributeKey` enum이 된다.
- **정합성 문제**: `place_categories`(분류 결과)가 `place_attributes`(원인)에서 파생되는 값인데, 속성이 바뀌어도 분류가 안 바뀌면 거짓 정보가 쌓인다. → **속성 변경 시 같은 트랜잭션에서 즉시 재분류**하는 규칙을 세웠다.

이 세 가지 보완을 반영해 설계 문서를 갱신했다. (섹션 9 "데이터가 더러워지지 않게 막는 장치")

---

## 3. DB 벤더 선택: MySQL vs PostgreSQL

처음엔 추상적으로 "어느 게 이상적인가"로 논의를 시작했으나, 실제로는 이미 `build.gradle`에 `mysql-connector-j`가 있고 `application.yml`도 MySQL로 설정되어 있어 그린필드가 아니었다.

- PostgreSQL이 우세한 지점(JSONB, PostGIS, 분석 쿼리 편의성)을 하나씩 대입해봤는데, 우리 설계는 이미 EAV(row) 방식이라 JSONB의 이점이 무력화되고, 필요한 공간 검색은 위경도 범위 검색으로 충분해 PostGIS도 불필요했다.
- 결론: **MySQL 8 유지**. 팀 숙련도와 기존 스택을 갈아엎을 이유가 없었다.
- 이후 "지금이 사실상 그린필드다"라는 재확인이 나왔을 때 다시 판단했지만, 스키마가 표준 관계형이라 이 선택 자체가 나중에 발목 잡을 일이 없다는 결론은 그대로 유지했다.

---

## 4. 서비스 비전 전체와의 정합성 검증

`docs/test.md`(코스 추천 플로우)와 `docs/service-concept.md`(전체 기획)를 검토하며, 지금 설계가 "코스 추천"까지 포함한 최종 비전에서도 버티는지 확인했다.

핵심 발견: 코스 추천의 요구사항(슬롯 순서 구성, 장소 고정, 슬롯 교체, 카테고리 제외 후 재추천, 주차장 연결)은 전부 **장소 엔진(1층) 위에 얹히는 추가 계층**이지, 장소 엔진 자체를 수정해야 하는 요구가 아니었다.

```text
[4층] 근거/룰        place_attribute_evidences, category_rules
[3층] 개인화/점수     user_preferences, user_exclusions, place_categories.score
[2층] 코스 도메인     courses, course_slots, place_nearby_parkings
[1층] 장소 엔진 ★현재  places, place_attributes, categories, place_categories
```

이 계층 구조를 발견하면서 한 가지가 드러났다: `test.md`의 "CHINESE 카테고리 제외", `service-concept.md`의 "빈티지 옷가게" 같은 분류는 우리가 원래 상정한 "목적 카테고리"(공부하기 좋은 카페)와 성격이 다르다는 것. 하나는 "무엇에 좋은가"(목적, 자동 판정), 하나는 "무엇인가"(종류, 수집 시점에 확정되는 사실)였다. 이 둘을 구분 없이 섞으면 우리가 스스로 세운 "겹치는 카테고리 금지" 원칙을 어기게 된다.

→ `categories`에 **`kind`(PURPOSE/SUBTYPE) 컬럼**을 추가하기로 결정.

---

## 5. 대분류 확장성 검증 (관광지·액티비티·숙소)

서비스가 관광 데이터 공모전 기반이라 카페 하나로 끝나지 않는다는 점을 확인하고, `TOURIST_SPOT`, `ACTIVITY`, `ACCOMMODATION`까지 같은 패턴으로 확장 가능한지 검증했다.

결론: 대분류가 늘어나도 **테이블은 그대로**고, 늘어나는 건 (1) 해당 타입 전용 세분화 필드 목록, (2) 그 필드로 판정하는 PURPOSE 카테고리 규칙뿐이었다. `place_attributes`가 EAV라 컬럼 변경이 필요 없다는 애초의 설계 의도가 여기서 검증됐다.

숙소는 한 가지 예외를 명시적으로 배제했다: **날짜별 가격·예약 가능 여부는 저장하지 않는다.** 이건 매일 바뀌는 값이라 "지점의 고정적인 환경 특성"을 다루는 `place_attributes`에 넣으면 항상 틀린 데이터가 된다. 우리 역할은 "이 숙소가 조용한지, 뷰가 좋은지"까지고, 실시간 가격은 범위 밖으로 그었다.

---

## 6. 코드 재구축 (설계 → 실제 엔티티)

설계가 확정된 뒤, 기존 place 패키지를 전면 재구축했다.

**삭제한 옛 모델**: `PlaceSuitability`, `PlaceRelation`, `ParkingInfo`와 관련 enum 10개. 이들은 "적합도 점수 + 근거 JSON" 방식이었는데, 우리가 확정한 "카테고리 자동 분류 + 상태값" 방식과 근본적으로 다른 모델이라 병행 유지가 불가능했다.

**새로 만든 것**:
- `Place`, `PlaceAttribute`, `Category`, `PlaceCategory` — 설계 문서의 4테이블 그대로
- `AttributeKey` — 세분화 필드 어휘 사전. 대분류별 적용 가능 필드와 허용값을 코드로 강제. 정의에 없는 키·값은 `PlaceAttribute.validate()`에서 저장 자체를 거부
- `CategoryKind`(PURPOSE/SUBTYPE), `PlaceCategoryStatus`(INCLUDED/EXCLUDED/NEED_REVIEW)
- `PlaceClassificationService` — 세분화 필드를 카테고리별 필수 조건에 대조해 자동 분류. 조건 중 하나라도 명확히 어긋나면 EXCLUDED, 값이 없거나 UNKNOWN이면 NEED_REVIEW, 전부 충족하면 INCLUDED

**TourAPI 수집 파이프라인 재연결**: `TourPlaceWriter`가 기본 정보는 `places`에, 음식점의 한식/중식 같은 구분은 SUBTYPE 카테고리로 자동 연결하도록 수정. PURPOSE 카테고리는 여기서 만들지 않는다 — 세분화 필드가 아직 없기 때문에, 이건 전적으로 사람이 속성을 입력한 뒤 `PlaceClassificationService`가 담당한다.

---

## 7. Region의 숨은 결합 문제 발견과 수정

재구축 과정에서 `Region`(지역 기준 정보)을 그대로 유지하기로 했으나, 점검 중 문제를 발견했다.

`Region.tourAreaCode`가 `NOT NULL`이었고, `regionKey`(고유 식별자)가 `"TOUR:" + 코드` 형태로 TourAPI 채번 방식에 직접 묶여 있었다. 이 상태로는 **TourAPI 코드 없이 Region을 만드는 것 자체가 불가능**했다.

이게 문제가 되는 이유: Region이라는 개념(지역 계층)은 데이터 소스와 무관하게 필요하지만, 지금 구현은 TourAPI를 못 쓰게 되는 순간 새 지역 등록이 막히는 구조였다. 더 심각한 건, 이 결합이 `regionKey`라는 **이미 다른 테이블이 참조하는 식별자**에 박혀 있어서, 데이터가 쌓인 뒤에 고치려면 코드 수정이 아니라 마이그레이션이 필요해진다는 점이었다.

→ 지금(운영 데이터 없음, 수정 비용 0)에 고쳤다:
- `tourAreaCode`를 nullable로 완화
- `regionKey`를 소스 중립 슬러그(`busan`, `gyeongnam.changwon`)로 변경
- TourAPI/DataLab 코드는 `assignTourApiMapping()` / `assignDatalabCode()`로 나중에 붙이는 선택적 매핑으로 분리

---

## 8. 코드 컨벤션 정리

설계와 별개로, 프로젝트 전반의 코드 스타일을 이 재구축 작업에 맞춰 통일했다.

- **Enum**: 값을 가진 enum은 물론, 마커 enum(예: `RegionLevel { AREA, SIGUNGU }`)도 전부 `@Getter @RequiredArgsConstructor` + 한글 라벨(`description`) 형태로 통일. `AttributeKey`처럼 이미 목적형 필드·검증 로직을 가진 enum은 예외로 유지.
- **예외**: raw JDK 예외(`IllegalArgumentException` 등) 대신 기존 `ErrorCode` + `BusinessException` 체계로 통일. 단, 기동 시 fail-fast(`static{}` 블록)와 내부에서 소비되는 제어흐름용 예외는 그대로 뒀다.
- **중첩 타입 참조**: `Outer.Inner` 풀네임 반복 대신 직접 import해서 짧은 이름으로 사용.

이 세 가지는 place 도메인뿐 아니라 `member`, `route` 패키지의 기존 코드까지 점검해 동일 기준으로 맞췄다.

---

## 9. 실제 구동 검증

설계와 코드가 실제로 동작하는지, Docker 환경을 완전히 초기화하고 처음부터 검증했다.

1. `docker compose down -v`로 기존 MySQL 볼륨(옛 스키마) 완전 삭제
2. `docker compose up -d --build`로 새 코드 기준 재빌드 및 기동 — 3초 내 정상 기동, 예외 없음
3. MySQL에 실제 생성된 스키마를 직접 조회해 설계와 일치하는지 확인 (테이블 5개, enum 컬럼, 유니크 제약 4개 — `(place_id,attribute_key)`, `(place_id,category_id)`, `(place_type,code)`, `(source_type,source_place_id)`)
4. 실제 API 호출로 end-to-end 흐름 검증:
   - 장소를 만들고 `PUT /places/{id}/attributes`로 카페 5개 필드 입력 → 응답에서 `STUDY_WORK`가 자동으로 `INCLUDED`, 조건 미입력 카테고리는 `NEED_REVIEW`로 정확히 판정
   - 정의에 없는 값(`VERY_MANY`) 입력 → `400 INVALID_PLACE_ATTRIBUTE` + 허용값 목록으로 거부 (오염 방지 실동작 확인)
   - 조건을 위반하는 값으로 변경 → 같은 트랜잭션에서 `INCLUDED`가 `EXCLUDED`로 즉시 재분류 (정합성 규칙 실동작 확인)
5. 검증에 쓴 테스트 데이터는 정리해 테이블을 다시 빈 상태로 되돌림

---

## 10. 실 데이터 수집 (1차)

`POST /api/place-admin/import/busan-changwon`으로 TourAPI에서 부산·창원 실 데이터를 적재했다. 결과: 부산 140 + 창원 107 = **247개 장소**, 6개 타입(관광지 79, 카페 40, 음식점 40, 액티비티 33, 상점 28, 숙소 27)에 걸쳐 실패·스킵 없이 적재됨. 음식점의 SUBTYPE(한식/중식 등) 자동 연결도 확인됨.

이 시점에서 채워진 것은 `places`(기본정보)와 SUBTYPE `place_categories`뿐이다. PURPOSE 카테고리는 세분화 필드가 없어 아직 0개 — 다음 단계(11번)로 이어진다.

---

## 11. 팀 브랜치 충돌 발견과 분리

재구축 작업을 `feature/tourism` 브랜치에 커밋(`2b34b5f`) 후 push하려다 non-fast-forward로 거부됐다. 원인을 조사한 결과, 같은 브랜치에 팀원이 다른 커밋 2개를 이미 푸시해둔 상태였고, 그 커밋들은 **우리가 이번에 삭제한 옛 모델(`PlaceSuitability`, `PlaceRelation`, `ParkingInfo`)을 그대로 유지한 채, 그 위에 새 추천 엔진(`BeamSearchRoutePlanner`, `PlaceScoringService` 등)을 얹은 상태**였다.

이건 단순한 "뒤처짐"이 아니라 같은 도메인에 대한 서로 다른 설계가 동시에 진행된 정면 충돌이었다. `git pull`로 기계적으로 병합하면 핵심 파일 다수에서 충돌이 나고, 어느 설계를 살릴지는 git이 아니라 사람이 판단해야 하는 문제였다. 원격 브랜치를 건드리지 않고 판단을 미루기 위해 **`feature/place-redesign`이라는 별도 브랜치로 분리**해 현재 작업을 안전하게 보존했다. 두 설계를 어떻게 조율할지는 팀 논의로 남겨둔 상태다.

---

## 12. 현재 열려 있는 것: 속성 수집 전략 (미반영)

DB 구조와 자동 분류 엔진은 완성되고 검증까지 끝났지만, 논의 중 지금 필드 정의에 현실적인 문제가 발견됐고 **아직 코드에는 반영하지 않았다.**

문제: `SEAT_CAPACITY`(좌석 수), `TABLE_SIZE`(테이블 크기), `SEAT_SPACING`(좌석 간격)처럼 물리적 실측 단위로 쪼개진 필드는, 운영자가 사진이나 후기만 보고 판정할 방법이 없다. 이런 필드가 분류 규칙의 필수 조건에 들어가 있으면, 영원히 UNKNOWN으로 남아 해당 카테고리 전체가 NEED_REVIEW에서 못 벗어난다.

논의된 방향(미반영):
- 판정 불가능한 해상도의 필드를 종합 판정값으로 통합. 예: `SEAT_CAPACITY` + `TABLE_SIZE` + `SEAT_SPACING` → `WORK_SEATING`(작업하기 좋은 좌석 환경, GOOD/NORMAL/POOR/UNKNOWN) 하나로.
- 판정 기준은 사진이 아니라 **후기·블로그 등 "직접 가본 사람의 기록"을 서로 다른 출처 2개 이상 교차 확인**하는 것을 표준으로 한다. 확신이 없으면 억지로 값을 찍지 않고 `UNKNOWN`을 유지한다 — 이게 이 서비스가 "리뷰 많은 순 나열"과 달라지는 지점이다.
- 수집 범위는 전체 필드×전체 장소가 아니라, **데모에 쓸 카테고리 규칙이 요구하는 필드 × 데모에 쓸 장소**로 좁힌다.

이 통합이 반영되면 `AttributeKey` 필드 정리와 `PlaceClassificationService`의 카페 규칙 수정이 함께 필요하다. 다음 작업 세션에서 처리할 항목.

---

## 요약: 지금까지의 의사결정 흐름

```text
1. 지점별 실제 환경 기반 분류라는 핵심 철학 확정
2. 초안의 구조적 약점(어휘 오염, 정합성) 검토 후 보완
3. MySQL 유지 결정 (팀 스택 우선, 벤더 이점 불필요)
4. 코스 추천까지 포함한 전체 비전과 대조 → 계층 구조 확인, PURPOSE/SUBTYPE 분리 결정
5. 관광지·액티비티·숙소로 확장 가능성 검증 (테이블 불변 확인)
6. 설계를 실제 엔티티/서비스로 재구축, 옛 모델 삭제
7. Region의 TourAPI 결합 문제 발견 즉시 수정 (비용이 0일 때)
8. 코드 컨벤션(enum/exception/import) 프로젝트 전반에 통일
9. Docker 환경 초기화 후 스키마·API 흐름 실제 검증
10. TourAPI로 실 데이터 247건 적재
11. 팀 브랜치 설계 충돌 발견, 별도 브랜치로 안전 분리
12. 속성 수집 전략 논의 (필드 해상도 조정) — 다음 단계로 이월
```
