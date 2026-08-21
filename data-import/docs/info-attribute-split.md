# PlaceInfo와 PlaceAttribute 분리

## 1. 문제

현재 데이터 저장 구조는 다음과 같다.

| 저장소 | 역할 | 예시 |
| --- | --- | --- |
| `Place` | 장소 기본정보 | 이름, 주소, 좌표 |
| `PlaceAttribute` | 우리가 판단한 특성 | 주차 편의성, 영업시간 수준 |

하지만 `detailIntro2`에는 영업시간, 휴무일, 메뉴, 입장료처럼 **원본 그대로 보존해야 하는 정보**가 많다. 이 정보를 바로 `PlaceAttribute`로 변환하면 원본이 사라진다.

따라서 원본 정보와 우리가 판단한 특성을 분리한다.

## 2. 데이터 구조

```text
원천 데이터
  ↓
PlaceInfo        원천이 알려준 사실
  ↓
PlaceAttribute   원천 사실을 해석한 특성
  ↓
PlacePurpose     여러 특성을 조합한 목적
```

| 구분 | 역할 | 예시 |
| --- | --- | --- |
| `Place` | 장소 기본정보 | 이름, 주소, 좌표 |
| `PlaceInfo` | 원천이 알려준 사실 보존 | 영업시간, 휴무일, 메뉴 |
| `PlaceAttribute` | 원천 사실을 해석·판단한 값 | 주차 편의성 |
| `PlacePurpose` | 여러 `PlaceAttribute`를 조합한 목적 | 데이트, 작업 |

> **핵심:** `PlaceInfo`는 원본, `PlaceAttribute`는 판단 결과다.

## 3. PlaceInfo 설계

`PlaceInfo`는 원천이 알려준 사실을 원문에 가까운 형태로 보존한다. 해석하거나 판단한 결과는 `PlaceAttribute`에 저장한다.

| 항목 | 결정 |
| --- | --- |
| 키 | `InfoKey` enum |
| 값 | `TEXT` |
| 검색 | 값 자체를 검색 조건으로 사용하지 않음 |
| 원천 | 현재는 TourAPI 단일 원천만 사용 |

유니크 제약은 다음과 같다.

```text
(place_id, info_key)
```

같은 장소의 같은 정보를 다시 수집해도 행을 추가하지 않고 기존 값을 갱신한다. 빈 값이나 형식이 명백히 잘못된 값은 저장하지 않는다.

현재는 TourAPI만 사용하므로 원천별로 충돌하는 값을 함께 쌓지 않는다. 다른 무료 원천을 실제로 도입할 때, 원천 우선순위·최근 수정일·관리자 검토 중 어떤 기준으로 값을 병합할지 먼저 결정한다.

## 4. InfoKey 선정 기준

`InfoKey`는 서비스에서 필요한 정보이고, 현재 TourAPI에서 수집할 수 있다면 정의한다. 채움률은 실제 수집 후의 참고 지표일 뿐, 지금 키를 만들거나 삭제하는 기준으로 쓰지 않는다.

현재는 TourAPI에서 충분히 얻을 수 있는 정보부터 사용한다.

### 1차 InfoKey — 22개

| 분류 | InfoKey | 개수 |
| --- | --- | ---: |
| 공통 | `OPERATING_HOURS`<br>`CLOSED_DAYS`<br>`PARKING_INFO`<br>`CREDIT_CARD` | 4 |
| 음식점 · 카페 · 술집 | `SIGNATURE_MENU`<br>`MENU_LIST`<br>`TAKEOUT_AVAILABLE` | 3 |
| 숙소 | `CHECK_IN_TIME`<br>`CHECK_OUT_TIME`<br>`ROOM_TYPE`<br>`ROOM_COUNT`<br>`COOKING_ALLOWED` | 5 |
| 상점 | `SALE_ITEM`<br>`RESTROOM`<br>`MARKET_DAY` | 3 |
| 관광지 · 축제 | `EVENT_START_DATE`<br>`EVENT_END_DATE`<br>`EVENT_PLACE`<br>`PLAY_TIME`<br>`ORGANIZER`<br>`ADMISSION_FEE`<br>`PROGRAM` | 7 |
| **합계** |  | **22** |

## 5. 실제 데이터 분포 확인

