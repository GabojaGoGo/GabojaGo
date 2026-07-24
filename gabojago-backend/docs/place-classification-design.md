# 장소 카테고리 자동 분류 및 DB 설계

지도 기반 장소 탐색/여행 코스 추천 서비스에서 장소 데이터를 어떻게 분류하고, 카테고리·세분화 필드·보조 필터·DB 구조를 어떻게 설계할지 정리한 문서다.

이 설계의 최종 관건은 하나다. **서로 겹치지 않는 깨끗한 데이터로 분류를 유지하고, 사용자에게 빠르게 제공하는 것.** 모든 설계 결정은 이 기준으로 판단한다.

---

## 1. 핵심 전제

우리 서비스는 사용자의 자유로운 의도를 자연어로 입력받아 해석하는 서비스가 **아니다**.

사용자가 직접 "조용하고 콘센트 있고 오래 앉기 좋은 카페 찾아줘"처럼 입력하는 방식이 아니라, **우리가 미리 정제해둔 카테고리와 필터를 사용자가 선택하는 구조**다.

핵심 구조는 다음과 같다.

```text
대분류 → 세분화 필드 → 자동 카테고리 분류 → 중복 없는 보조 필터
```

| 단계 | 설명 |
|------|------|
| 대분류 (place_type) | 카페, 음식점, 술집 등 장소의 기본 타입 |
| 세분화 필드 (attribute) | 지점 단위의 실제 환경값 (내부 데이터) |
| 자동 카테고리 분류 | 세분화 필드 값이 기준을 충족하면 목적 카테고리에 자동 편입 |
| 보조 필터 | 카테고리 안에서 결과를 더 좁히는 조건 (카테고리 필수 조건과 중복 금지) |

그리고 이 장소 데이터 구조는 일상 탐색과 여행 코스 추천이 **공유하는 하나의 기반**이다. 코스 추천은 이 기반 위에 얹히는 별도 도메인이지, 장소 데이터 구조를 바꾸는 요구가 아니다 (12장 참고).

---

## 2. 서비스의 핵심 데이터 철학

### 2.1 브랜드가 아니라 지점 단위로 판단한다

장소 이름이나 브랜드명만 보고 판단하지 않는다.

예를 들어 `할리스`라는 이름만 보고 무조건 시끄러운 대형 프랜차이즈 카페라고 판단하면 안 된다. 중요한 것은 `할리스`라는 브랜드가 아니라 `할리스 강남역점`, `할리스 홍대점` 같은 **지점 단위의 실제 환경**이다.

같은 브랜드라도 지점마다 완전히 다를 수 있다.

```text
할리스 A점:
- 좌석 많음
- 테이블 넓음
- 충전 포트 많음
- 늦게까지 영업
- 통창뷰 있음
- 개방감 있음
- 적당한 생활 소음
- 오래 앉기 부담 적음

→ 공부/작업하기 좋은 카페로 분류 가능
→ 대화하기 좋은 카페로 분류 가능
→ 뷰 좋은 카페로 분류 가능
```

반대로 같은 할리스라도 다른 지점이 다음과 같다면 제외될 수 있다.

```text
할리스 B점:
- 좌석 적음
- 콘센트 부족
- 테이블 작음
- 음악 큼
- 회전율 빠름
- 오래 앉기 부담 있음

→ 공부/작업하기 좋은 카페에서 제외
```

### 2.2 핵심 원칙

```text
브랜드명 기반 판단 X
대분류 기반 판단 X
리뷰 많은 순 판단 X

지점별 실제 환경 기반 분류 O
세분화 필드 기반 자동 분류 O
카테고리별 필수 조건 통과 O
중복 없는 보조 필터 O
```

---

## 3. 카테고리 설계 원칙

카테고리는 많이 만드는 것이 목표가 아니다.

카테고리는 **서로 상관도가 낮고, 사용자가 명확하게 구분할 수 있는 값**들로 구성해야 한다. 서로 겹치는 카테고리를 많이 만들면 데이터가 더러워지고 사용자가 헷갈린다.

예를 들어 아래처럼 만들면 안 된다.

```text
공부하기 좋은 카페
작업하기 좋은 카페
독서하기 좋은 카페
조용한 카페
오래 있기 좋은 카페
```

이 값들은 서로 많이 겹친다.

따라서 **카테고리는 사용자의 큰 목적을 대표하는 값**으로 만들고, 세부 조건은 세분화 필드와 보조 필터로 처리한다.

### 3.1 카테고리에는 두 가지 종류가 있다

우리 서비스에는 성격이 다른 두 가지 분류가 등장한다. 이 둘을 섞으면 데이터가 더러워지므로 처음부터 구분한다.

