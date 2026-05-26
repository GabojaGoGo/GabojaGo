# 가보자GO — Codex 지침서

## 작업 워크플로우

### 상황별 자동 적용 스킬
| 상황 | 사용할 접근법 |
|------|--------------|
| 새 기능 설계 요청 | `/plan-eng-review`: 데이터 흐름·엣지케이스·테스트 계획 먼저 수립 |
| 버그·오류 수정 | `/investigate`: 증상→원인→가설→검증, 3회 실패 시 사용자에게 보고 |
| 코드 작성 완료 후 | `/review`: null safety·N+1·JWT 우회 등 자동 체크 |
| PR·커밋 요청 | `/ship`: analyze → 테스트 → commit → PR |
| 인증·보안 관련 변경 | `/cso`: .env 노출·OWASP Top 10·redirect URI 자동 검토 |
| "테스트해줘" 요청 | `/qa`: 엣지케이스(null lat/lng, 빈 purposes) + 회귀 테스트 |
| 주간 정리 요청 | `/retro`: git log 7일 기반 분석 |

### 핵심 원칙
- 구현 전 설계 리뷰 먼저 (ask before build)
- 버그 수정은 atomic commit + 회귀 테스트 세트로
- 보안 관련 파일(`.env`, JWT, OAuth) 변경 시 항상 `/cso` 관점으로 검토
- 3번 디버깅 실패 → 가설 목록과 함께 사용자에게 보고

## 스택
- **Frontend**: Flutter ^3.11.4 · Material 3 · `app_links` 딥링크
- **Backend**: Spring Boot 3.2.4 · Hibernate 6 · Flyway · MySQL (Docker)
- **인증**: 카카오 OAuth PKCE → JWT (access 메모리 / refresh SecureStorage)
- **지도**: 카카오맵 Flutter SDK

## 디렉토리
```
tripmate/
├── lib/
│   ├── main.dart                  # 앱 진입, 딥링크, MainShell
│   ├── models/user_prefs.dart     # UserPrefs, UserPrefsScope
│   ├── services/
│   │   ├── auth_service.dart      # 로그인/로그아웃/JWT
│   │   ├── api_service.dart       # 백엔드 API 호출
│   │   ├── user_data_service.dart # 족적/버킷/혜택 로컬+서버 sync
│   │   └── tour_api_service.dart  # 관광공사 API 직접 호출
│   └── screens/
│       ├── home_screen.dart               # 홈 (주변관광지·축제·개인화 인사+CTA)
│       ├── nearby_spots_screen.dart       # 지도 전체보기 (반경 20km, 최대 300개)
│       ├── spot_detail_screen.dart        # 관광지 상세
│       ├── my_trip_screen.dart            # 기록탭 (족적·버킷·혜택)
│       ├── travel_setup_screen.dart       # 취향설정 (showCourseResult 파라미터)
│       ├── course_loading_screen.dart     # 코스 로딩 화면 (돋보기 애니메이션+취향칩, 취향있을때 CTA→여기로)
│       ├── travel_course_result_screen.dart # 맞춤 코스 추천 결과 (preloadedCourses 파라미터)
│       ├── course_detail_screen.dart      # 코스 상세 (장소 목록, 저장/해제)
│       ├── planner_screen.dart            # 플래너 (취향 기반 자동 코스추천 + AppBar 북마크로 내플래너)
│       ├── subsidy_screen.dart            # 혜택·보조금 (is_repeatable 지원)
│       └── benefit_detail_screen.dart     # 보조금 상세 (외부링크→앱복귀감지→신청완료확인)
├── backend/
│   ├── .env                       # DB/카카오/JWT 키 (절대 커밋 금지)
│   └── src/main/java/com/tripmate/backend/
│       ├── auth/                  # 카카오OAuth, JWT, 토큰갱신
│       ├── user/                  # User, UserProfile, UserSession
│       ├── userdata/              # Footprint, BucketItem, BenefitReport, Preference
│       ├── spot/                  # 관광지 목록·혼잡도
│       ├── festival/              # 축제 목록
│       ├── course/                # CourseController, CourseService, CourseDto, CourseDetailDto
│       └── infrastructure/
│           ├── tourapi/           # TourApiClient (areaBasedList2, detailIntro2, detailInfo2, detailCommon2), 혼잡도 스케줄러
│           └── kakao/             # KakaoLocalClient (주변맛집/숙박), KakaoDirectionClient (이동시간)
└── .env                           # Flutter용 (API_BASE_URL, KAKAO_NATIVE_APP_KEY)
```

