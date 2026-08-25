# P1 데이터 원천 탐색·실측 결과 보고서

새로 합류한 개발자가 `data-import/data`를 처음 봐도 10~15분 안에 "P1에서 뭘 했고,
뭐가 쓸만하고, 뭐가 왜 탈락했는지" 전체 그림을 잡을 수 있게 쓴 요약 문서다. 조사
로그가 아니라 결과 중심 문서이며, 날짜순으로 읽을 필요가 없다.

---

## 1. Executive Summary

P1(`GO-31`~`GO-125` 등 Jira `GO` 프로젝트)은 **부산·울산·경남(부울경)** 을 대상으로,
서비스가 쓸 수 있는 장소 데이터 원천을 찾고 실제로 받아서 재보는 단계였다. 공공데이터
포털의 인허가·표준데이터부터 지자체 포털(부산 Big-데이터웨이브·경남 빅데이터허브·
울산 데이터포털), 전문기관(산림청·해양수산부·농�촌진흥청·국가유산청 등), 그리고
민간·글로벌 원천(Kakao·Google·Naver·OpenStreetMap·Tripadvisor·예약 플랫폼 등)까지
조사 범위에 넣었다.

**현재 P1 상태**: 실질적으로 끝났다. 남은 것은 판단이 필요한 작업이 아니라 TourAPI
일일 쿼터 초기화 대기(`GO-73`/`GO-75`)와 수개월 뒤 재관측이 필요한 항목(`GO-121`의
`GO-123`/`GO-124`) 둘뿐이며, 둘 다 P2 착수를 막지 않는 것으로 판정됐다(`L0` §6).

**핵심 결론**: 현재 base 원천(인허가·상가정보·표준데이터·TourAPI)은 그대로 유지한다.
그 위에 정보 가치가 확인된 확장 후보 15개 안팎을 새로 찾았고, 겉보기엔 좋아 보였지만
탈락·보류된 원천도 비슷한 수만큼 있다. 민간 대형 원천은 이용조건(저장 가능 여부)부터
막혀 base를 대체하지 못했다.

**P2/P3와의 경계**: 이 문서는 "원천에 무엇이 있는가"만 다룬다. 그 사실을
`PlaceSubtypeCode`·`AttributeKey`로 어떻게 표현할지는 P2-A~F의 몫이고, 실제 수집기·DB
적재는 P3다. 이 문서에서 어떤 taxonomy 결론도 내리지 않는다.

---

## 2. `data-import/data` 디렉터리 읽는 법

```
data/
├── raw/       원본 보존 — 다운로드·API 응답을 가공 없이 저장
├── reports/   실측 결과 — 채움률·중복·순증·판정을 담은 JSON (99개)
└── rejected/  현재 비어 있음(.gitkeep만 존재) — 반려 원본을 넣는 자리만 있고 아직 쓰인 적 없다
```

`data/raw`, `data/reports`, `data/rejected` 전부 `.gitignore` 대상이라 저장소에는
없다. 로컬에만 있으므로 이 문서는 파일 경로를 안내하는 용도로 쓴다.

### `data/raw/` — 하위 구조

| 하위 디렉터리 | 내용 | 대표 예시 |
| --- | --- | --- |
| `standard/` | data.go.kr 표준데이터·LOCALDATA 인허가 CSV/JSON (가장 큰 비중) | `standard/도시공원/전국도시공원정보표준데이터.json`, `standard/인허가_지역별/부산광역시.zip` |
| `tourism/` | TourAPI 목록·상세 응답 | `tourism/tour-api/12_관광지/page-00001.json`, `tourism/tour-api-detail/32_숙박/` |
| `busan_portal/` | 부산 Big-데이터웨이브에서 받은 파일·API 응답 | `busan_portal/비짓부산패스/`, `busan_portal/공공미술현황/` |
| `gyeongnam_portal/` | 경남 빅데이터허브 | `gyeongnam_portal/공공체육시설_시군샘플/` |
| `ulsan_portal/` | 울산 데이터포털 | `ulsan_portal/문화시설현황/` |
| `agrohealing_portal/`, `mcst_portal/` | 개별 기관 자체 포털(농촌진흥청 치유농업ON, 문화체육관광부) | `agrohealing_portal/우수치유농업시설_부울경.json` |