| 종류 | 의미 | 예시 | 정해지는 방식 |
|------|------|------|--------------|
| 목적 카테고리 (PURPOSE) | 이 장소가 어떤 목적에 좋은가 | 공부/작업하기 좋은 카페, 데이트하기 좋은 카페 | 세분화 필드 기준 충족 시 **자동 분류** |
| 종류 카테고리 (SUBTYPE) | 이 장소가 무엇인가 | 중식(음식점), 빈티지(옷가게), 편집샵 | 데이터 수집 시점에 확정되는 **사실 값** |

한 장소는 SUBTYPE을 보통 1개 가지지만(중식집은 중식집이다), PURPOSE는 여러 개 가질 수 있다(한 카페가 공부하기도 좋고 데이트하기도 좋을 수 있다). "중식 제외하고 다시 추천" 같은 기능은 SUBTYPE으로, "공부하기 좋은 카페" 탐색은 PURPOSE로 동작한다.

---

## 4. 카테고리와 필터의 구분

카테고리와 보조 필터는 반드시 역할을 분리해야 한다.

| 개념 | 역할 |
|------|------|
| 카테고리 | 사용자의 큰 목적을 대표하는 값 |
| 세분화 필드 | 실제 장소 환경을 판단하기 위한 내부 데이터 |
| 보조 필터 | 카테고리 안에서 더 좁히는 조건 |

가장 중요한 규칙:

```text
카테고리의 필수 조건은 보조 필터로 다시 묻지 않는다.
```

예를 들어 `공부/작업하기 좋은 카페` 카테고리에 이미 다음 조건이 포함되어 있다면,

```text
- 좌석/테이블 있음
- 일정 시간 체류 가능
- 콘센트 접근성 일정 수준 이상
- 소음이 과하지 않음
```

사용자에게 다시 이런 필터를 물으면 안 된다.

```text
콘센트 있음?
좌석 있음?
오래 있기 좋음?
```

이건 중복이다. 보조 필터는 카테고리 안에서 **더 세밀하게 좁히는 역할**이어야 한다.

```text
공부/작업하기 좋은 카페

기본적으로 이미 충족:
- 좌석/테이블 있음
- 일정 시간 체류 가능
- 콘센트 접근성 일정 수준 이상
- 소음 과하지 않음

보조 필터:
- 콘센트 많은 편
- 1인석 있음
- 늦게까지 영업
- 평일 낮 조용한 편
- 주차 편함
```

---

## 5. 카페 기준 카테고리 예시

카페 대분류에서 MVP 기준으로 고려하는 목적 카테고리는 다음과 같다.

```text
공부/작업하기 좋은 카페
감성카페
데이트하기 좋은 카페
대화하기 좋은 카페
주차 편한 카페
가성비 좋은 카페
독서하기 좋은 카페
```

다만 초기 MVP에서는 너무 많은 카테고리를 만들지 않는다.

**초기 MVP 추천 카테고리:**

| # | 카테고리 | code |
|---|---------|------|
| 1 | 공부/작업하기 좋은 카페 | `STUDY_WORK` |
| 2 | 감성카페 | `EMOTIONAL` |
| 3 | 데이트하기 좋은 카페 | `DATE` |
| 4 | 주차 편한 카페 | `PARKING_FRIENDLY` |

각 카테고리는 이름만 붙이는 것이 아니라, **세분화 필드 값이 특정 기준을 충족할 때 자동으로 분류되는 구조**여야 한다.

---

## 6. 세분화 필드

세분화 필드는 실제 장소 환경을 판단하기 위한 내부 데이터다.

### 6.1 카페 기준 세분화 필드 목록

| 필드 | 의미 |
|------|------|
| `seat_capacity` | 좌석 수 규모 |
| `table_size` | 테이블 크기 |
| `seat_spacing` | 좌석 간 간격 |
| `outlet_accessibility` | 콘센트 접근성 |
| `wifi_stability` | 와이파이 안정성 |
| `noise_level` | 소음 수준 |
| `crowd_level` | 혼잡도 |
| `business_hours` | 영업시간 특성 |
| `stay_duration` | 체류 가능 시간 |
| `space_openness` | 공간 개방감 |
| `interior_mood` | 인테리어 분위기 |
| `natural_light` | 자연 채광 |
| `window_view` | 창가 뷰 |
| `dessert_visual` | 디저트 비주얼 |
| `parking_convenience` | 주차 편의성 |
| `price_level` | 가격대 |
| `solo_visit_suitability` | 1인 방문 적합성 |
| `conversation_suitability` | 대화 적합성 |
| `date_mood` | 데이트 분위기 |

### 6.2 값은 있다/없다가 아니라 상태값으로 관리한다

각 필드는 단순히 있다/없다로만 관리하지 않는다.

콘센트:

```text
outlet_accessibility:
- MANY
- NORMAL
- LIMITED
- NONE
- UNKNOWN
```

주차 (단순 주차 가능 여부가 아님):

```text
parking_convenience:
- GOOD
- NORMAL
- LIMITED
- NEARBY_PARKING
- DIFFICULT
- UNKNOWN
```

소음:

```text
noise_level:
- QUIET
- MODERATE
- LOUD
- TIME_DEPENDENT
- UNKNOWN
```

핵심:

```text
있다/없다 X
상태값/등급/제약/확실도 O
```

`UNKNOWN`을 정식 값으로 두는 것이 중요하다. 데이터가 없는 것과 "없음"이 확인된 것은 다르며, 이 구분이 결과 카드의 솔직한 표현(`확인 필요`)으로 이어진다.

### 6.3 타입별 세분화 필드: 공통 + 전용 조합

세분화 필드는 place_type마다 전부 다른 것이 아니라, **공통 필드 + 타입 전용 필드**의 조합이다. 관광지에 콘센트 필드는 의미가 없고, 카페에 입장료 필드는 의미가 없기 때문이다.

**공통 필드 (거의 모든 타입에 존재):**

| 필드 | 의미 |
|------|------|
| `parking_convenience` | 주차 편의성 |
| `noise_level` | 소음 수준 |
| `crowd_level` | 혼잡도 |
| `price_level` | 가격대 |
| `business_hours` | 영업/운영시간 특성 |

**타입 전용 필드 예시:**

| place_type | 전용 필드 예시 |
|------------|---------------|
| `CAFE` | `outlet_accessibility`(콘센트), `stay_duration`(체류), `dessert_visual`(디저트 비주얼), `date_mood`(데이트 분위기) |
| `RESTAURANT` | `waiting_level`(웨이팅), `group_seating`(단체석), `kids_friendly`(아이 동반), `solo_dining_suitability`(혼밥) |
| `TOURIST_SPOT` | `visit_duration`(관람 소요시간), `entrance_fee_level`(입장료), `photo_spot`(포토스팟), `walking_intensity`(도보 강도), `indoor_outdoor`(실내/실외), `scenery_quality`(경관) |
| `ACTIVITY` | `reservation_required`(예약 필수 여부), `physical_intensity`(체력 강도), `group_size_fit`(적정 인원), `weather_dependency`(날씨 영향), `experience_duration`(체험 소요시간) |

타입 전용 필드도 6.2와 동일하게 상태값으로 관리한다. 예:

```text
walking_intensity (관광지 도보 강도):
- LIGHT          # 가볍게 걷는 수준
- MODERATE       # 어느 정도 걷기 필요
- INTENSE        # 등산/장거리 도보
- UNKNOWN

weather_dependency (액티비티 날씨 영향):
- INDOOR         # 날씨 무관
- OUTDOOR_SAFE   # 야외지만 웬만하면 가능
- WEATHER_BOUND  # 우천 시 불가
- UNKNOWN
```

새 타입을 추가하는 작업은 DB 변경이 아니라 **Java enum에 "이 타입은 이 필드들을 쓴다"는 목록을 추가하는 것**이다. `place_attributes`는 row 방식이라 어떤 조합이 와도 그대로 담긴다.

---

## 7. 자동 카테고리 분류

세분화 필드 값이 카테고리별 필수 조건을 모두 통과하면 해당 목적 카테고리로 자동 분류된다. MVP 단계에서 이 분류 로직은 Java 코드에서 처리한다 (룰 테이블은 추후 확장 — 12장 참고).

분류 코드를 짤 때 한 가지를 지킨다: **통과/탈락 결과만 남기지 말고, 어떤 조건을 어떻게 통과했는지 조건별 판정 결과를 로그로 남긴다.** 나중에 "얼마나 적합한지" 점수로 발전시킬 때 데이터를 다시 모으지 않기 위해서다.

### 7.1 공부/작업하기 좋은 카페

필수 조건:

```text
- seat_capacity가 NORMAL 이상
- table_size가 MEDIUM 이상
- outlet_accessibility가 NORMAL 이상
- noise_level이 LOUD가 아님
- stay_duration이 LONG_OK 또는 NORMAL 이상
```

조건을 충족하면:

```text
→ 공부/작업하기 좋은 카페로 자동 분류
```

### 7.2 데이트하기 좋은 카페

필수 조건:

```text
- interior_mood가 GOOD 이상
- seat_spacing이 NORMAL 이상
- noise_level이 LOUD가 아님
- conversation_suitability가 NORMAL 이상
- date_mood가 GOOD 이상
```

조건을 충족하면:

```text
→ 데이트하기 좋은 카페로 자동 분류
```

### 7.3 독서하기 좋은 카페

필수 조건:

```text
- noise_level이 QUIET 또는 MODERATE
- seat_comfort가 NORMAL 이상
- natural_light 또는 lighting_quality가 NORMAL 이상
- solo_visit_suitability가 GOOD 이상
- stay_duration이 NORMAL 이상
```

조건을 충족하면:

```text
→ 독서하기 좋은 카페로 자동 분류
```

### 7.4 주차 편한 카페

필수 조건:

```text
- parking_convenience가 GOOD 또는 NORMAL
- 또는 nearby_parking_distance가 일정 기준 이하
- 주차 공간이 너무 협소하지 않음
```