## 핵심 규칙

### 환경 파일 보안 ⚠️
**절대 커밋하면 안 됨:**
- `.env` — DB, 카카오 키, JWT 시크릿, TOUR_API 키
- `.env.imac` — 로컬 개발 환경 변수 (민감한 정보)
- `.env.tailscale` — Tailscale VPN 환경 변수

**Git 관리:**
```bash
# .gitignore에 이미 추가됨
.env
.env.local
.env.imac
.env.tailscale
.env.*.local
```

**안전한 방법:**
1. 로컬에서만 `.env` 파일 생성 (git 추적 안 됨)
2. 변수 템플릿은 `.env.example` 사용
3. 팀원과 환경 변수 공유 시 별도 채널 사용

### 네트워크 / 멀티 환경
| ENV 플래그 | .env 파일 | API_BASE_URL | 용도 |
|-----------|----------|-------------|------|
| (없음) | `.env` | `http://jyys-MacBook-Pro.local:8080/api` | 맥북 로컬 Docker |
| `imac` | `.env.imac` | `http://192.168.1.182:8080/api` | 아이맥 Docker (같은 WiFi) |
| `tailscale` | `.env.tailscale` | `http://100.104.150.127:8080/api` | 외부 접속 (Tailscale VPN) |

- 실행: `flutter run --dart-define=ENV=imac` / `--dart-define=ENV=tailscale`
- `UserDataService._base` = baseUrl에서 `/api` 제거
- 실기기 테스트 시 localhost 절대 사용 금지
- Tailscale: 아이맥 IP `100.104.150.127`, 폰도 Tailscale 앱 로그인 필요

### 인증 흐름
1. `startKakaoLogin()` → 브라우저 → 백엔드 콜백 → 딥링크
2. `handleDeepLink()` → 토큰 저장 → `syncFromServer()` await → `/main`
3. 취향 저장 시 반드시 `AuthService.updatePrefs()` + `UserDataService.savePrefs()` 동시 호출

### 카카오 OAuth redirect URI (동적 생성)
- `KAKAO_REDIRECT_URI` 환경변수 불필요 — 백엔드가 요청의 Host 헤더에서 자동 생성
- `KakaoOAuthService.buildRedirectUri(request)` → `scheme://host:port/auth/kakao/callback`
- 새 환경 추가 시 **카카오 개발자 콘솔**에만 해당 URL 등록하면 됨
- 현재 등록된 URI: `http://jyys-MacBook-Pro.local:8080/...`, `http://192.168.1.182:8080/...`

### 취향 저장소 (두 곳 동시 저장 필수)
| 저장소 | 키 | 용도 |
|--------|-----|------|
| AuthService SharedPrefs | `auth_purposes`, `auth_duration` | 앱 재시작 시 복원 |
| UserDataService SharedPrefs | `ud_prefs` | 서버 sync 기준 |

### Hibernate 6 주의
- `@Enumerated(EnumType.STRING)` → MySQL ENUM 타입 매핑 (VARCHAR 컬럼과 충돌)
- VARCHAR 컬럼엔 반드시 `@Convert(converter = ...)` 사용

### 백엔드 재빌드
```bash
# 맥북 (Docker 로컬 실행 시)
cd ~/tripmate/backend && docker compose up --build -d
docker compose logs -f backend

# 아이맥 (Docker 원격 실행 시) — 변경 파일 먼저 동기화 후
ssh imac "cd ~/tripmate/backend && docker compose up --build -d"
```