한 원천당 raw 파일 1~수 개, 대응하는 report가 최소 1개 있는 구조다.

### `data/reports/` — 파일명 규칙

`{원천-슬러그}-{YYYYMMDD}-{HHMMSSZ}.json` 형식이다. 같은 원천을 다시 재도 파일을
덮어쓰지 않고 새 타임스탬프로 추가한다 — 여러 버전이 있으면 **가장 최신 것이 최종**이다
(예: `heritage-spatial-info-*`가 두 버전 있으며 `-0900Z`가 최종본).

---

## 3. P1 판단 기준

원천 하나를 잴 때 아래 7개를 확인한다(`other-source-survey.md` §0이 원본 소유):

1. **제공 필드** — 명세가 아니라 실제 응답/파일에 있는 것
2. **채움률** — 컬럼 존재는 근거가 아니다
3. **값 분포** — 한쪽으로 몰리면 채워져 있어도 변별력이 없다
4. **값의 단위·의미** — 같은 컬럼에 다른 단위가 섞일 수 있다
5. **연결성** — 기존 장소와 이름·주소·좌표로 이어지는가
6. **모집단 범위** — 부울경에서 몇 건이며 기존 base의 몇 %를 덮는가
7. **기존 원천 대비 순증 가치** — 이미 있는 것과 겹치면 새로 얻는 게 없다

### 판정 어휘

| 어휘 | 의미 |
| --- | --- |
| **확장 후보** | 모집단을 늘리거나 새 subtype을 담을 만한 순증이 확인된 원천 |
| **정보 보강 후보** | 새 장소를 늘리진 않지만 기존 장소의 필드(가격·전화·프로그램 등)를 보강하는 원천 |
| **판단 보류** | 실측은 했지만 채택/탈락을 지금 결정하기엔 근거(최신성·연결성 등)가 부족 |
| **탈락** | 핵심 기준(최신성·연결성 등)을 명확히 통과하지 못함 |
| **접근 대기** | 서비스키 발급·활용신청 등 승인 절차가 남아 실측을 시작 못 함 |
| **수동 접근 필요** | CAPTCHA 등으로 자동 접근이 막혀 있어 우회하지 않고 대기 |

---

## 4. 현재 기준(base) 원천

`PlaceType`별로 실제 모집단을 만드는 데 쓰는 원천이다. 사실별 원천 선택 원칙(원천
전체가 아니라 사실마다 권위성·완전성·연결성·최신성을 본다)은 `other-source-survey.md`
§1이 소유한다.

| 원천 | 주요 역할 | 범위(부울경) | 주요 강점 | 한계 | 상세 근거 |
| --- | --- | --- | --- | --- | --- |
| 일반음식점 인허가(LOCALDATA) | RESTAURANT·BAR base | 106,186(일반)+13,229(주점 업태) | 영업상태·폐업일자로 명시적 종료 신호 제공, 지번주소 100% | 좌표 EPSG:5174(변환 필요), 전화 48.7% | `GO-120`, `other-source-survey.md` §1 |
| 휴게음식점+제과점 인허가 | CAFE base | 16,820 | 위와 동일 종료 신호 | 동일 좌표계 이슈 | `GO-120` |
| 숙박업+관광숙박업 인허가 | ACCOMMODATION base | 6,385 | 업태명이 subtype에 거의 그대로 대응(여관업→MOTEL 등) | 좌표 EPSG:5174 | `GO-120` |
| 소상공인시장진흥공단 상가정보 | SHOP base | 93,775(대분류 소매) | 주소·좌표 100%, WGS84라 변환 불필요 | 영업상태 컬럼 자체가 없음(종료 신호 못 줌) | `GO-120`, `GO-85` |
| TourAPI | TOURIST_SPOT·ACTIVITY 유일 base | 관광지 1,920·문화시설 348·축제 134·레포츠 467(상세 수집 대상) | 설명·이미지 등 콘텐츠가 풍부 | 목록 응답엔 상태 필드 없음, 상세 채움률이 유형마다 크게 다름(레포츠는 상세 존재율 51%) | `GO-66`, `GO-73`, `tour-api-detail-*` |
| 전국주차장정보표준데이터 | PARKING_LOT 유일 원천 | 2,529 | WGS84라 변환 불필요, TourAPI에 대응 콘텐츠 타입 자체가 없어 유일 원천 | 갱신 주기 미확정(반기 단위 의심) | `GO-79` |