조건을 충족하면:

```text
→ 주차 편한 카페로 자동 분류
```

### 7.5 확장 예시: 사진 찍기 좋은 관광지

같은 패턴이 관광지에도 그대로 적용된다. 재료(세분화 필드)만 관광지용으로 바뀔 뿐이다.

필수 조건:

```text
- photo_spot이 GOOD 이상
- scenery_quality가 NORMAL 이상
- crowd_level이 VERY_CROWDED가 아님
```

조건을 충족하면:

```text
→ 사진 찍기 좋은 관광지로 자동 분류
```

---

## 8. 보조 필터 설계

보조 필터는 카테고리와 중복되면 안 된다. 다음 원칙을 따른다.

```text
1. 카테고리 필수 조건을 다시 묻지 않는다.
2. 카테고리 안에서 더 좁히는 역할만 한다.
3. 사용자에게는 짧은 칩 형태로 보여준다.
4. 내부적으로는 상태값/등급으로 관리한다.
5. 결과 카드에서는 실제 상태를 솔직하게 보여준다.
```

### 8.1 예시: 공부/작업하기 좋은 카페

**사용자 선택 화면 (칩 UI):**

```text
[콘센트 많은 편]
[1인석 있음]
[늦게까지 영업]
[평일 낮 조용한 편]
[주차 편함]
```

**내부 데이터 매핑:**

| 칩 | 내부 조건 |
|----|----------|
| 콘센트 많은 편 | `outlet_accessibility = MANY` |
| 1인석 있음 | `solo_visit_suitability = GOOD` |
| 늦게까지 영업 | `business_hours = LATE` |
| 평일 낮 조용한 편 | `noise_level_weekday_daytime = QUIET` |
| 주차 편함 | `parking_convenience = GOOD` |

**결과 카드 표현:**

```text
콘센트: 여러 좌석에서 사용 가능
좌석: 1인 작업 좌석 있음
영업시간: 늦게까지 영업
소음: 평일 낮 조용한 편
주차: 자체 주차장은 있지만 공간은 넓지 않음
```

정보가 불확실하면 솔직하게 `확인 필요`로 보여준다.

주의: 카테고리 필수 조건은 `outlet_accessibility ≥ NORMAL`이고, 보조 필터는 `= MANY`를 요구한다. 같은 필드를 쓰더라도 **더 높은 기준으로 좁히는 것**이므로 중복이 아니다. "콘센트 있음?" 같이 필수 조건을 그대로 다시 묻는 것이 중복이다.

---

## 9. 데이터가 더러워지지 않게 막는 장치

이 서비스의 생명은 데이터의 순도다. 데이터가 한번 오염되면(같은 뜻의 키가 두 개, 오타 값, 겹치는 카테고리) 분류 전체가 신뢰를 잃는다. 그래서 "조심하자"가 아니라 **구조적으로 오염이 불가능하게** 만든다.

### 9.1 속성 키와 값은 코드에 정의된 것만 저장한다

`attribute_key`와 `attribute_value`는 자유 입력 문자열이 아니다. **Java enum에 정의된 키와 값만 저장을 허용**하고, 정의에 없는 값이 들어오면 저장 자체를 거부한다.

```text
막히는 것들:
- 오타: MNAY, QUITE
- 같은 뜻의 다른 키: parking vs parking_convenience
- 정의 안 된 값: outlet_accessibility = "많음"
```

새 속성이 필요하면 enum에 먼저 추가하고(코드 리뷰를 거치고) 나서 데이터를 넣는다. DB 컬럼 추가는 필요 없지만, **어휘 추가는 반드시 코드를 통한다.**

### 9.2 목적과 종류를 섞지 않는다

`categories` 테이블의 `kind` 컬럼으로 PURPOSE(목적)와 SUBTYPE(종류)를 구분한다 (3.1 참고). "공부하기 좋은 카페"와 "중식"은 같은 테이블에 있어도 절대 같은 성격으로 다루지 않는다.

### 9.3 새 카테고리를 만들기 전에 겹침 검사를 한다

새 목적 카테고리를 추가하고 싶을 때 다음을 먼저 묻는다.

```text
1. 기존 카테고리와 대상 장소가 크게 겹치는가?
   → 겹치면 카테고리가 아니라 보조 필터로 만든다.
2. 사용자가 기존 카테고리와 명확히 구분해서 고를 수 있는가?
   → 구분이 애매하면 만들지 않는다.
```

예: "독서하기 좋은 카페"는 "공부/작업하기 좋은 카페"와 겹침이 크다. MVP에서 별도 카테고리로 두지 않고, 필요하면 보조 필터(`조용한 편`, `1인석 있음`)로 커버하는 것을 우선 검토한다.

### 9.4 같은 사실은 한 곳에만 저장한다

```text
장소 기본 정보   → places 에만
환경값          → place_attributes 에만
분류 결과       → place_categories 에만
```