## API 엔드포인트 요약
```
GET  /api/spots?lat=&lng=&limit=     # 주변관광지 (기본10, 지도100)
GET  /api/spots/congestion?lat=&lng=&limit=
GET  /api/festivals?lat=&lng=
GET  /api/courses?purposes=&duration=&lat=&lng= # 앵커 기반 코스 추천 (앵커 선정→fetchNearby→슬롯 배치→Nearest Neighbor 최적화, places+dayLabel+slotType 포함; lat/lng 시 거리+복합점수 정렬)
GET  /api/courses/{contentId}/detail?purposes=  # 코스 상세 (anchor_/custom_ prefix면 즉시 빈 반환; 일반은 detailIntro2+detailInfo2+detailCommon2 병렬; food→주변맛집, resort→주변숙박 via Kakao Local)
GET  /me/prefs                       # 취향 조회
PUT  /me/prefs                       # 취향 저장
GET  /me/footprints                  # 족적
POST /me/footprints
DELETE /me/footprints/{id}
GET  /me/bucket-list
POST /me/bucket-list
PATCH /me/bucket-list/{id}
DELETE /me/bucket-list/{id}
GET  /me/benefit-reports
POST /me/benefit-reports
```

## 자주 쓰는 패턴

### Flutter 새 API 연동
```dart
// api_service.dart에 추가
static Future<T> getSomething(params) async {
  final url = '$baseUrl/endpoint?param=$param';
  final res = await http.get(Uri.parse(url)).timeout(const Duration(seconds: 20));
  if (res.statusCode == 200) return json.decode(utf8.decode(res.bodyBytes));
  throw Exception('Server error: ${res.statusCode}');
}
```

### UserDataService 로컬우선 패턴
- 읽기: SharedPrefs에서 즉시 반환 (동기)
- 쓰기: 로컬 갱신 → 비동기 서버 sync (실패해도 로컬 유지)
- 로그인 후: `syncFromServer()` → 서버 데이터로 로컬 덮어쓰기

### 코스 추천 흐름 (취향 설정 완료 시)
1. 홈 CTA 탭 → `CourseLoadingScreen(prefs: userPrefs)` (취향 없으면 `TravelSetupScreen`)
2. `CourseLoadingScreen`: 🔍 흔들림 + 취향칩 표시, `ApiService.getCourses()` 병렬 호출
3. API 완료 + 최소 2초 경과 → ✓ 체크 표시 → 1.5초 후 `TravelCourseResultScreen(preloadedCourses: _courses)`
4. `TravelCourseResultScreen`: `preloadedCourses != null`이면 즉시 표시 (API 재호출 없음)
5. 코스 탭 → `CourseDetailScreen`: `course['places'] != null`이면 API 미호출 (커스텀 코스 최적화)

### 앵커 포인트 기반 코스 추천 (CourseService v2) + Phase A 개선
- **앵커 시스템**: 전국 25개 여행 거점 (`ANCHORS` 상수), 각 앵커에 (이름, 위경도, areaCode, purposes, tags) 정의
- **앵커 선정 및 정렬**: `selectAnchors(purposes, count, userLat, userLng)`
  - 기본: matchScore(purpose 매칭도) 내림차순
  - 사용자 위치 제공 시: `computeAnchorScore()` = matchScore×70% + distanceScore×30%
  - distanceScore: 선형 감소 (300km=0점) → `Math.max(0, 30 * (1 - dist/300))`
  - 다양성 보장: 같은 시도 최대 2개 + 50km 거리 제한
- **카테고리 필터링**:
  - **MEAL**: Kakao Local FD6 categoryName 기반 필터링
    - 제외: 베이커리, 파스트푸드, 도넛, 디저트, 아이스크림, 주점, 바 등
    - 우선: 한식(+5점), 양식/해산물/고기 등 화이트리스트 매칭(+10점/개)
  - **TPO 식사 필터링** (Phase A):
    - `MealTimeContext` enum: BREAKFAST/LUNCH/DINNER/GENERAL
    - `morning` → BREAKFAST(죽/국밥/베이커리 우선, 갈비/한정식/회 제외, 베이커리 예외 허용)
    - `lunch` → LUNCH(한식/일식/중식/양식 가벼운 한끼)
    - `dinner` → DINNER(갈비/한정식/회/정찬 우선, 분식/김밥 제외)
    - `afternoon`/`evening` → GENERAL(ALWAYS 블랙리스트만 적용)
    - `PlacePool`에 `Map<MealTimeContext, List<KakaoPlace>>` 사전 필터링 저장
  - **LODGING**: 취향별 필터링
    - resort+food: 호텔/리조트/펜션 우선, 모텔/여관 제외
    - budget: 게스트하우스/모텔/민박/한옥/호스텔 우선