---

## 5. 주요 확장 후보

실제 순증이 report/survey 정본 수치로 확인된 원천이다. 규모 순이 아니라 발견 순.

| 원천 | 부울경 규모 | 비교 기준 | 순증 | 핵심 필드/특징 | 현재 판단 |
| --- | ---: | --- | --- | --- | --- |
| 국가유산청 지정 국가유산 공간정보 | 1,353 | TourAPI 관광지+문화시설(26) | 86.5%(1,170건) | 폴리곤 대표점 계산+EPSG:5179→WGS84 변환 완료, 좌표검증 95.6% | 정보 보강 후보로 유력, 채택은 P2 판단 (`heritage-spatial-info-20260824-0900Z.json`) |
| 부산관광공사 비짓부산패스 | 588(점형 후보) | TourAPI 부산 12+14+39 | 73.5%(432건) | 대표메뉴·이용요금 등 TourAPI에 없는 필드. 전화번호는 전량 0 | 정보 보강 후보로 유력 (`busan-visitbusanpass-attractions-20260824-0715Z.json`) |
| 부산 공공체육시설 현황(SHP) | 239 | LOCALDATA 부산 체육계열 7업종(1,500) | 96.7%(231건) | 좌표 WGS84 100%, "공공 소유 ≠ 민간 인허가" 가설 확증 | 확장 후보 자격 있음 (`busan-public-sports-facility-shp-20260824-0730Z.json`) |
| 경남 시군 공공체육시설(A유형) | 7개 시군 표본 | LOCALDATA 대비 | 100%(7개 시군 전부 이름 일치 0건) | 반복 재현으로 근거 확보 후 나머지 시군 실측 중단 | 확장 후보 근거 확보 (`gyeongnam-county-public-sports-*.json` 4건) |
| 전국관광안내소표준데이터 | 115 | TourAPI 관광지+문화시설 | 95.7%(110건) | 좌표·운영시간·전화 100%, subtype 대응 개념 자체가 없음 | 확장 후보 + 신규 장소군 (`tourist-info-center-standard-20260824-0800Z.json`) |
| 전국박물관미술관정보표준데이터 | 116 | TourAPI 관광지+문화시설 | 33.6%(39건) | 좌표·입장료·운영시간 100% | 확장 후보(§8 경쟁 원천 비교 참고) (`museum-artgallery-standard-20260824-1030Z.json`) |
| 울산광역시 문화시설 현황 | 57 | TourAPI | 73.7%(42건, 공연장 100%·도서관 81%) | 도서관은 subtype 대응 개념 없음 | 확장 후보(공연장·미술관)+신규 장소군(도서관) (`ulsan-culture-facility-20260824-1040Z.json`) |
| 전국농어촌체험휴양마을표준데이터 | 173 | TourAPI 관광지+레포츠 | 87.3%(151건) | 좌표 100%, "체험마을" subtype 대응 개념 없음 | 확장 후보 + 신규 장소군 (`rural-fishing-village-standard-20260824-1250Z.json`) |
| 해양수산부 마리나 정보 | 11 | TourAPI 관광지+레포츠 | 100% | 규모는 작으나 완전 신규. EPSG:5179→WGS84 변환 검증 완료 | 확장 후보(소규모)+신규 장소군 (`marina-and-healing-forest-20260824-1310Z.json`) |
| 해양수산부 어항정보 | 25 | TourAPI 관광지 | 48%(12건) | 좌표 WGS84, 13개 필드 100% | 확장 후보 (`fishing-port-and-kids-forest-20260824-1330Z.json`) |
| 산림청 유아숲체험원 등록현황 | 55 | TourAPI | 100% | 좌표 없음(산지번 주소) | 확장 후보 + 신규 장소군 (`fishing-port-and-kids-forest-20260824-1330Z.json`) |
| 전국낚시터정보표준데이터 | 46 | TourAPI 관광지+레포츠 | 93.5%(43건) | 좌표·요금·어종 100%, 이번 세션 중 최고 순증률 | 확장 후보 (`fishing-spot-standard-20260824-1345Z.json`) |
| 농촌진흥청 치유농업ON | 7(전부 경남) | TourAPI 경남 | 85.7%(6건) | 기관 인증 정본이나 좌표·상세주소 컬럼 자체 없음 | 확장 후보(소규모) (`agrohealing-farm-20260824-1400Z.json`) |
| 부산광역시 부산명소정보(API) | 213 | TourAPI 부산 관광지+문화시설 | 83.6%(178건) | 전화 86.4%(비짓부산패스는 0%) | 확장 후보 + 정보 보강 (`busan-approved-apis-20260825-0600Z.json`) |
| 부산광역시 공공미술 현황(API) | 1,763건 / 767곳 | 대응 원천 없음 | 100%(완전 신규) | 명칭·유형·좌표 100%, 이번 세션 최대 규모 신규 장소군 | 확장 후보(신규 장소군) (`busan-approved-apis-20260825-0600Z.json`) |