예를 들어 "주차 편한지"를 places 컬럼에도 넣고 attributes에도 넣으면, 둘이 어긋나는 순간 어느 쪽이 진실인지 알 수 없게 된다. 한 사실의 저장 위치는 반드시 한 곳이다.

### 9.5 속성이 바뀌면 분류를 즉시 다시 계산한다

`place_categories`는 `place_attributes`에서 계산된 결과물이다. 속성값이 갱신되면 **같은 배치 안에서 그 장소의 카테고리 분류를 반드시 다시 계산**한다. 이 규칙이 없으면 "콘센트가 없어진 카페가 여전히 공부하기 좋은 카페로 노출되는" 어긋남이 조용히 쌓인다.

### 9.6 모르는 값은 UNKNOWN으로 명시한다

값을 모르면 row를 안 넣거나 추측값을 넣는 게 아니라 `UNKNOWN`을 넣는다. "확인 안 됨"도 데이터다.

### 9.7 DB 제약으로 중복 row를 차단한다

사람과 코드가 실수해도 DB가 마지막에 막도록 유니크 제약을 건다 (11.6 참고).

---

## 10. DB 선택: MySQL 8

**MySQL 8로 확정한다.** 이유는 단순하다.

- 팀이 이미 익숙하고, 프로젝트가 이미 MySQL 기준으로 세팅되어 있다.
- 우리 규모(장소 수만 건, 속성 수십만 row)에서는 어떤 DB를 써도 성능 차이가 없다. 승부처는 DB 종류가 아니라 인덱스와 구조다.
- 지도 조회(화면 영역, 반경 검색)는 위경도 인덱스로 충분하다.
- PostgreSQL이 유리한 기능(행정구역 폴리곤 검색 같은 고급 지도 연산, JSON 검색)은 현재 계획에 없다.

**재검토 조건**: 나중에 폴리곤 기반 지역 검색이나 DB 안에서의 룰 평가가 필요해지면 그때 PostgreSQL 전환을 다시 검토한다. 아래 스키마는 전부 표준 관계형 구조라 어느 DB로든 옮길 수 있으므로, 이 선택이 발목을 잡을 일은 없다.

---

## 11. DB 설계

초기 MVP는 다음 4개 테이블로 시작한다.

```text
places
place_attributes
categories
place_categories
```

```text
places 1 ──── N place_attributes
places 1 ──── N place_categories N ──── 1 categories
```

빠른 제공의 핵심 원리: **분류는 배치에서 미리 계산해 저장해두고, 사용자 조회 시점에는 인덱스를 타는 단순 조회만 한다.** 조회할 때 조건을 계산하지 않는다.

### 11.1 places

장소 기본 정보만 저장한다. **세부 환경값은 절대 places 컬럼으로 넣지 않는다.**

| 컬럼 | 타입 예시 | 설명 |
|------|----------|------|
| `id` | BIGINT (PK) | 장소 ID |
| `name` | VARCHAR | 지점 단위 이름 (예: 할리스 강남역점) |
| `place_type` | VARCHAR | 대분류 |
| `address` | VARCHAR | 주소 |
| `latitude` | DECIMAL | 위도 |
| `longitude` | DECIMAL | 경도 |
| `phone` | VARCHAR | 전화번호 |
| `created_at` | TIMESTAMP | 생성 시각 |
| `updated_at` | TIMESTAMP | 수정 시각 |

`place_type` 값 예시:

```text
CAFE
RESTAURANT
BAR
SHOP
TOURIST_SPOT
ACTIVITY
ACCOMMODATION
PARKING_LOT
```

### 11.2 place_attributes

장소의 세분화 필드 값을 row 형태로 저장한다.

| 컬럼 | 타입 예시 | 설명 |
|------|----------|------|
| `id` | BIGINT (PK) | 속성 row ID |
| `place_id` | BIGINT (FK → places) | 장소 ID |
| `attribute_key` | VARCHAR | 세분화 필드 키 (Java enum에 정의된 값만) |
| `attribute_value` | VARCHAR | 상태값 (Java enum에 정의된 값만) |
| `created_at` | TIMESTAMP | 생성 시각 |
| `updated_at` | TIMESTAMP | 수정 시각 |

예시 데이터:

| place_id | attribute_key | attribute_value |
|----------|---------------|-----------------|
| 1 | `seat_capacity` | `HIGH` |
| 1 | `table_size` | `LARGE` |
| 1 | `outlet_accessibility` | `MANY` |
| 1 | `noise_level` | `MODERATE` |
| 1 | `open_until_late` | `YES` |
| 1 | `window_view` | `YES` |
| 1 | `parking_convenience` | `LIMITED` |

이 구조를 사용하는 이유:

```text
- 새로운 속성이 생겨도 DB 컬럼 추가가 필요 없다.
- 카페, 음식점, 술집, 옷가게 등 여러 대분류에 유연하게 대응할 수 있다.
- 미래에 속성이 100개 이상으로 늘어나도 구조가 무너지지 않는다.
```