- **슬롯 템플릿**: `TEMPLATES` — duration별 DAY 단위 일정 구성 (SIGHT→MEAL→SIGHT→MEAL→LODGING 패턴)
  - day: 5슬롯(1DAY), 1n2d: 9슬롯(2DAY), 2n3d: 14슬롯(3DAY), 3nplus: 19슬롯(4DAY)
  - LODGING은 각 DAY 마지막 (마지막 DAY 제외), 당일치기는 LODGING 없음
- **장소 수집**: `fetchWithFallback()` — TourAPI `fetchNearby()` + Kakao Local 병렬 호출
  - SIGHT: TourAPI fetchNearby (반경 30km, contentType별 50개)
  - MEAL: Kakao Local FD6 (반경 15km, 15개) + 시간대별 필터링
  - LODGING: Kakao Local AD5 (반경 20km, 10개) + 취향별 필터링
  - 폴백: 부족 시 반경 50km 확대 → TourAPI 음식점(39)/숙박(32) (필터링 적용)
- **동선 최적화** (Phase A):
  - `optimizeRoute()` — Nearest Neighbor 휴리스틱
  - DAY 단위 처리, 현재 위치에서 가장 가까운 미방문 장소 배치
  - **SIGHT 2-opt 후처리**: DAY 내 SIGHT 시퀀스만 추출 → 거리 기반 reverse-swap → 원래 인덱스에 재삽입 (슬롯의 timeLabel/type 보존)
    - `applyTwoOptOnSights()` — 교차 제거, 가드(20회) 포함
  - LODGING: 마지막 비숙박 일정 기준(`lastNonLodgingWithPlace()`)에서 가장 가까운 숙소 선택 (기존 무게중심 → 변경)
- **DTO**: `SubPlace`에 `dayLabel`("DAY 1"), `timeLabel`("morning"/"lunch"등), `slotType`("sight"/"meal"/"lodging") 추가
- contentId 형식: `"anchor_{앵커이름}_{purposes}"` — `getCourseDetail()`에서 `startsWith("anchor_")` 또는 `startsWith("custom_")`이면 빈 detail 반환
- **장소 스코어링**: 이미지(+10), 주소(+5), 좌표(+5), 취향 키워드 매칭(+3/개, 제목+주소 결합) → `scoredItems()`, `computeScore()`
- **코스 점수** (relevanceScore): `computeAnchorScore()` = matchScore×70 + distanceScore×30 (사용자 위치 기반 동적 계산)
- `travel_course_result_screen.dart`: `relevanceScore >= 70`이면 "복합 취향 최적" 뱃지 Chip, 장소 미리보기(`_PlacesPreview`) DAY별 표시
- `course_detail_screen.dart`: `_DayDivider` DAY 구분 헤더, 슬롯타입별 아이콘·색상 (sight=초록, meal=주황, lodging=보라)
- **Flutter 연동**: `course_loading_screen.dart`에서 현재 위치(lat/lng) 자동 수집 → `ApiService.getCourses(purposes, duration, lat, lng)`

### 코스 상세 로딩 패턴
- `ApiService.getCoursesWithDetail()` — 코스 목록 + 각 상세(places/distance/taketime) 병렬 프리패치
- `CourseDetailScreen(course, purposes)` — places 이미 있으면 재사용, 없으면 getCourseDetail 호출
- 플래너 취향 변경 감지: `didChangeDependencies()`에서 `UserPrefsScope.of(context).prefs` 비교

