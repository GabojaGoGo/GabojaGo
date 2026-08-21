# `PlaceSubtypeCode` 기준 장소 원천 적재 계획

서비스 화면의 출시 순서가 아니라, Java
`PlaceSubtypeCode`에 정의된 장소를 운영할 수 있도록 **원천을 먼저 전부 적재하고
정제한다.** TourAPI는 여러 원천 중 하나이며, 장소와 `PlaceInfo`의 기본 원천으로
고정하지 않는다. 필드별로 가장 신뢰도 높은 원천을 선택한다.

- 기준일: 2026-08-09
- 적재 순서: 원본 API/CSV → 원천별 staging → `Place` 후보 매칭 → `PlaceInfo`
  원문 저장 → 검수 → `PlaceAttribute` 파생
- `Info`는 원천 사실이고, `Attribute`는 정제·판단 결과다.
- 원천의 필드가 존재해도 자동으로 `PlaceInfo`에 넣지 않는다. Java `InfoKey`에
  매핑을 정의한 필드만 넣고, 그 전에는 staging 원본으로 보존한다.

## 1. TourAPI의 역할 — 전체 기준선이 아니라 관광 콘텐츠 원천

TourAPI는 `ACCOMMODATION`, `ACTIVITY`, `CAFE`, `RESTAURANT`, `SHOP`,
`TOURIST_SPOT`의 넓은 subtype 범위를 제공한다. 특히 `ACTIVITY`와 관광지의
설명·이미지·반복 정보는 현재 다른 도입 원천으로 대체하지 않는다. 그러나
인허가 상태, 업종 분류, 시장·주차장·축제의 행정 사실은 해당 공공 원천을 먼저
쓴다.

| subtype 군 | TourAPI에서 먼저 보존할 Info |
| --- | --- |
| 숙소 | 체크인/아웃, 객실 유형·수, 취사 가능 여부, 주차 |
| 카페·음식점·술집 | 영업시간, 휴무일, 대표·전체 메뉴, 포장, 주차 |
| 상점·시장 | 영업시간, 휴무일, 판매품, 화장실, 주차 |
| 관광지·축제·공연 | 행사 시작·종료일, 장소, 주최, 관람시간, 입장료, 프로그램 |
| 액티비티 | 영업시간, 주차, 이용요금·예약 관련 원문 |

TourAPI에서 시작할 필요는 없다. 모든 원천을 독립적으로 staging에 쌓고, 이름·
정규화 주소·좌표로 같은 장소를 연결한다. TourAPI `contentid`는 연결된 장소의
추가 원천 키로만 보관한다.

### 사실별 원천 선택

원천 전체의 우열을 매기지 않는다. 사실마다 따로 정한다. 한 원천이 모든 사실을 잘
주지는 않는다.

판단 기준은 네 가지다.

| 기준 | 묻는 것 |
| --- | --- |
| 권위성 | 그 사실을 공식적으로 관리하는 원천인가 |
| 완전성 | 부울경에서 필요한 필드가 실제로 얼마나 채워지는가 |
| 연결성 | 안정적인 원본 ID·주소·좌표로 기존 장소와 연결되는가 |
| 최신성 | 변경·폐업·삭제를 신뢰성 있게 추적할 수 있는가 |

네 기준을 합쳐 종합점수를 만들지 않는다. 사실마다 지배하는 기준이 다르다.
**해당 사실의 핵심 기준을 충족하지 못한 원천은 다른 장점이 있어도 그 사실의 원천으로
채택하지 않는다.** 관광 설명이 풍부해도 폐업 추적이 부정확하면 영업 상태 원천으로는
탈락이다.

좌표는 값 자체가 틀릴 수 있으므로 정확성을 따로 검증한다. 채워져 있고 매칭이 되어도
수백 미터 어긋날 수 있다. 정확성은 정답을 알 수 있는 필드에만 적용하는 보조 기준이다.

아래는 현재 후보와 채택 상태다. **실측 전 원천은 채택이 아니다.** 측정값은 Jira에
남기고 이 표에는 결론만 반영한다.