---

## 6. 정보 보강 후보

새 장소를 늘리기보다 **기존 장소에 없는 필드를 채워주는** 원천이다.

| 원천 | 부울경 규모 | 왜 확장이 아니라 보강인가 |
| --- | ---: | --- |
| 소상공인시장진흥공단 온누리상품권 가맹점 현황 | 25,723 | 소재지가 광역시·도 단위뿐이라 개별 지오코딩 불가. 다만 가맹점명의 66%가 기존 전통시장 원천과 연결돼, 가맹여부·취급품목을 붙이는 용도로만 가치 있음 |
| 문화체육관광부 전국문화기반시설총람 | 139(박물관+미술관) | 박물관미술관표준데이터 대비 순증 41.0%(57건)로 모집단은 크지만 **좌표·운영시간·입장료 필드 자체가 없다** — 핵심 필드는 여전히 표준데이터가 우세 |
| 부산교통공사 역주변 음식점 정보 | 840(고유 790곳) | LOCALDATA 대비 이름 일치 52.0%로 모집단 확장 목적이 아니라 대표메뉴·역 접근성 등 큐레이션 필드 보강용 |
| 경상남도 관광지 지정현황 | 25 | 표준데이터 경남 하위집합과 60% 부분 중복. 지정일자·시행청 등 법정 근거 필드만 표준데이터에 없어 보강 가치 |
| 부산시설공단 공원시설 현황(API) | 1,620(프로그램 단위) | 이름과 달리 시설 카탈로그가 아니라 **프로그램/행사 목록**. 대상 공원이 7곳뿐이라 모집단 확장 없음, 프로그램 정보만 보강 |

---

## 7. 판단 보류 / 탈락 사례

전부 나열하지 않고 **반복되는 실패 패턴** 중심으로 정리한다.