### 외부 API 키 위치 (backend/.env)
- `KAKAO_REST_API_KEY` — Kakao Local + Kakao Direction + OAuth 공용
- `TOUR_API_SERVICE_KEY` — 한국관광공사 TourAPI 공용 (areaBasedList2/detailIntro2/detailInfo2/detailCommon2)

### 저장된 코스 (로컬 전용)
- `UserDataService.getSavedCourses()` / `saveCourse()` / `removeSavedCourse()`
- SharedPrefs 키: `ud_saved_courses` (JSON 리스트)

### SpotData 생성
```dart
SpotData.fromJson(json)  // 기본 파싱
spot.copyWith(congestion: ..., congestionSource: ...)  // 혼잡도 병합
```

### 홈 화면 개인화 패턴
- `_kGreetingVariants`: purpose별 `(greeting, cta)` 튜플 3개 — 매 진입 `Random().nextInt(3)`으로 선택
- `_greetingVariantIdx`: `_HomeScreenState.initState`에서 결정, `_TravelStartCard(variantIdx:)`에도 전달
- 취향 칩: `_PrefsChip` 위젯, `kPurposeOptions`/`kDurationOptions`로 label 변환
- `HomeScreen(onShowAllBenefits:)`: "여행 혜택 전체보기" → main.dart에서 `setState(() => _currentIndex = 1)`

### 혜택 신청 패턴 (benefit_detail_screen.dart)
- `_ApplyButton` with `WidgetsBindingObserver`: 외부 URL 열기 → `_waitingForReturn=true` → 앱 복귀(resumed) 감지 → 400ms 후 다이얼로그
- `is_repeatable=true`: 버튼 항상 활성, "총 N회 이용 기록" 표시 (BenefitReport의 benefitId로 카운트)
- `is_repeatable=false`: 완료 후 비활성 회색 버튼 "신청 완료 ✓"
- DB 마이그레이션: V11(korail_spring apply_url), V12(is_repeatable 컬럼 + sale_festa/gift_voucher/korail_spring=1)

### 지역사랑 휴가지원 크롤링 (VacationSupportSyncScheduler)
- **자동 스케줄**: 매일 오전 6시 (한국 시간) 실행
  - `@Scheduled(cron = "0 0 6 * * *", zone = "Asia/Seoul")`
  - `VacationSupportCrawler.crawl()` → visitkorea 페이지 크롤링
- **크롤링 로직** (VacationSupportCrawler):
  - 대상: `https://korean.visitkorea.or.kr/dgtourcard/tour50.do`
  - Jsoup 사용, userAgent 설정 (브라우저 차단 회피)
  - href에 `func_go_detail` 포함하는 `<a>` 태그 추출
  - 링크 텍스트에서 지역명 + 상태 파싱 (예: "밀양신청접수중" → "밀양": "신청접수중")
  - 인식 상태: "신청접수중" | "준비중" | "마감"
- **DB 업데이트** (VacationSupportSyncScheduler.sync()):
  - Benefit(id="vacation_support")의 detailJson 조회
  - regions[].status 필드를 크롤링 결과로 업데이트
  - 실패 시: 로그만 남기고 기존 데이터 유지 (신뢰성 보장)
- **수동 동기화**: `POST /api/benefits/sync/vacation-support` (관리자용)
- **detailJson 구조**:
  ```json
  {
    "regions": [
      { "name": "경남 밀양", "status": "신청접수중", "link": "..." },
      { "name": "부산 해운대", "status": "마감", "link": "..." }
    ]
  }
  ```

### 주변 관광지 검색 범위
- 백엔드: `SPOT_RADIUS_METER = 20000` (20km)
- TourApiClient `fetchNearby`: `Math.min(numOfRows, 1000)` (기존 100 → 1000)
- Flutter `nearby_spots_screen.dart`: `limit: 300`으로 호출

### 화면 분석 시 파악 순서
1. 더미/하드코딩 데이터 위치 확인
2. 해당 백엔드 엔드포인트 확인 (UserDataController 등)
3. UserDataService 메서드 확인
4. UI 위젯에 데이터 연결
