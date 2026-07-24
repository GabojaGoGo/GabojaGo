# Beam Search Route Recommendation Review Prompt

아래 내용을 다른 AI에게 붙여 넣어, 가보자GO의 방향과 DB/알고리즘 설계가 타당한지 객관적으로 검토받기 위한 프롬프트입니다.

```text
너는 여행/장소 추천 서비스의 백엔드 아키텍처, 추천 알고리즘, DB 설계 전문가라고 가정하고 아래 내용을 객관적으로 검토해줘.

우리는 “가보자GO”라는 국내 여행/장소 루트 추천 앱을 만들고 있다.

핵심 목표는 단순히 관광지나 맛집 목록을 보여주는 것이 아니라, 사용자가 원하는 목적/상황/이동수단/동행 조건에 맞춰 실패 확률이 낮은 루트를 추천하는 것이다.

우리가 생각하는 주요 플로우는 다음과 같다.

1. 사용자가 시작 위치를 정한다.
2. 이동수단을 선택한다.
   - MVP에서는 대중교통 / 자차 2가지만 고려한다.
   - 택시 일부 허용 등은 추후 확장한다.
3. 사용자가 슬롯 순서를 직접 만들 수 있다.
   예:
   - 음식 → 음식 → 카페 → 술 → 숙소
   - 관광지 → 음식 → 카페 → 숙소
4. 각 슬롯마다 앱이 후보 장소를 추천한다.
5. 사용자는 특정 장소를 고정할 수 있다.
   예:
   - “나는 이 마라탕집은 꼭 가고 싶다”
   - 이 경우 해당 장소는 hard fixed stop으로 본다.
   - 다만 너무 멀거나 동선이 비효율적이면 프론트에서 경고를 띄운다.
6. 사용자가 특정 슬롯이 마음에 안 들면 수정할 수 있다.
   예:
   - 두 번째 음식 슬롯에서 중식집을 추천받았는데 싫다.
   - 그러면 이번 슬롯에서 CHINESE 카테고리를 제외하고 다시 추천한다.
   - 기본적으로 제외는 해당 슬롯/해당 추천 세션에만 적용하고, 사용자가 원하면 장기 취향으로 저장한다.
7. 카페 슬롯처럼 세부 취향이 중요한 경우, 사용자가 다시 조건을 줄 수 있다.
   예:
   - 분위기 좋은 카페
   - 사진 찍기 좋은 카페
   - 조용한 카페
   - 디저트 좋은 카페
   그리고 지도에서 후보를 직접 선택할 수 있게 하고 싶다.
8. 숙소는 두 경우를 모두 지원하고 싶다.
   - 이미 정한 숙소가 있다면 마지막 stop으로 fixed.
   - 숙소도 추천받고 싶다면 숙소 후보를 추천.
9. 자차 루트에서 주차가 협소한 장소라면 주변 추천 주차장을 보여주고 싶다.
   - 주차장은 매번 RouteStop으로 넣기보다는 장소 카드에 “추천 주차장”으로 붙이는 것이 좋다고 생각한다.
   - 단, 주차장에서 목적지까지 도보 이동 안내가 중요하면 나중에 RouteStop으로 승격할 수 있다.
10. 대중교통의 경우 역/정류장은 일단 RouteStop이 아니라 route segment 설명으로 다룬다.
11. 추천 결과는 단순히 루트 카드 3개만 주는 것보다 지도 기반으로 카테고리별 후보를 보여주고, 사용자가 직접 선택해 바꿀 수 있게 하고 싶다.
12. MVP 플로우는 두 가지로 보고 있다.
   - 완전 자동 추천:
     사용자가 대략적인 취향만 입력하면 앱이 슬롯과 장소를 자동 구성해서 루트를 만들어줌.
   - 슬롯별 추천:
     사용자가 슬롯 순서를 직접 만들고, 앱이 계속 질문하면서 각 슬롯별 최적 후보와 대안을 제시해 같이 코스를 완성함.

추천 알고리즘은 Beam Search 기반으로 생각하고 있다.

Beam Search의 역할은 다음과 같다.

- 사용자가 만든 slot sequence에 대해 각 슬롯별 후보 장소를 넣어본다.
- 고정된 장소는 반드시 유지한다.
- 제외된 카테고리/장소는 후보에서 제외한다.
- 각 후보 경로에 대해 점수를 계산한다.
- 상위 K개의 경로만 유지하며 다음 슬롯으로 확장한다.
- 최종적으로 좋은 루트와 슬롯별 대체 후보를 반환한다.

Beam Search 점수에 들어갈 요소로는 다음을 생각하고 있다.

- 목적 적합도
- 슬롯 적합도
- 이동시간
- 이동거리
- 이동 피로도
- 대중교통 접근성
- 자차 주차 편의성
- 장소 매력도
- 가성비
- 호캉스 적합도
- 가족/커플/친구/부모님 동행 적합도
- 비 오는 날/야간/더운 날 등 상황 적합도
- 설명 가능성
- 사용자가 고정한 장소로 인한 detour penalty
- 너무 멀 경우 warning

우리가 TourAPI/Kakao API에서 자동으로 받을 수 있는 데이터는 대략 다음이다.

- 장소 이름
- 주소
- 좌표
- 대표 이미지
- 전화번호
- 원본 ID
- 기본 카테고리 후보
- 지역 코드
- 현재 위치에서 거리
- 일부 방문량/혼잡 보조값

하지만 API가 주지 않아서 직접 큐레이션해야 하는 데이터는 다음이라고 보고 있다.

- 실제 추천 속성
  예: 조용함, 산책 좋음, 사진 좋음, 실내/실외
- 목적 적합도
  예: 데이트 적합, 가족 적합, 힐링 적합, 사진 적합
- 상황 적합도
  예: 비 오는 날 적합, 야간 적합, 더운 날 적합
- 이동수단별 체감 편의
  예: 자차 주차 편함, 대중교통 편함, 짐 들고 이동 가능
- 평균 체류 시간
- 주차 편의
- 주차장 규모
- 주차장 사진 여부
- 숙소의 가성비/호캉스/커플/가족 적합도
- 추천 이유
- 주의사항
- 검수 여부와 검수일

우리가 생각하는 DB 구조는 다음과 같다.

1. places
   - 모든 동선 지점의 공통 모델
   - 관광지, 식당, 카페, 숙소, 주차장, 역, 정류장, 터미널, 공항, 짐보관소, 관광안내소 등
   - 단, 화장실, 엘리베이터, 배리어프리 접근점 같은 건 Place가 아니라 Attribute로 본다.

주요 컬럼:
- id
- name
- normalized_name
- primary_type
- category_large
- category_medium
- category_small
- address
- latitude
- longitude
- phone
- image_url
- status
- average_stay_minutes
- price_level
- curation_status
- created_at
- updated_at

2. place_sources
   - TourAPI/Kakao/수작업 출처 기록
   - 내부 place_id와 외부 contentid/kakao id를 분리하기 위함
   - 같은 장소가 여러 API에 있을 수 있으므로 sourcePlaceId를 내부 placeId로 직접 쓰지 않는다.

주요 컬럼:
- place_id
- source_type
- source_place_id
- raw_payload
- fetched_at
- expires_at
- attribution

3. attribute_dictionary
   - 속성 코드 사전
   - QUIET, OUTDOOR, INDOOR, PHOTO_SPOT, PARKING_EASY, TRANSIT_FRIENDLY 등
   - 태그 중복을 막기 위함

4. place_attributes
   - 장소가 실제로 가진 특성
   - score/confidence/source/evidence/verified_at을 둔다.

예:
- OUTDOOR score=1.0
- WALKING score=0.8
- PHOTO_SPOT score=0.6
- PARKING_EASY score=0.3

5. place_suitabilities
   - 특정 목적/상황/동행에 대한 적합도
   - Attribute와 분리한다.
   - Attribute는 장소 자체의 특성이고, Suitability는 특정 사용자 목적에 대한 적합도다.

예:
- HEALING score=0.8
- DATE score=0.6
- FAMILY score=0.5
- HOTEL_STAY score=0.9
- BUDGET score=0.7

6. regions
   - 지역 추천, 지역별 수집, 지역별 큐레이션을 위해 필요

7. recommendation_requests
   - 사용자가 이번 추천에서 입력한 조건의 스냅샷
   - 시작 위치, 이동수단, 슬롯 순서, 고정 장소, 제외 조건, 취향, 예산, 기간 등을 JSON으로 저장

8. routes / route_stops / route_segments
   - 추천 결과를 매번 저장하지 않고 DTO로 반환
   - 사용자가 저장할 때만 persist
   - route_stop은 루트 안의 장소
   - route_segment는 장소와 장소 사이의 이동 정보
     예: A→B 이동시간, 거리, 대중교통 환승 수, 도보 시간, 자차 이동시간 등

9. place_travel_cache 또는 Redis 이동 캐시
   - Beam Search 중 같은 A→B 이동시간을 반복 계산하지 않도록 캐시
   - 저장된 route_segment와는 분리
   - route_segment는 저장된 루트의 이동 스냅샷이고, travel_cache는 알고리즘 계산용 TTL 캐시

10. place_relations
   - MVP에서는 미루거나 최소화
   - 나중에 관광지→추천 주차장, 숙소→가까운 역, 관광지→짐보관소 같은 관계를 저장할 수 있음
   - MVP에서는 대부분 좌표 기반 온디맨드 검색으로 처리하고, 중요한 큐레이션 관계만 저장

우리는 MVP에서 처음부터 모든 데이터를 다 채우지 않고, 검증 지역 5~6개를 정한 뒤 지역별 상위 50~80개 장소만 수작업 검수하려고 한다.

MVP 우선순위는 다음과 같다.

1. places
2. place_sources
3. attribute_dictionary
4. place_attributes
5. place_suitabilities
6. regions
7. recommendation_requests
8. 저장용 routes / route_stops / route_segments
9. 이동 캐시
10. 주차장/교통 접근성 최소 지원

질문:
1. 이 서비스 방향이 추천 앱/여행 루트 앱으로 타당한가?
2. Beam Search가 이 문제에 적합한가?
3. 우리가 정의한 slot 기반 추천 구조가 적절한가?
4. fixed stop, excluded category, preferred attribute 개념이 충분한가?
5. DB 설계에서 과한 부분과 부족한 부분은 무엇인가?
6. places를 모든 동선 지점의 공통 모델로 보는 게 맞는가?
7. 주차장/역/정류장/짐보관소/관광안내소를 Place로 보는 게 맞는가?
8. 화장실/엘리베이터/배리어프리 접근점을 Attribute로 보는 게 맞는가?
9. place_attributes와 place_suitabilities를 나누는 게 맞는가?
10. recommendation_requests를 저장하는 것이 필요한가?
11. route_segment와 travel_cache를 분리하는 게 맞는가?
12. MVP에서 줄여야 할 테이블이나 미뤄야 할 기능은 무엇인가?
13. 수작업 큐레이션해야 하는 데이터 범위가 현실적인가?
14. 주차장/대중교통/숙소 적합도를 어떤 방식으로 점수화하는 게 좋은가?
15. 나중에 회식/해장/데이트/외국인 관광객 추천까지 확장하려면 지금 어떤 설계만 열어두면 되는가?
16. 이 설계에서 가장 위험한 가정이나 놓친 핵심은 무엇인가?
17. 더 단순하고 실용적인 MVP 구조가 있다면 제안해줘.

객관적으로 장점, 단점, 위험 요소, 개선안을 구체적으로 말해줘.
특히 “지금 당장 만들 것”과 “나중에 확장할 것”을 분리해서 조언해줘.
```
