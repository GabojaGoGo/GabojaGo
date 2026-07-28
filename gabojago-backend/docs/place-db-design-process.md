# Place 도메인 DB 사용법

## 구조

```text
places 1─N place_attributes
places 1─N place_categories N─1 categories
```

4개 엔티티만 있다. 새 속성·새 카테고리가 늘어나도 이 테이블 구조는 안 바뀐다.

---

## 각 Entity 역할

### Place — 장소 기본 정보만

이름·주소·좌표·타입. 환경값은 여기 넣지 않는다.

```text
id=23, name="카페 385", placeType=CAFE,
address="부산 ...", latitude=35.xx, longitude=129.xx
```

### PlaceAttribute — 세분화 필드 값 (key-value)

이 장소의 실제 환경. `AttributeKey` enum에 정의된 키·값만 저장 가능 — 정의에 없으면 저장 자체가 거부된다 (오타·유사값으로 데이터 오염되는 걸 막기 위함).

```text
place_id=23, attribute_key=OUTLET_ACCESSIBILITY, attribute_value=MANY
place_id=23, attribute_key=NOISE_LEVEL,          attribute_value=MODERATE
```

### Category — 카테고리 정의

PlaceType별로 사용할 카테고리를 정의하고 `kind`로 두 성격을 구분한다.
`active=false`는 기존 연결을 보존하면서 신규 연결과 사용자 필터 노출을 중단한다.

| kind | 의미 | 예시 |
|---|---|---|
| `PURPOSE` | 이 장소가 무엇에 좋은가. 세분화 필드로 **자동 판정** | 공부/작업하기 좋은 카페 |
| `SUBTYPE` | 이 장소가 무엇인가. 수집 시점에 확정되는 **사실값** | 중식, 한식 |

```text
placeType=CAFE, kind=PURPOSE, code=STUDY_WORK, name="공부/작업하기 좋은 카페"
placeType=RESTAURANT, kind=SUBTYPE, code=CHINESE, name="중식"
```

### PlaceCategory — 장소-카테고리 판정 결과

```text
place_id=23, category_id=(STUDY_WORK), status=INCLUDED
place_id=23, category_id=(BRAND_CAFE), status=INCLUDED
place_id=23, category_id=(LARGE_CAFE), status=INCLUDED
```

한 Place에는 서로 다른 SUBTYPE과 PURPOSE를 각각 여러 개 연결할 수 있다. 동일한
`(place_id, category_id)` 연결만 중복 저장할 수 없다.

| status | 의미 |
|---|---|
| `INCLUDED` | 필수 조건 통과. 사용자에게 노출 |
| `NEED_REVIEW` | 조건 판단할 속성값이 없거나 `UNKNOWN`. 검수 대상 |

조건에 맞지 않는 카테고리는 `place_categories` row를 저장하지 않는다.

`assignment_type`은 연결이 만들어진 방식을 구분한다.

| assignment_type | 의미 |
|---|---|
| `IMPORTED` | TourAPI 등 외부 원본 분류에서 적재 |
| `DERIVED` | PlaceAttribute 규칙으로 자동 분류 |
| `MANUAL` | 운영자 검수로 확정 |

---
SUBTYPE은 원본 적재 또는 수동 검수로, PURPOSE는 속성 규칙 또는 수동 검수로 연결한다.

---

## 봉착한 문제: PlaceAttribute를 누가, 무엇을 보고 채우나

`AttributeKey`의 값들은 결국 사람이 채워야 하는데, **사진만 보고는 판정이 안 되는 필드**가 있다.

`SEAT_CAPACITY`(좌석 수), `TABLE_SIZE`(테이블 크기), `SEAT_SPACING`(좌석 간격) 같은 건 "여기가 공부하기 좋은 카페인가?"를 사진 한 장으로 판정하려는 시도인데, 실제로 공부하기 좋은지는 **직접 앉아봐야, 혹은 앉아본 사람의 후기를 읽어야** 안다. 콘센트 위치, 오래 앉아도 눈치 안 보이는지, 소음이 어느 정도인지 — 전부 사진에 안 찍히는 정보다.

그리고 이건 사실 이 서비스의 본질과 맞닿아 있다: **"사용자가 리뷰를 뒤지며 검수하던 시간을 없애준다"**는 게 우리 서비스의 핵심 가치인데, 그 검수를 우리가 자동화로 회피할 수는 없다. 회피하면 우리가 비판했던 "리뷰 많은 순 나열"과 다를 게 없어진다. 검수 노동이 사라지는 게 아니라 **사용자에게서 우리 쪽으로 옮겨오는 것**이고, 이게 이 서비스의 원가다.

## 앞으로 나아갈 방향

1. **필드 해상도를 판정 가능한 수준으로 통합**: `SEAT_CAPACITY`+`TABLE_SIZE`+`SEAT_SPACING`처럼 쪼개진 물리적 필드를, 후기 한 줄 보고 바로 등급을 매길 수 있는 종합값(예: `WORK_SEATING` — 작업하기 좋은 좌석 환경, GOOD/NORMAL/POOR)으로 합친다. `AttributeKey`와 `PlaceClassificationService` 규칙도 같이 수정.
2. **판정 기준 = 사진이 아니라 "직접 가본 사람의 기록"**: 블로그·후기에서 서로 다른 출처 2개 이상이 같은 내용을 말할 때만 값을 입력한다. 근거가 부족하면 억지로 등급을 찍지 않고 `UNKNOWN`을 유지 — `NEED_REVIEW` 상태가 그대로 검수 대기열이 된다.
3. **핵심 장소는 직접 방문**: 데모의 간판이 될 소수 장소(5~10곳)는 실제로 가서 확인한다. 와이파이 안정성처럼 후기에도 잘 안 남는 값은 이 방법이 유일하다.
4. **범위를 좁혀서 시작**: 전체 필드×전체 장소가 아니라, 데모에 쓸 카테고리 규칙이 요구하는 필드 × 데모에 쓸 장소만 먼저 채운다.
5. **장기적으로는 사용자 제보로 이관**: 출시 후엔 실제 방문자가 최고의 검증자다. "콘센트 많았나요?" 같은 원클릭 제보로 데이터가 스스로 갱신되게 하는 게 최종 목표. 지금은 운영자가 수동으로 하는 걸 임시로 감당하는 단계일 뿐이다.