| 원천 | 상태 | 핵심 이유 |
| --- | --- | --- |
| 전국문화축제표준데이터 | 탈락 | 부울경 182건 중 73.1%가 이미 종료일 경과 — 오래된 기준일이 방치됨 |
| 전국등산로표준데이터 | 탈락 | 부산·울산 제공기관 등록이 0건, 좌표·주소 컬럼 자체가 없음 |
| 기후에너지환경부 생태관광지역정보 | 판단 보류 | 좌표·주소 컬럼이 아예 없는 서술형 마케팅 텍스트 |
| 한국환경보전원 환경교육자원 | 판단 보류 | 레코드의 99%가 '기관' 타입 — 방문 가능한 **장소가 아니라 협회·센터 사무실** |
| 경남 시군별 숙박업소정보(산청·창녕 표본) | 탈락 | LOCALDATA와 건수까지 정확히 일치 — **재배포본**임을 확인 |
| 경남 시군별 공공체육시설(D/E/F유형) | 판단 보류/대기 | GIS(CAPTCHA), 도 전체 등록체육시설(39.5% 혼재), API 승인 필요 |
| 한국교통안전공단 주차장개별정보 | 탈락 | 전국 234,239행이나 컬럼이 **ID 대응표 2개뿐**(시설명·주소·좌표 없음) |
| 국민체육진흥공단 전국체육시설 정보 | 판단 보류 | 부산은 기존 SHP와 69.7% 중복, 경남 진주는 72.2% 순증 — **지역 편차가 커서 공공체육시설 대체 근거로 부적합**. 민간 등록시설(당구장·태권도 등)이 압도적 |
| 산림청 산림복지전문업 등록현황 | 판단 보류 | 좌표는 있으나 **사업자 등록 주소이지 실제 체험 현장인지 미확인** |
| 국가유산청 지정문화재현황(API) | 접근 대기 | data.go.kr이 아니라 기관 자체 LINK형 별도 인증 체계 — 승인 절차가 복잡해 사용자가 보류 결정 |
| 국립공원공단 야영장·탐방안내소·탐방로 | 수동 접근 필요 | CAPTCHA로 자동 접근 불가, 우회하지 않음 |

---

## 8. 경쟁 원천 비교 결과

현재 원천보다 더 나은 대체재가 있는지 직접 검증한 결과다.

| 비교 | 결론 | 근거 |
| --- | --- | --- |
| 박물관미술관표준데이터 **vs** 문화기반시설총람 | **병행** — 핵심 필드(좌표·운영시간·입장료)는 표준데이터가 유일 보유. 총람은 모집단 20% 더 크고(순증 57건) 법정 등록번호를 추가로 줌 | `competing-source-comparison-20260824-1800Z.json` |
| 부산 SHP/경남 A유형 **vs** 국민체육진흥공단 전국체육시설 | **현재 유지** — 지역 편차가 커서(부산 69.7% 중복, 경남 진주 72.2% 순증) 공공체육시설 카탈로그를 대체하지 못함 | `sports-facility-national-20260825-0700Z.json` |
| 현재 base 전체 **vs** OpenStreetMap | **현재 유지** — 부산 5개 장소군 실측 결과 이용조건은 통과했으나(ODbL, 저장 가능) 음식점 4.2%·체육 7.2%로 모집단이 작음. 공원(77.7%)·숙박(31.6%, 이름 채움 90.7%)만 제한적 보조 가치 | `osm-busan-category-comparison-20260824-1700Z.json` |
| 국가유산청 지정문화재현황(API) **vs** 국가유산청 공간정보(현재 채택) | **보류** — API는 LINK형 별도 인증이라 비교 자체를 진행하지 않음 | `other-source-survey.md` §28 |

---

## 9. 민간 / 외부 실시간 API

P1 **적재** 후보와 향후 **실시간 조회** 후보를 분리한다. "P1 저장 부적합"은 서비스
전체 영구 제외가 아니다(`decision-log.md` 2026-08-24 원칙).