대신 이 구조의 약점(아무 문자열이나 들어갈 수 있음)은 9.1의 enum 검증으로 막는다. **유연함은 DB가 담당하고, 엄격함은 코드가 담당한다.**

### 11.3 categories

카테고리 정보를 저장한다. 카테고리는 대분류(`place_type`)에 종속되며, `kind`로 목적/종류를 구분한다.

| 컬럼 | 타입 예시 | 설명 |
|------|----------|------|
| `id` | BIGINT (PK) | 카테고리 ID |
| `place_type` | VARCHAR | 이 카테고리가 속한 대분류 |
| `kind` | VARCHAR | `PURPOSE`(목적) / `SUBTYPE`(종류) |
| `code` | VARCHAR | 카테고리 코드 (예: `STUDY_WORK`) |
| `name` | VARCHAR | 표시 이름 |
| `description` | VARCHAR | 설명 |
| `is_active` | BOOLEAN | 노출 여부 |
| `created_at` | TIMESTAMP | 생성 시각 |
| `updated_at` | TIMESTAMP | 수정 시각 |

예시 데이터:

| place_type | kind | code | name |
|------------|------|------|------|
| `CAFE` | `PURPOSE` | `STUDY_WORK` | 공부/작업하기 좋은 카페 |
| `CAFE` | `PURPOSE` | `DATE` | 데이트하기 좋은 카페 |
| `CAFE` | `PURPOSE` | `EMOTIONAL` | 감성카페 |
| `CAFE` | `PURPOSE` | `PARKING_FRIENDLY` | 주차 편한 카페 |
| `RESTAURANT` | `SUBTYPE` | `CHINESE` | 중식 |
| `SHOP` | `SUBTYPE` | `VINTAGE` | 빈티지 옷가게 |
| `SHOP` | `SUBTYPE` | `SELECT_SHOP` | 편집샵 |

- `PURPOSE`는 세분화 필드 기준 충족 시 자동 분류로 부여된다.
- `SUBTYPE`은 데이터 수집 시점에 확정되는 사실 값이다. 코스 추천에서 "중식 제외" 같은 기능이 이 값으로 동작한다.

### 11.4 place_categories

장소가 어떤 카테고리에 포함되는지 저장한다. 처음부터 점수를 넣지 않아도 된다.

| 컬럼 | 타입 예시 | 설명 |
|------|----------|------|
| `id` | BIGINT (PK) | ID |
| `place_id` | BIGINT (FK → places) | 장소 ID |
| `category_id` | BIGINT (FK → categories) | 카테고리 ID |
| `status` | VARCHAR | 분류 상태 |
| `created_at` | TIMESTAMP | 생성 시각 |
| `updated_at` | TIMESTAMP | 수정 시각 |

`status` 값:

```text
INCLUDED      # 필수 조건 통과 → 노출
EXCLUDED      # 필수 조건 미달 → 제외
NEED_REVIEW   # 데이터 불충분/애매 → 검수 필요
```

예시 데이터:

| 장소 | 카테고리 | status |
|------|---------|--------|
| 할리스 A점 | 공부/작업하기 좋은 카페 | `INCLUDED` |
| 할리스 A점 | 데이트하기 좋은 카페 | `INCLUDED` |
| 할리스 A점 | 감성카페 | `NEED_REVIEW` |
| 할리스 A점 | 주차 편한 카페 | `EXCLUDED` |

`EXCLUDED`를 row로 남기는 이유: "검사했는데 탈락"과 "아직 검사 안 함"을 구분하기 위해서다. 이 구분이 있어야 분류 배치가 어디까지 돌았는지 추적할 수 있다.

### 11.5 다른 장소 타입으로의 확장

이 구조는 카페에 종속되지 않는다. `place_type`별로 세분화 필드 셋과 카테고리 셋만 새로 정의하면 그대로 확장된다. 우리 서비스의 초기 컨셉이 관광이므로, 관광지와 액티비티가 첫 번째 확장 대상이다.

예시 (음식점):

```text
place_type = RESTAURANT

세분화 필드: waiting_level, group_seating, kids_friendly, ...
PURPOSE 카테고리: 혼밥하기 좋은 식당, 단체 모임 좋은 식당, ...
SUBTYPE 카테고리: 한식, 중식, 일식, 양식, ...
```

예시 (관광지):

```text
place_type = TOURIST_SPOT

세분화 필드: visit_duration, entrance_fee_level, photo_spot,
            walking_intensity, indoor_outdoor, scenery_quality, ...
PURPOSE 카테고리: 사진 찍기 좋은 관광지, 아이와 가기 좋은 관광지,
                가볍게 산책하기 좋은 관광지, 비 오는 날 가기 좋은 관광지, ...
SUBTYPE 카테고리: 자연 경관, 역사 유적, 전시/박물관, 테마파크, ...
```