| 사실 | 핵심 기준 | 우선 후보 | 보조 후보 | 상태 | 근거 |
| --- | --- | --- | --- | --- | --- |
| 인허가·영업 상태·인허가일 | 권위성·최신성 | 행정안전부 식품·문화 인허가 | 없음 | **부분 실측** — 권위성·완전성·연결성 확인, 음식점·카페·술집·숙소의 base로 잠정 사용. **핵심 기준인 최신성 미검증이므로 채택 아님** | `GO-120` `GO-123` |
| 상권업종 세분류·층/호 | 완전성·연결성 | 소상공인시장진흥공단 상가정보 | 없음 | **부분 실측** — 부울경 387,313건, 주소·좌표 100%, WGS84. SHOP base로 잠정 사용. **상가업소번호 안정성·중복률 미측정** | `GO-120` |
| 주차장 운영시간·요금·구획수 | 완전성·최신성 | 전국주차장정보표준데이터 | 없음 | **실측 완료** — 부울경 2,529건, 운영시간·요금 채움률 100%, WGS84 | `GO-79` |
| 시장 개설주기·시장 편의 정보 | 권위성·완전성 | 전국전통시장표준데이터 | TourAPI 쇼핑 정보 | 미실측 — TourAPI에 833건 있어 보강 원천으로 후순위 | `GO-80` |
| 축제 기간·개최장소·주최 | 권위성·최신성 | 전국문화축제표준데이터 | TourAPI 행사 정보 | 미실측 — TourAPI에 925건 있어 보강 원천으로 후순위 | `GO-81` |
| 관광지·액티비티 설명·반복 정보 | 권위성·완전성 | TourAPI | 없음 | 미실측 — 상세 조회 미수집 | `GO-66` `GO-73` |
| 대표 이미지 URL | 완전성 | TourAPI | 없음 | 미실측 — 목록 응답에 `firstimage` 존재만 확인 | `GO-73` |
| 좌표 | 연결성·정확성 | 미정 | 미정 | **부분 실측** — 주차장 WGS84, 식품 인허가 EPSG:5174 계열. 상가정보 미확인 | `GO-79` `GO-120` `GO-94` |

`상태` 칸에는 결론과 함께 **무엇을 어디까지 쟀는지**를 적는다. 범위 없이 결론만 적으면
측정한 것보다 넓게 단정한 것을 알아챌 수 없다. 자세한 규칙은
[`L-1-operating-principles.md`](../../docs/L-1-operating-principles.md) 6절에 있다.

**`base로 잠정 사용`과 `채택`은 다르다.** base는 어느 원천으로 `Place`를 만들지 정한 것이고,
채택은 그 사실의 정본으로 확정한 것이다. base는 다른 원천이 더 나은 것으로 확인되면 바꿀 수 있다.
채택은 네 기준을 모두 통과해야 하며, 특히 **해당 사실의 핵심 기준을 재지 않은 원천은 채택하지 않는다.**

`미실측`은 아직 재보지 않았다는 뜻이고, 원천이 그 사실을 아예 제공하지 않는 경우는
`해당 없음`으로 적는다. 둘은 다르다. 미실측은 언젠가 재야 하고 해당 없음은 재지 않는다.

최신성은 한 번 수집해서는 잴 수 없다. 표본 단계에서는 갱신 필드(기준일·갱신시점)의
존재 여부까지만 확인하고, 실제 변경 감지율은 재수집 이후에 채운다.

### PlaceType별 후보 원천 관리

모집단 후보는 **PlaceType별로** 관리한다. 현재 채택 원천을 실측·적재하는 동안 다른
공식 CSV·API 후보를 조사해 대기열에 넣고, 원본을 확보하면 같은 부울경 범위와 기준일로
비교한다. 후보가 더 좋은 근거를 내면 현재 채택 원천도 바꿀 수 있다.

다만 최종 병합에서는 원천 전체를 한꺼번에 우열화하지 않는다. PlaceType 후보를 실측한
뒤, 실제로 채택할 값은 위의 **사실별 원천 선택** 원칙으로 결정한다. 따라서 어떤 후보는
모집단 전체를 대체하고, 어떤 후보는 빠진 subtype이나 운영 정보만 보강할 수 있다.