| 원천 | P1 저장 | 향후 역할 | 재검토 조건 |
| --- | --- | --- | --- |
| Kakao Local | 제한(저장 불허, 실시간 호출만) | 영업시간·폐업·장소 존재 검증 | 최신 실시간 정보 필요 시 |
| Google Places | 제한(place_id·좌표 30일만 캐싱) | 장소정보·운영정보 검증 | 최신 정보 검증 필요 시 |
| Naver 지역검색/Maps | 현재 비용으로 보류(2025-07 무료 티어 폐지) | 국내 장소·메뉴·영업정보 | 예산 확보 또는 정책 변경 시 |
| Foursquare | 제한(ID류만 캐싱) | 실시간 POI 보강 | 실시간 POI 보강 필요 시 |
| Tripadvisor Content API | 계약형(파트너 승인 전제, AI/ML 학습 금지) | 관광 콘텐츠·리뷰 | 리뷰 기능 개발 시 계약 조건 기준 |
| Booking/Agoda/Expedia Rapid | 계약형 | 숙박 예약·가격·재고 | 예약 기능 개발 시 |
| OpenStreetMap | 저장 가능(ODbL), 품질은 B등급(제한적) | 공원 폴리곤 경계·숙박 스크리닝 보조 | 한국 커뮤니티 매핑이 크게 늘어날 때 |
| KOPIS | 접근 대기(인증키 미확보, 심사 없어 보이나 미확인) | 공연·축제·기간·가격 | 인증키 발급 후 실측 |
| 기상청 단기예보 조회서비스 | 조회형(무료·자동승인) | 날씨 영향 정보 | 날씨 연동 기능 개발 시 |
| 한국교통안전공단 주차장실시간정보 | 조회형(문서 확인만, 미실측) | 실시간 잔여 주차면수 | 실시간 주차 표시 기능 개발 시 |
| 야놀자/여기어때 | 해당 없음(공개 조회 API 없음) | 국내 숙박 예약 | 사업 제휴 필요 시 |

---

## 10. P1에서 발견된 신규/애매 장소군

현재 `PlaceType`/`PlaceSubtype` 어디에도 자연스럽게 들어가지 않는 장소군 관찰이다.
**여기서 새 taxonomy 결론을 내리지 않는다** — P2-A가 볼 재료만 남긴다.

| 장소군 | P1에서 확인한 규모/원천 | 현재 분류상 문제 | P2에서 볼 쟁점 |
| --- | --- | --- | --- |
| 관광안내소 | 115개소, 전국관광안내소표준데이터 | 대응 subtype 없음 | 신규 subtype 신설 여부 |
| 공공미술 | 767곳/1,763작품, 부산 공공미술 API | 대응 subtype 없음 | 레코드 단위가 '장소'가 아니라 '작품'(한 곳에 평균 2.3개) — subtype 신설 시 단위 재정의 필요 |
| 마리나(요트 계류시설) | 11곳, 해양수산부 마리나 정보 | 대응 subtype 없음 | 규모가 작아 별도 subtype 실익 검토 필요 |
| 유아숲체험원 | 55개소, 산림청 등록현황 | 대응 subtype 없음 | 좌표 없음(산지번 주소만) — 연결 방법도 함께 검토 |
| 치유의숲 | 6곳, 산림청 현황 | 자연휴양림과 별개 시설군이나 규모 작음 | 규모가 작아 독립 subtype 실익 낮을 수 있음 |
| 농어촌체험휴양마을 | 173개, 전국표준데이터 | '체험마을' 대응 개념 없음 | ACCOMMODATION 하위인지 별도 유형인지 |
| 치유농업시설 | 7곳(전부 경남), 치유농업ON | 대응 subtype 없음 | 규모가 매우 작아 우선순위 판단 필요 |
| 도서관 | 21곳(울산), 울산 문화시설 현황 | 대응 subtype 없음 | TOURIST_SPOT에 넣을지 별도 유형인지 |
| 거리공연장소 | 5곳(창원), 경남 빅데이터허브 | 대응 subtype 없음 | 규모가 매우 작아 보류 가능성 높음 |
| 산림복지전문업(업체 등록부) | 부울경 74건(부산20·울산12·경남42) | 시설이 아니라 사업자 등록부 — 애초에 '장소'로 볼 수 있는지 자체가 쟁점 | 사업자 주소=실제 시설 위치인지 P2 이전에 별도 검증 필요할 수 있음 |