예시 (액티비티):

```text
place_type = ACTIVITY

세분화 필드: reservation_required, physical_intensity, group_size_fit,
            weather_dependency, experience_duration, ...
PURPOSE 카테고리: 커플이 하기 좋은 액티비티, 가족이 하기 좋은 액티비티,
                당일 예약 가능한 액티비티, ...
SUBTYPE 카테고리: 수상 레저, 공방 체험, 테마 체험, ...
```

`place_attributes`는 row 구조라 컬럼 변경 없이 새 필드를 수용하고, `categories`는 `place_type` + `kind`로 분리 관리한다. 어떤 타입을 추가하든 작업 내용은 동일하다: **enum에 필드 목록 정의 → PURPOSE 카테고리와 필수 조건 정의 → 분류 배치에 규칙 추가.** DB 테이블은 그대로다.

### 11.6 제약조건과 인덱스

사람과 코드의 실수를 DB가 마지막에 막고, 조회가 항상 인덱스를 타게 한다.

**유니크 제약 (중복 차단):**

```text
place_attributes:  UNIQUE (place_id, attribute_key)
place_categories:  UNIQUE (place_id, category_id)
categories:        UNIQUE (place_type, code)
```

**인덱스 (빠른 조회):**

```text
places:            INDEX (place_type)
places:            INDEX (latitude, longitude)     -- 지도 화면 영역 조회
place_attributes:  INDEX (attribute_key, attribute_value, place_id)  -- 보조 필터 조회
place_categories:  INDEX (category_id, status, place_id)             -- 카테고리 조회
```

지도 조회는 MVP에서 위경도 범위 검색(BETWEEN)으로 충분하다. 반경 검색이 정밀해져야 하면 그때 MySQL 8의 공간 인덱스(POINT + SPATIAL INDEX)로 올린다 — 컬럼 추가로 가능하며 구조 변경이 아니다.

### 11.7 조회 흐름 예시

"공부/작업하기 좋은 카페 + 콘센트 많은 편" 조회:

```sql
SELECT p.*
FROM places p
JOIN place_categories pc ON pc.place_id = p.id AND pc.status = 'INCLUDED'
JOIN categories c ON c.id = pc.category_id
JOIN place_attributes pa ON pa.place_id = p.id
WHERE p.place_type = 'CAFE'
  AND c.code = 'STUDY_WORK'
  AND pa.attribute_key = 'outlet_accessibility'
  AND pa.attribute_value = 'MANY';
```

카테고리 필수 조건은 이미 배치 분류 시점에 `place_categories.status`로 반영되어 있으므로, 조회 시점에는 카테고리 조인 + 보조 필터 조건만 확인하면 된다. 보조 필터를 여러 개 걸면 `place_attributes` 조인이 필터 수만큼 늘어나는데, 우리 데이터 규모와 11.6의 인덱스면 문제없다.

---

## 12. 성장 계층 구조: 갈아엎지 않고 커지는 경로

이 설계가 코스 추천을 포함한 서비스 전체 비전(docs/service-concept.md)까지 버티는 이유는, 이후의 모든 기능이 기존 테이블의 **수정이 아니라 추가**로 쌓이기 때문이다.

```text
[4층] 근거/룰        place_attribute_evidences, category_rules
[3층] 개인화/점수     user_preferences, user_exclusions, place_categories.score
[2층] 코스 도메인     courses, course_slots, place_nearby_parkings
[1층] 장소 엔진 ★지금  places, place_attributes, categories, place_categories
```

일상 탐색과 여행 코스의 슬롯 교체는 **같은 1층 엔진의 같은 쿼리**를 쓴다. 코스 추천을 만들 때 1층은 한 줄도 바뀌지 않는다.

### 12.1 [2층] 코스 도메인 — 코스 추천 기능 개발 시 추가

**courses** (여행 코스 한 개):

| 컬럼 | 설명 |
|------|------|
| `id` | 코스 ID |
| `user_id` | 사용자 |
| `title` | 코스 이름 |
| `transport_mode` | `PUBLIC_TRANSIT` / `CAR` |
| `start_latitude`, `start_longitude` | 시작 위치 |
| `status` | 작성중/확정 등 |

**course_slots** (코스 안의 자리 하나):

| 컬럼 | 설명 |
|------|------|
| `id` | 슬롯 ID |
| `course_id` | 소속 코스 |
| `slot_order` | 순서 (음식→카페→술→숙소) |
| `slot_type` | `place_type` 재사용 (CAFE, RESTAURANT, ...) |
| `category_id` | 사용자가 고른 목적 카테고리 (없으면 NULL) |
| `place_id` | 확정된 장소 (추천 전이면 NULL) |
| `is_fixed` | 사용자가 직접 고정한 장소인지 ("이 마라탕집은 꼭 간다") |
| `status` | `EMPTY` / `RECOMMENDED` / `CONFIRMED` |

**place_nearby_parkings** (주차 협소 장소 ↔ 추천 주차장 연결):