| 상태 | 의미 |
| --- | --- |
| 탐색 | 구체적인 원천을 더 찾아야 함 |
| 문서 확인 | 운영기관·제공 필드·접근 및 이용조건만 확인함. 품질은 미검증 |
| 원본 확보 대기 | 실측할 API·CSV가 정해졌지만 아직 원본을 받거나 호출하지 않음 |
| 실측 중 | 실제 원본으로 건수·채움률·연결률 등을 측정 중 |
| 채택 | 핵심 기준을 통과해 현재 우선 원천으로 사용함 |
| 보조 채택 | 우선 원천의 값이 없거나 유효하지 않을 때만 사용함 |
| 탈락 | 해당 사실의 핵심 기준을 통과하지 못함. 원천 전체의 탈락은 아님 |

인터넷·포털 조사만으로 올릴 수 있는 최고 상태는 `문서 확인`이다. `채택`과
`보조 채택`은 실제 원본 실측 근거가 있을 때만 사용한다.

#### 현재 모집단 기준선

| PlaceType | 현재 모집단 원천 | 부울경 |
| --- | --- | ---: |
| `RESTAURANT` | 일반음식점 인허가 | 106,186 |
| `BAR` | 일반음식점 주점 업태 | 13,229 |
| `CAFE` | 휴게음식점 + 제과점 | 16,820 |
| `SHOP` | 상가정보 | 93,775 |
| `ACCOMMODATION` | 숙박업 + 관광숙박업 | 6,385 |
| `TOURIST_SPOT` · `ACTIVITY` | TourAPI | — |
| `PARKING_LOT` | 주차장 표준데이터 | 2,529 |

#### 후보 목록

`기존`은 공공데이터포털·LOCALDATA 중심으로 먼저 찾은 후보, `추가`는 기관 자체 원천·
부울경 지역 포털·전문 분야 API까지 조사 범위를 넓혀 찾은 후보다. 후보는 역할에 따라
**모집단 대체·확장**과 **정보 보강**으로 나눈다.

##### A. 모집단 대체·subtype 확장 후보