---

## 11. P1 현재 상태 및 backlog

**P1 실질 완료 / P2-A 진입 가능** (`L0-project-context.md` §6, 2026-08-25 phase gate
재판정).

| 구분 | 항목 | 비고 |
| --- | --- | --- |
| P1 장기관찰 backlog(P2 비차단) | `GO-73`/`GO-75`: TourAPI 관광지(12) 상세 391/1,920건, 나머지는 일일 쿼터 초기화 후 resume만 필요 | 필드·범위·연결성은 이미 표본으로 측정 완료 — 수량 문제일 뿐 |
| P1 장기관찰 backlog(P2 비차단) | `GO-121`의 `GO-123`(인허가 갱신시점 증분 신뢰성): 실제 배치 갱신 이벤트 관측 필요 | 2일 창 1회 측정으로 재현율 100%는 이미 확인됨 |
| P1 장기관찰 backlog(P2 비차단) | `GO-121`의 `GO-124`(표준데이터 미관측 임계값): 수개월 뒤 재스냅샷 필요 | 주차장 CSV 두 번째 스냅샷이 아직 없음(2026-08-19 1건뿐) |

**완료된 주요 Jira** (전체 히스토리는 Jira에서 확인, 여기선 개요만): `GO-31`(Attribute별
조사 출처 확정) · `GO-66`/`GO-73` 일부(TourAPI 상세 실측·부분 수집) · `GO-76`/`GO-77`
(API 접근 준비, 대부분 CSV/파일데이터 우회로 해결) · `GO-78`(CSV 3건 실측+좌표계 확정)
· `GO-83`(보강 API 4건 실측+PlaceInfo 후보 작성) · `GO-111`(부울경 범위 확정) ·
`GO-120`(base 원천 확정) · `GO-122`(원천별 종료 계약) · `GO-125`(외부 보강 원천
탐색·실측 및 경쟁 원천 검증, 최종 종료 권고까지 완료).

---

## 12. 개발자가 다음에 어디를 보면 되는가

| 알고 싶은 것 | 볼 문서 |
| --- | --- |
| 어떤 원천을 채택했는지 | [`other-source-survey.md`](other-source-survey.md) §1 (A/B표) |
| 실제 중복/순증 수치를 보고 싶다 | `data/reports/*.json` (이 문서 §5~§8의 파일명 참고) |
| 원본 파일을 보고 싶다 | `data/raw/...` (§2 디렉터리 구조 참고) |
| 어디서 언제 다운로드했는지 | [`source-downloads.md`](source-downloads.md) |
| AttributeKey별 채움률·측정 이력 | [`attribute-coverage.md`](attribute-coverage.md) |
| 판단이 바뀐 이유·재검증 이력 | [`../../docs/decision-log.md`](../../docs/decision-log.md) |
| 현재 프로젝트 단계·P1→P2 게이트 | [`../../docs/L0-project-context.md`](../../docs/L0-project-context.md) §6 |
| P2 taxonomy 판단(착수 시) | 현재 문서에 없음 — P2-A 담당자 산출물 확인 |

---

## 부록. 주요 report 인덱스

99개 report 전부를 나열하지 않고 카테고리별 대표만 묶는다. 파일명 뒤 숫자는 다운로드
시각(UTC)이라 같은 원천이 여러 개면 **가장 최신 것이 최종**이다.