| 컬럼 | 설명 |
|------|------|
| `place_id` | 대상 장소 |
| `parking_place_id` | 주차장 (places의 `PARKING_LOT` row) |
| `walk_distance_m` | 도보 거리 |

주차장을 별도 테이블이 아니라 places의 한 타입으로 둔 덕분에, 이 연결 테이블 하나로 "자체 주차장 없는 카페 → 도보 3분 공영주차장" 안내가 가능하다.

### 12.2 [3층] 개인화/점수 — 추천 고도화 시 추가

```text
user_preferences        # 회원가입 취향 조사 결과 (취향 적합도 계산의 재료)
user_exclusions         # "중식 제외를 장기 취향으로 저장" (user_id + category_id)
place_categories.score  # 목적 적합도 점수 (컬럼 추가) — 이진 포함/제외 → 순위 매기기로 승격
place_categories.reason_summary  # 분류 근거 요약 (컬럼 추가)
```

추천 점수(취향 0.30 + 목적 0.20 + 동선 0.20 + 주차 0.15 + 가성비 0.10 + 감성 0.05)에서 위치/동선은 저장하지 않고 요청 시점에 계산한다. 저장하는 것은 장소 자체의 성질뿐이다.

### 12.3 [4층] 근거/룰 — 운영 고도화 시 추가

```text
place_attribute_evidences  # 속성값의 근거 (리뷰/사진/사용자 피드백/수동 검수)
category_rules             # 분류 기준을 DB에서 관리 → 운영자가 수정 가능, Java 하드코딩 감소
```

**MVP 단계에서는 자동 분류 로직을 Java 코드에서 처리해도 된다.**

---

## 13. 최종 설계 원칙

```text
 1. places에는 장소 기본 정보만 저장한다.
 2. 세부 환경값은 place_attributes에 row 형태로 저장한다.
 3. 목적 카테고리는 categories로 관리한다.
 4. 장소와 카테고리 관계는 place_categories로 관리한다.
 5. 점수, 출처, 근거, 룰 엔진은 MVP 이후에 확장한다.
 6. 장소는 브랜드명이 아니라 지점별 실제 환경값으로 판단한다.
 7. 세분화 필드 값이 기준을 충족하면 자동으로 카테고리에 분류한다.
 8. 카테고리와 보조 필터는 중복되지 않아야 한다.
 9. 카테고리의 필수 조건은 보조 필터로 다시 묻지 않는다.
10. 보조 필터는 카테고리 안에서 더 좁히는 역할만 한다.
11. DB는 MySQL 8로 시작한다. 스키마는 표준 구조로 유지해 이사 가능성을 열어둔다.
12. 속성 키와 값은 Java enum에 정의된 것만 저장한다. 정의에 없으면 저장을 거부한다.
13. 카테고리는 목적(PURPOSE)과 종류(SUBTYPE)를 kind로 구분해 절대 섞지 않는다.
14. 속성값이 바뀌면 그 장소의 카테고리 분류를 같은 배치에서 즉시 다시 계산한다.
```

---

## 14. 테이블 요약

### MVP 기준 최종 테이블 (1층 — 지금 만든다)

| 테이블 | 역할 | 핵심 컬럼 | 핵심 제약 |
|--------|------|----------|----------|
| `places` | 장소 기본 정보 (지점 단위) | `id`, `name`, `place_type`, `latitude`, `longitude` | 위경도 인덱스 |
| `place_attributes` | 세분화 필드 값 (row 단위) | `place_id`, `attribute_key`, `attribute_value` | UNIQUE(place_id, attribute_key) |
| `categories` | 카테고리 정의 (목적/종류 구분) | `place_type`, `kind`, `code`, `name` | UNIQUE(place_type, code) |
| `place_categories` | 장소-카테고리 분류 결과 | `place_id`, `category_id`, `status` | UNIQUE(place_id, category_id) |

### 향후 확장 테이블 (2~4층 — 기능 개발 순서대로 추가)

| 층 | 대상 | 용도 | 시점 |
|----|------|------|------|
| 2층 | `courses`, `course_slots` | 슬롯 기반 코스 추천, 장소 고정, 슬롯 교체 | 코스 기능 개발 시 |
| 2층 | `place_nearby_parkings` | 주차 협소 장소에 주변 주차장 연결 | 코스 기능 개발 시 |
| 3층 | `user_preferences`, `user_exclusions` | 취향 조사 기반 개인화, 장기 제외 저장 | 추천 고도화 시 |
| 3층 | `place_categories.score`, `.reason_summary` | 목적 적합도 점수화, 분류 근거 | 추천 고도화 시 |
| 4층 | `place_attribute_evidences` | 리뷰/사진/피드백 기반 속성 근거 | 운영 고도화 시 |
| 4층 | `category_rules` | 분류 룰을 DB에서 관리 (운영자 수정 가능) | 운영 고도화 시 |