채움률은 수집한 장소 중 특정 정보에 값이 들어 있는 비율이다. 예를 들어 100개 숙소 중 5개에만 사우나 정보가 있으면 해당 정보의 채움률은 5%다.

| 채움률이 알려 주는 것 | 채움률만으로 알 수 없는 것 |
| --- | --- |
| TourAPI가 해당 정보를 얼마나 자주 제공하는지 | 값이 없는 장소에 해당 시설·서비스가 실제로 없는지 |

따라서 50% 같은 고정 기준은 두지 않는다. `detailIntro2` 수집과 `PlaceInfo` 저장이 끝난 뒤, 5번 단계에서 실제 분포를 확인해 이후 규칙의 근거로만 사용한다.

## 6. 숙소 부대시설은 보류

`sauna`, `karaoke`, `fitness` 등의 실제 제공 여부와 채움률은 아직 확인하지 않았다. 따라서 현재 1차 `InfoKey` 22개에는 개별 부대시설 키와 `SUBFACILITY`를 넣지 않는다.

`detailIntro2` 수집 뒤 원천에 부대시설 원문이 실제로 있고 보존할 필요가 확인되면, 개별 키 대신 `SUBFACILITY` 하나를 추가할지 결정한다. 이 경우 1차 22개와는 별도의 추가 결정이다.

## 7. lcnsno 보존

`lcnsno`는 사용자에게 보여주는 정보는 아니지만, 향후 행정안전부 인허가데이터와 연결할 수 있는 키다. 따라서 원천값을 보존한다.

## 8. 무료 원천 원칙

현재 예산이 없으므로 무료 원천만 사용한다.

| 원천 | 상태 |
| --- | --- |
| 한국관광공사 TourAPI | 사용 중 |
| 행정안전부 인허가데이터 | 후보 |
| 소상공인시장진흥공단 상가정보 | 후보 |

후보 원천은 비용뿐 아니라 저장·캐싱 가능 여부도 확인한 후 사용한다. 현재는 TourAPI 작업에 집중한다.

## 9. 전체 작업 순서

순서를 바꾸지 않는다.

1. 설계 확정
2. `InfoKey` + `PlaceInfo` 작성
3. `detailIntro2` 수집
4. `PlaceInfo` 저장
5. 실제 데이터 분포 확인
6. Info → Attribute 규칙 결정
7. Purpose 규칙 설계

> **현재 작업 범위:** 2번 `InfoKey + PlaceInfo 작성`만 진행한다.
>
> 3번 이후의 `detailIntro2` 수집, `PlaceInfo` 저장, 실제 데이터 분포 확인, Info → Attribute 규칙 결정, Purpose 규칙 설계는 지금 진행하지 않는다. 다른 무료 원천 조사도 이후 단계에서 진행한다.

## 10. Java와 Python의 역할 분담

Java는 서비스에서 허용하는 표준 이름과 도메인 규칙을 정의하고, Python은 외부 원천 데이터를 그 표준에 맞춰 검증·매핑·적재한다.

| 책임 | 담당 |
| --- | --- |
| 허용되는 `InfoKey` 정의 | Java `InfoKey` enum |
| `PlaceInfo` DB 구조와 JPA 매핑 | Java `PlaceInfo` entity |
| TourAPI 및 다른 외부 원천 수집 | Python |
| 원본 응답 보존 | Python |
| 원천 필드를 `InfoKey`로 매핑 | Python |
| 빈 값·형식 오류 등 원천값 검증 | Python |
| 여러 원천 중 저장할 값 선택 | Python |
| `(place_id, info_key)` 기준 `PlaceInfo` upsert | Python |
| Info → Attribute 변환 규칙 | Java |
| `PlaceAttribute` 변경 후 관련 Category 재계산 | Java |

전체 흐름은 다음과 같다.

```text
외부 원천
  ↓
Python 수집·원본 보존
  ↓
Python 검증·정규화·InfoKey 매핑
  ↓
Python PlaceInfo upsert
  ↓
Java Info → Attribute 변환
  ↓
Java Category 재계산
```

`InfoKey`의 정의 원본은 Java enum이다. Python은 Java에 없는 키를 임의로 만들지 않으며, 매핑 대상이 허용된 `InfoKey`에 없으면 적재를 중단한다.