### base 원천 검증
| report | 측정 대상 | 대응 raw |
| --- | --- | --- |
| `tour-api-import-*.json` (11개) | TourAPI JSON → MySQL 적재 dry-run/실적재 | `tourism/tour-api/` |
| `tour-api-detail-{12,14,15,28,32,38,39}-*.json` | TourAPI 상세(detailIntro2) 채움률 | `tourism/tour-api-detail/` |
| `tour-api-detail-analysis-{14,32,38,39}-20260823-131819Z.json` | 문화시설·숙박·쇼핑·음식점 상세 분석 | 위와 동일 |
| `tourapi-termination-signal-recheck-20260825.json` | TourAPI 종료 신호(재수집 대조) | `tourism/tour-api-recheck-20260825/` |

### LOCALDATA subtype 확장 (2026-08-23)
| report | 측정 대상 |
| --- | --- |
| `localdata-sportsfacility-*.json` | 체육시설업 8업종 |
| `localdata-performancehall-*.json` | 공연장 |
| `localdata-museum-*.json`, `localdata-museum-link-*.json` | 박물관·미술관 |
| `localdata-themepark-*.json` | 테마파크업 |
| `localdata-temple-*.json`, `localdata-temple-final-*.json` | 전통사찰 |
| `localdata-largestore-*.json` | 대규모점포 |
| `localdata-activity-*.json` | 레저 업종 10종 |
| `localdata-camping-*.json` (2개) | 야영장업 |
| `localdata-hanokstay-*.json`, `localdata-tourpension-*.json`, `localdata-foreignguesthouse-*.json` | 한옥체험·관광펜션·외국인관광도시민박 |
| `localdata-culturecenter-tourrest-*.json` | 지방문화원 |

### 표준데이터 실측 (2026-08-24)
`city-park-standard`, `culture-festival-standard`, `forest-recreation-standard`,
`mountain-trail-standard`, `street-tourism-standard`, `camping-ground-standard`,
`traditional-market-standard`, `public-facility-open-standard`, `museum-artgallery-standard`,
`tourist-info-center-standard`, `rural-fishing-village-standard`, `fishing-spot-standard`,
`themepark-15124679-origin-check` — 전부 §5·§7의 표에서 인용된 원본.

### 지역 포털·전문기관 확장 원천
`busan-visitbusanpass-attractions`, `busan-subway-tourist`, `busan-station-area-wide`,
`busan-public-sports-facility-shp`, `busan-approved-apis`, `gyeongnam-designated-tourist-site`,
`gyeongnam-county-public-sports-{sample,round2,round3,round4-Atype-confirm}`,
`gyeongnam-namhae-ctype-followup`, `gyeongnam-county-lodging-sample`,
`ulsan-culture-facility`, `heritage-spatial-info-20260824-0900Z`(최종본),
`marina-and-healing-forest`, `fishing-port-and-kids-forest`, `agrohealing-farm`,
`onnuri-merchant`, `eco-tourism-and-env-education`, `sports-facility-national`.

### 경쟁 원천·민간 API 검토
`competing-source-comparison`, `osm-busan-category-comparison`,
`private-large-scale-source-review`, `access-gated-source-reclassification`,
`access-verification-empirical-test`, `parking-id-crosswalk-and-wellness-final`.

---

## 이 문서 작성 중 발견한 정본 충돌 (수정하지 않음)

`attribute-coverage.md`의 "측정 이력" 표에는 2026-08-23에 TourAPI 숙박(32)·쇼핑(38)·
음식점(39) 상세를 재측정했다고 기록돼 있고 대응 report 파일도 실제로 존재한다. 그런데
바로 다음 문단은 "숙소(32)·쇼핑(38)·음식점(39)의 상세 채움률은 **아직 재측정하지
않았다**... 명세 기반 추정치다"라고 반대로 적혀 있다 — 같은 문서 안에서 서로 모순된다.
이 보고서는 원칙대로 새 문서 생성 외에는 기존 문서를 고치지 않으므로, 실제 수정은
`attribute-coverage.md` 담당자가 판단하도록 여기 기록만 남긴다.