| PlaceType | 조사 | 후보 원천 | 방식 | 기대 역할 | 상태·다음 확인 |
| --- | --- | --- | --- | --- | --- |
| `RESTAURANT`, `CAFE` | 기존 | [식약처 식품접객업정보](https://www.data.go.kr/data/15064859/openapi.do) | API | 현재 식품 인허가 모집단 대체 가능성 | 문서 확인 · 활성 건수·ID·폐업 상태·주소 비교 |
| `BAR` | 기존 | [LOCALDATA 단란주점영업](https://www.data.go.kr/data/15045017/fileData.do) | CSV + [API](https://www.data.go.kr/data/15154883/openapi.do) | 법정 주점 subtype 확장 | 문서 확인 · 현재 주점 업태와 중복·신규 비율 |
| `BAR` | 기존 | [LOCALDATA 유흥주점영업](https://www.data.go.kr/data/15045018/fileData.do) | CSV + [API](https://www.data.go.kr/data/15154890/openapi.do) | 법정 주점 subtype 확장 | 문서 확인 · 중복·신규 비율과 서비스 노출 적합성 |
| `SHOP` | 기존 | [전국전통시장표준데이터](https://www.data.go.kr/data/15012894/standard.do) | CSV + API | 시장 subtype 확장 | 원본 확보 대기 · 상가정보 연결률·신규 시장 측정 (`GO-80`) |
| `ACCOMMODATION` | 기존 | [LOCALDATA 관광펜션업](https://www.data.go.kr/data/15044965/fileData.do) | CSV + [API](https://www.data.go.kr/data/15155103/openapi.do) | 관광펜션 subtype 확장 | 문서 확인 · 현재 6,385건과 중복·신규 비율 |
| `ACCOMMODATION` | 기존 | [LOCALDATA 외국인관광도시민박업](https://www.data.go.kr/data/15044966/fileData.do) | CSV + [API](https://www.data.go.kr/data/15155139/openapi.do) | 도시민박 subtype 확장 | 문서 확인 · 중복·신규·종료 상태 측정 |
| `ACCOMMODATION` | 기존 | [LOCALDATA 한옥체험업](https://www.data.go.kr/data/15045085/fileData.do) | CSV | 한옥체험 subtype 확장 | 문서 확인 · API URL과 부울경 활성 건수 확인 |
| `TOURIST_SPOT` | 기존 | [전국관광지정보표준데이터](https://www.data.go.kr/data/15021141/standard.do) | CSV + API | 지정 관광지 모집단 후보 | 문서 확인 · TourAPI 중복·신규·포함 범위 측정 |
| `TOURIST_SPOT` | 기존 | [전국도시공원정보표준데이터](https://www.data.go.kr/data/15012890/standard.do) | CSV + API | 공원 subtype 확장 | 문서 확인 · 부울경 건수·TourAPI 중복 측정 |
| `ACTIVITY` | 기존 | LOCALDATA 일반·자동차야영장업 | [CSV](https://www.data.go.kr/data/15045088/fileData.do) + [일반 API](https://www.data.go.kr/data/15155149/openapi.do) + [자동차 API](https://www.data.go.kr/data/15154788/openapi.do) | 캠핑 subtype 확장 | 문서 확인 · 활성 건수·중복·명시적 폐업 상태 측정 |
| `ACTIVITY` | 기존 | [전국기타유원시설업소표준데이터](https://www.data.go.kr/data/15124679/standard.do) | CSV + API | 놀이·키즈시설 subtype 확장 | 문서 확인 · 활성 건수·TourAPI 중복 측정 |
| `ACTIVITY` | 기존 | LOCALDATA 체육시설업 묶음 | 업종별 CSV·API 탐색 | 수영장·승마장·체력단련장 확장 | 탐색 · 현재 subtype에 직접 맞는 업종만 선별 |
| `ACCOMMODATION`, `ACTIVITY` | 추가 | [전국휴양림표준데이터](https://www.data.go.kr/data/15013111/standard.do) | CSV | 자연휴양림 subtype 확장 | 문서 확인 · 부울경 건수·현재 모집단과 중복 측정 |
| `PARKING_LOT` | 기존 | 한국교통안전공단 주차정보 | [CSV](https://www.data.go.kr/data/15135783/fileData.do) + [API](https://www.data.go.kr/data/15099883/openapi.do) | 기준선 대체·실시간 확장 가능성 | 문서 확인 · 2,529건과 ID 연결률·부울경 커버리지 측정 |

##### B. 기존 장소의 정보 보강 후보

| PlaceType | 조사 | 후보 원천 | 방식 | 보강 정보 | 상태·다음 확인 |
| --- | --- | --- | --- | --- | --- |
| `RESTAURANT`, `CAFE`, `BAR` | 기존 | 소상공인 상가정보 | [ZIP](https://www.data.go.kr/data/15083033/fileData.do) + [API](https://www.data.go.kr/data/15012005/openapi.do) | 업종·주소·좌표·층/호 | 부분 실측 · 장소 연결률 측정 |
| `RESTAURANT`, `CAFE` | 추가 | [울산문화관광재단 음식관광 DB](https://ulsan.openapi.redtable.global/) | API | 메뉴·매장·운영·품질정보 | 문서 확인 · 대상 수·연결률·채움률·갱신주기 측정 |
| `RESTAURANT`, `CAFE` | 추가 | [경남관광재단 우수음식점 DB](https://gyeongnam.openapi.redtable.global/) | API | 메뉴·이미지·운영·품질정보 | 문서 확인 · 대상 수·연결률·채움률 측정 |
| `RESTAURANT`, `CAFE`, `BAR`, `SHOP`, `ACCOMMODATION` | 기존 | [TourAPI](https://www.data.go.kr/data/15101578/openapi.do) | API | 설명·영업시간·이미지·편의정보 | 실측 중 · 현재 모집단 연결률·상세 채움률 측정 (`GO-73`) |
| `ACCOMMODATION` | 추가 | [울산 민박펜션·굿스테이](https://www.ulsan.go.kr/u/metro/contents.ulsan?mId=001007004002004000) | 지역 API | 객실수·좌표·이미지 | 문서 확인 · endpoint 생존 여부와 연결률 측정 |
| `TOURIST_SPOT`, `ACTIVITY` | 추가 | [한국문화정보원 문화포털](https://www.culture.go.kr/portal/main/contents.do?menuNo=200155) | API | 문화시설·공연·전시 | 문서 확인 · TourAPI 중복·신규·행사 갱신 추적 |
| `ACTIVITY` | 추가 | [공연예술통합전산망 KOPIS](https://kopis.or.kr/por/cs/openapi/openApiInfo.do) | API | 공연장·공연·축제·기간·가격 | 문서 확인 · 부울경 건수·중복·출처표시 조건 확인 |
| `ACTIVITY` | 기존 | [전국 야영장 등록 현황](https://www.data.go.kr/data/15037499/fileData.do) | CSV + API | 캠핑 편의·안전시설 | 문서 확인 · LOCALDATA·TourAPI 연결률과 채움률 측정 |
| `TOURIST_SPOT`, `ACTIVITY` | 추가 | [산림청 등산로정보](https://www.data.go.kr/data/15002734/openapi.do) | API | 난이도·거리·소요시간 | 문서 확인 · WALKING_INTENSITY·PHYSICAL_INTENSITY·EXPERIENCE_DURATION·VISIT_DURATION 4개 AttributeKey가 걸려 있다. 응답 필드·난이도 값 체계·장소 연결 가능 여부 측정 |
| `TOURIST_SPOT` | 추가 | [부산광역시_부산명소정보](https://www.data.go.kr/data/15063481/openapi.do) | API | 운영시간·이용요금 | 문서 확인 · TourAPI 관광지는 `usefee` 필드가 응답에 없었다(실측 0%, GO-66). ENTRANCE_FEE_LEVEL·PRICE_LEVEL이 걸려 있다. 부산 한정 |
| `RESTAURANT`, `CAFE` | 추가 | [부산광역시_부산맛집정보](https://www.data.go.kr/data/15063472/openapi.do) | API | 이름·주소·홈페이지·상세정보 | 문서 확인 · 부산시 선별 데이터라 전량 아님. 인허가에 없는 큐레이션 정보. 부산 한정 |
| 전체 | 추가 | [한국관광공사 반려동물 동반여행](https://korean.visitkorea.or.kr/other/otherService.do?otdid=22dd6a3f-9d71-42f8-9163-295a7ac62fb8) | TourAPI 계열 | 반려동물 동반 가능 정보 | 문서 확인 · TourAPI 상세의 `chkpet`은 관광지 표본에서 1.5%였다(GO-66). 전용 서비스의 커버리지 비교 |
| 전체 | 추가 | [부산 Big-데이터웨이브](https://data.busan.go.kr/bdip/opendata/dataSet.do) · [경남 빅데이터허브](https://bigdata.gyeongnam.go.kr/index.gn?menuCd=DOM_000000114001002000) | 지역 파일·API | 부산·경남 기관별 누락 정보 | 탐색 · PlaceType·subtype별 구체 원천으로 분리 |
| 전체 | 추가 | [울산광역시 데이터포털](https://data.ulsan.go.kr/) | 지역 파일·API | 울산 기관별 누락 정보 | 탐색 · 부산·경남만 조사돼 있었다. 목록 확인 전 |

후보는 `문서 확인 → 원본 확보 대기 → 실측 중` 순으로 올린다. 비교가 끝났을 때만
현재 모집단 기준선과 사실별 원천 선택 표에 결론을 반영한다.

#### 민간·계약형 후보

민간 원천은 공개 API가 있다는 이유만으로 적재 후보가 아니다. 최신성·완전성이 좋아도
장기 저장, 재배포, 지도 결합, 상업 이용이 허용되는지 먼저 확인한다.

| 원천 | 기대 가치 | 현재 판단 | 다시 볼 조건 |
| --- | --- | --- | --- |
| Kakao Local | 상호·카테고리·주소·좌표의 최신 검색 결과 | **대조 후보** — 쿼터·과금·저장 및 재사용 조건 검토 전 적재하지 않음 | 공식 이용조건 확인과 비용 산정 |
| Google Places | 장소 상세·영업시간·사진·리뷰 | **적재 제외** — 정책상 허용된 예외 외 사전 수집·캐시·저장이 제한됨 | 별도 계약으로 장기 저장 권한을 확보한 경우 |
| 네이버 플레이스 | 국내 장소·메뉴·영업정보 | **탐색 보류** — 장소 모집단을 내려받는 공개 API를 확인하지 못함 | 공식 API 또는 데이터 제휴 계약 확인 |
| Expedia Rapid 등 숙박 공급 API | 숙소 상세·객실·요금·예약 가능 여부 | **LATER** — 파트너 심사·계약형이며 현재 공개 기준선 용도가 아님 | 예약·정산 단계에서 파트너 승인 후 |

민간 데이터는 인허가의 영업 상태를 덮어쓰는 정본으로 쓰지 않는다. 허용 범위가 확인되면
최신 상호·영업시간·메뉴·이미지를 검증하거나 실시간 조회하는 보조 원천으로 평가한다.

같은 사실이 여러 원천에 있으면 채택된 원천을 `PlaceInfo`에 반영하고, 나머지는
staging 원본과 갱신시점으로 보존한다. 원천의 값이 더 최신이거나 더 구체적이라는
근거 없이 자동 덮어쓰지 않는다.

## 2. 현재 기준선 수집 운영

후보 목록은 1절에서만 관리한다. 이 절에는 현재 기준선으로 사용 중인 원천의 수집 방식만
남긴다. `PARKING_LOT`은 `PlaceSubtypeCode`가 아니라 독립 `PlaceType`으로 운영한다.

### API 호출 규칙

| 원천 | 호출·증분 기준 | 원본 식별자 |
| --- | --- | --- |
| 일반음식점 | `https://apis.data.go.kr/1741000/general_restaurants/info`; 초기에는 `cond[SALS_STTS_CD::EQ]`, 이후 `cond[DAT_UPDT_PNT::GTE/LT]` | `MNG_NO` |
| 숙박업 | `https://apis.data.go.kr/1741000/lodgings/info`; 영업 상태와 `DAT_UPDT_PNT`로 초기/증분 적재 | `MNG_NO` |
| 관광숙박업 | `https://apis.data.go.kr/1741000/tourist_accommodations/info`; 영업 상태와 `DAT_UPDT_PNT`로 초기/증분 적재 | `MNG_NO` |
| 상가정보 | `https://apis.data.go.kr/B553077/api/open/sdsc2`의 지역·업종·수정일 목록 오퍼레이션을 페이지네이션 | `bizesId` |

행정안전부 세 API는 모두 `serviceKey`, `pageNo`, `numOfRows`(최대 100)가
필수다. `CRD_INFO_X/Y`는 WGS84가 아니라 EPSG:5174이므로, 변환 검증 전에는
지도 좌표로 사용하지 않는다.

### CSV 다운로드 규칙

1. 각 데이터셋 페이지에서 원본 CSV를 내려받는다.
2. 원본 파일은 `data-import`의 입력 보관 위치에 파일명·데이터기준일자·다운로드
   시각과 함께 보존한다.
3. 주차장은 `주차장관리번호`, 시장은 제공되는 이름·주소·좌표, 축제는 이름·기간·
   개최장소를 staging의 중복 판정 재료로 쓴다.
4. CSV를 DB에 바로 덮어쓰지 않는다. 새 파일의 레코드를 staging에서 비교하고
   장소 매칭·값 정제 결과만 본 테이블에 반영한다.

## 3. subtype별 반영 원칙

위 대기열에서 원본 실측을 통과한 후보만 현재 모집단에 합치거나 보강 원천으로 쓴다.
특히 단란·유흥주점, 관광펜션·도시민박·한옥체험, 공원·야영장·유원시설처럼 현재
모집단에서 빠질 수 있는 subtype은 **현재 원천과의 중복률·신규 비율을 먼저 측정**한다.

새 원천 레코드는 기존 staging 전체를 이름·정규화 주소·좌표 거리로 먼저 찾는다.
매칭이 확실하지 않으면 자동 병합하지 않고, 새 후보로 남긴다. 후보가 많다는 이유만으로
현재 모집단에 모두 합치지 않는다.

## 4. `PlaceInfo` 반영 전 필수 결정

현재 `InfoKey` 설계는 TourAPI 중심이다. 외부 원천을 실제 `PlaceInfo`로 옮기기
전에 다음 키·병합 규칙을 Java에 먼저 확정한다.

| 외부 원천 사실 | `InfoKey` 후보 | 적용 subtype |
| --- | --- | --- |
| 인허가/영업 상태, 인허가일 | `LICENSE_STATUS`, `LICENSE_DATE` | 음식점·숙소 |
| 객실수, 한실수, 양실수 | `ROOM_COUNT`, `ROOM_TYPE` | 숙소 |
| 시장 개설 주기 | `MARKET_DAY` | 전통시장·정기시장 |
| 화장실/주차장 보유 | `RESTROOM`, `PARKING_INFO` | 시장 |
| 주차장 운영시간·요금·구획수 | `OPERATING_HOURS`, `PARKING_FEE`, `PARKING_CAPACITY` | 주차장 |
| 축제 기간·장소·주최 | `EVENT_START_DATE`, `EVENT_END_DATE`, `EVENT_PLACE`, `ORGANIZER` | 축제 |

`InfoKey`를 먼저 정의하지 않은 원천 필드는 삭제하지 않고 staging 원본으로
보존한다. 키·병합 우선순위·값 정규화 규칙이 확정된 뒤에 `PlaceInfo` upsert를
수행한다.

### 원천 식별 규칙 — `Place.sourceType`

`Place.sourceType`은 유지한다. 재수집 시 덮어써도 되는 레코드와 보존해야 하는
레코드를 구분하는 유일한 근거이기 때문이다. `CURATED`로 직접 등록한 장소를
공공 원천 재수집이 덮어쓰면 손으로 넣은 데이터가 사라진다.

현재 `PlaceDataSourceType`은 `TOUR_API`, `CURATED` 두 값뿐이라 이 문서가 적재
대상으로 정한 보강 원천을 받을 수 없다. 원천별 staging을 `Place`로 승격하기
전에 다음 값을 먼저 정의한다.

| 원천 | `PlaceDataSourceType` 후보 |
| --- | --- |
| TourAPI | `TOUR_API` |
| 전국주차장정보표준데이터 | `PARKING_STANDARD` |
| 행정안전부 식품(일반음식점) | `FOOD_LICENSE` |
| 소상공인시장진흥공단 상가(상권)정보 | `COMMERCIAL_DISTRICT` |
| 행정안전부 문화(숙박업) | `LODGING_LICENSE` |
| 행정안전부 문화(관광숙박업) | `TOURIST_LODGING_LICENSE` |
| 전국전통시장표준데이터 | `TRADITIONAL_MARKET_STANDARD` |
| 전국문화축제표준데이터 | `CULTURAL_FESTIVAL_STANDARD` |
| 직접 등록 | `CURATED` |

원천이 준 관리번호·상가업소번호 등 원본 식별자는 `Place.sourcePlaceId`에
보존한다. `sourceType`과 `sourcePlaceId`는 함께 있어야 증분 갱신 시 같은
레코드를 다시 찾을 수 있다.

`sourceType`과 `sourcePlaceId`는 모두 운영용 내부 필드이므로 API 응답에는
노출하지 않는다.

## 5. 현 단계 불필요 — 재조사·수집·적재하지 않음

- 전국공연행사정보표준데이터
- 영화진흥위원회 KOBIS Open API — 영화·박스오피스 정보는 제공하지만 영화관 장소 목록 API는 확인되지 않음
- 전국장애인편의시설표준데이터
- 한국사회보장정보원 장애인편의시설 현황 API
- 한국관광공사 무장애 여행 정보 API

## 6. DB 적재·캐싱 확인 완료

| 원천 | DB 적재 | 운영 주의사항 |
| --- | --- | --- |
| TourAPI 국문 관광정보 | 가능 | 사진은 항목별 저작권 유형을 따로 처리한다. 기본값은 이미지 URL과 저작권 유형만 저장하고, 이미지 바이너리의 다운로드·변형·재호스팅은 하지 않는다. |
| 일반음식점·숙박업·관광숙박업 API | 가능 | 영업 상태와 `DAT_UPDT_PNT`를 주기적으로 갱신한다. |
| 상가(상권)정보 API | 가능 | 업종 분류와 실제 영업 상태를 같은 사실로 취급하지 않는다. |
| 주차장·전통시장·문화축제 CSV | 가능 | 각 원본 파일의 데이터기준일자와 다운로드 시각을 보존한다. |

## 7. 적재 완료 조건

1. 각 API/CSV 원본이 staging에 원천 식별자와 갱신시점으로 보존돼 있다.
2. subtype별로 TourAPI 장소와 외부 원천의 매칭/미매칭/충돌 수를 집계했다.
3. Java `InfoKey` 매핑과 원천별 병합 우선순위가 확정돼 있다.
4. `PlaceInfo`에 원문을 upsert한 뒤, 빈값·형식 오류·중복을 검증했다.
5. 그 후에만 Info → Attribute → Purpose 정제를 실행한다.
