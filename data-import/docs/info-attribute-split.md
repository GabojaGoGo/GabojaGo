# PlaceInfo와 PlaceAttribute 분리

이 문서는 **Info는 원천 사실, Attribute는 판단 결과**라는 data-import 영역의 경계를 설명한다.
프로젝트 전체 실행 순서·현재 단계·구현 착수 권한은 `docs/L0-project-context.md` 6절이 소유한다.
아래 TourAPI 사례와 초기 저장 모델은 경계 설명을 위한 예시이며, 현행 다중 원천 계약이나 구현 스키마의 단일 원본이 아니다.

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

## 3. 초기 단일 원천 `PlaceInfo` 모델의 예시

`PlaceInfo`는 원천이 알려준 사실을 원문에 가까운 형태로 보존하고, 해석하거나 판단한 결과는 `PlaceAttribute`에 저장한다. 아래 제약은 TourAPI 단일 원천으로 시작했을 때의 예시이며, 실제 구현 계약은 P2-C에서 확정한다.

| 항목 | 결정 |
| --- | --- |
| 키 | `InfoKey` enum |
| 값 | `TEXT` |
| 검색 | 값 자체를 검색 조건으로 사용하지 않음 |
| 원천 | TourAPI 단일 원천으로 시작한 예시. 실제 원천 선택·병합 계약은 L0와 `other-source-survey.md`를 따른다 |

유니크 제약은 다음과 같다.

```text
(place_id, info_key)
```

같은 장소의 같은 정보를 다시 수집해도 행을 추가하지 않고 기존 값을 갱신한다. 빈 값이나 형식이 명백히 잘못된 값은 저장하지 않는다.

원천별 값 선택·병합은 이 문서가 정하지 않는다. 실제 원천 선택·병합 계약은 L0와 `other-source-survey.md`를 따른다.

## 4. InfoKey 선정 판단 예시

`InfoKey`는 서비스에서 필요한 원천 사실을 보존하기 위한 허용 어휘다. 아래 TourAPI 기준은 초기 판단 예시이며, 실제 적용 범위와 원문 보존 계약은 P2-C에서 확정한다. 채움률은 실제 수집 후의 참고 지표일 뿐, 키를 만들거나 삭제하는 단독 기준이 아니다.

초기에는 TourAPI에서 충분히 얻을 수 있는 정보를 예시로 삼았다.

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

## 8. 무료 원천 제약의 data-import 적용

L0의 예산 제약에 따라 data-import에서는 무료 원천만 검토한다.

| 원천 | 상태 |
| --- | --- |
| 한국관광공사 TourAPI | 사용 중 |
| 행정안전부 인허가데이터 | 후보 |
| 소상공인시장진흥공단 상가정보 | 후보 |

후보 원천은 비용뿐 아니라 저장·캐싱 가능 여부도 확인한 후 사용한다. 현재 우선 원천과 실행 단계는 이 문서가 아니라 L0와 Jira를 따른다.

## 9. 역할 경계 예시

이 절은 역할 경계의 예시다. 실제 구현 시점과 데이터 계약은 L0의 단계 게이트 및 해당 data-import 매핑 계약을 먼저 확인한다.

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
