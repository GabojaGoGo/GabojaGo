# 가보자GO — 프론트엔드 코딩 규칙

---

## 1. 폴더 구조 — feature-first

백엔드 도메인 구조(`auth/`, `spot/`, `course/` …)와 동일한 방식으로 프론트도 기능 단위로 묶는다.

```
lib/
├── main.dart
│
├── core/                    # 앱 전체 공통 (백엔드의 global/)
│   ├── theme/
│   │   ├── app_theme.dart   # kAppGradient, kPrimaryColor 등
│   │   └── app_colors.dart  # AppColors 클래스 (색상 상수 통합)
│   ├── services/
│   │   ├── auth_service.dart
│   │   └── user_data_service.dart
│   └── models/
│       └── user_prefs.dart
│
├── features/                # 기능 단위 묶음 (백엔드의 각 도메인 폴더)
│   ├── auth/
│   │   ├── splash_screen.dart
│   │   ├── login_screen.dart
│   │   └── travel_setup_screen.dart
│   │
│   ├── home/
│   │   └── home_screen.dart
│   │
│   ├── spot/
│   │   ├── nearby_spots_screen.dart
│   │   ├── spot_detail_screen.dart
│   │   └── widgets/
│   │       ├── spot_card.dart
│   │       ├── spot_map_view.dart
│   │       └── spot_marker_builder.dart
│   │
│   ├── course/
│   │   ├── planner_screen.dart
│   │   ├── course_loading_screen.dart
│   │   ├── course_result_screen.dart
│   │   ├── course_detail_screen.dart
│   │   └── widgets/
│   │       └── course_card.dart
│   │
│   ├── benefit/
│   │   ├── subsidy_screen.dart
│   │   └── benefit_detail_screen.dart
│   │
│   └── my_trip/
│       └── my_trip_screen.dart
│
└── infrastructure/          # 외부 API 클라이언트 (백엔드의 infrastructure/)
    ├── api_service.dart
    └── tour_api_service.dart
```

**배치 판단 기준:**
- 이 feature에서만 쓰는 위젯 → 해당 `features/xxx/widgets/`
- 2개 이상 feature에서 쓰는 위젯 → `core/widgets/`
- 외부 서버 / API 호출 코드 → `infrastructure/`

---

## 2. 파일 분리 기준

**파일 하나 = 위젯(컴포넌트) 하나**

```
❌ 잘못된 예
nearby_spots_screen.dart  (1,454줄 — 지도, 마커, 카드, UI 전부)

✅ 좋은 예
features/spot/
├── nearby_spots_screen.dart     # 진입점, 조합만 담당
└── widgets/
    ├── spot_map_view.dart        # 지도 + 마커 관리
    ├── spot_card_carousel.dart   # 카드 목록 (PageView)
    └── spot_marker_builder.dart  # 마커 PNG 생성 유틸
```

---

## 3. StatelessWidget vs StatefulWidget

React의 "useState가 필요한가?"와 동일하게 판단한다.

```dart
// ✅ StatelessWidget — 데이터 받아서 그리기만 할 때
class SpotCard extends StatelessWidget {
  const SpotCard({ super.key, required this.spot });
  final SpotData spot;

  @override
  Widget build(BuildContext context) { ... }
}

// ✅ StatefulWidget — 내부 상태(로딩, 탭 선택 등)가 필요할 때
class SpotScreen extends StatefulWidget { ... }
class _SpotScreenState extends State<SpotScreen> { ... }
```

---

## 4. 상태 관리 규칙

| 상태 종류 | 방법 |
|----------|------|-----------|
| 버튼 눌림, 탭 선택 등 로컬 UI 상태 | `setState` |
| 화면 진입 시 API 데이터 로딩 | `initState` + `setState` |
| 로그인 정보 / 취향 등 앱 전역 상태 | `InheritedWidget` (`UserPrefsScope`) |

**금지 규칙:**
```
❌ 같은 화면에서 setState + FutureBuilder 혼용 금지
✅ 단순 데이터 로딩은 initState + setState 패턴으로 통일
```

**mounted 체크 필수** (비동기 완료 후 setState 전에 항상 확인):
```dart
if (mounted) setState(() { _data = result; });
```

---

## 5. 타입 규칙

### 5-1. `dynamic` 금지

`dynamic`은 타입 검사를 완전히 포기하는 것. 어디서도 사용하지 않는다.

```dart
// ❌ 금지
static Future<List<dynamic>> getSpots() async { ... }

// ✅ 모델 클래스 + 반환 타입 명시
static Future<List<SpotData>> getSpots() async { ... }
```

### 5-2. `as` 캐스팅 — JSON 파싱 경계에서만 허용

`as`는 `fromJson` 내부에서 JSON → 모델 변환 시에만 허용한다.  
앱 내부 코드(위젯, 서비스 로직)에서 `as`가 등장하면 모델 설계를 다시 검토한다.

```dart
// ✅ 허용 — fromJson 내부, JSON 파싱 경계
factory SpotData.fromJson(Map<String, dynamic> json) => SpotData(
  id:    json['id']    as String,           // 필드가 반드시 있는 경우
  lat:   (json['lat']  as num).toDouble(),  // num → double 변환
  items: (json['items'] as List)
             .map((e) => Item.fromJson(e as Map<String, dynamic>))
             .toList(),
);

// ❌ 금지 — 앱 내부 코드에서 as 사용
final spot = data as SpotData;
final title = someWidget as Text;
```

### 5-3. Nullable 최소화

nullable(`?`)은 **JSON 파싱에서 해당 필드가 진짜로 없을 수 있을 때**만 허용한다.  
모델 클래스 경계에서 `??`로 기본값을 확정하고, 앱 내부에서는 non-nullable로 흐른다.

```dart
// ✅ 모델 경계에서 nullable 해소 — 내부에서는 String으로 사용
factory SpotData.fromJson(Map<String, dynamic> json) => SpotData(
  title:   json['title']   as String? ?? '',     // 없을 수 있으면 기본값 확정
  address: json['address'] as String? ?? '',
  imageUrl: json['firstimage'] as String? ?? '',
);

// ❌ nullable을 앱 내부로 전파하지 않는다
class SpotData {
  final String? title;   // X — 모델에서 이미 확정했어야 함
  final String title;    // O
}

// ❌ 위젯에서 null 체크 반복
Text(spot.title ?? '이름 없음')  // 모델에서 이미 처리됐어야 함
Text(spot.title)                 // O
```

**예외 — nullable을 모델에 두는 경우:**  
의미적으로 "없음"과 "빈 값"을 구분해야 할 때만 허용한다.
```dart
// 좌표가 없는 경우와 (0, 0)을 구분해야 하므로 nullable 허용
final double? lat;
final double? lng;
```

---

## 6. API 호출 규칙

**화면(Screen)에서 직접 `http` 호출 금지. 반드시 `infrastructure/` 서비스 경유.**

```
화면 → infrastructure/service → http
```

**에러 처리 표준 패턴** (모든 API 메서드 동일하게):
```dart
static Future<T> _get(String url) async {
  try {
    final res = await http.get(Uri.parse(url))
        .timeout(const Duration(seconds: 20));
    if (res.statusCode == 200) {
      return parseResponse(res);
    }
    throw Exception('${res.statusCode}');
  } catch (e) {
    debugPrint('[ServiceName] error: $e');
    rethrow;
  }
}
```

---

## 7. 네이밍 규칙

| 대상 | 규칙 | 예시 |
|------|------|------|
| 파일명 | `snake_case` | `spot_card.dart` |
| 클래스 / 위젯 | `PascalCase` | `SpotCard`, `NearbySpotScreen` |
| 변수 / 함수 | `camelCase` | `isLoading`, `fetchData()` |
| private 멤버 | `_` 접두사 | `_isLoading`, `_fetchData()` |
| 파일 내 전용 상수 | `_kCamelCase` | `_kCardHeight` |
| 공유 상수 | `kCamelCase` | `kPrimaryColor` |
| 색상 | `AppColors.xxx` | `AppColors.primary`, `AppColors.congestion['낮음']` |

---

## 8. 색상 상수 — 반드시 `AppColors` 사용

색상을 파일마다 따로 정의하지 않는다. 모두 `core/theme/app_colors.dart`에서 관리.

```dart
// ❌ 금지 — 각 파일에 흩어진 색상 정의
const _kPrimary = Color(0xFF2E7D6B);
color: const Color(0xFF1B8C6E),

// ✅ AppColors에서 가져오기
color: AppColors.primary,
color: AppColors.congestion['낮음'],
```

---

## 9. 주석 규칙

코드만 봐서 알 수 있는 내용은 주석 달지 않는다.  
**"왜(WHY)"가 명확하지 않을 때만** 한 줄 주석 작성. 한글 허용.

```dart
// ❌ 불필요한 주석
// 로딩 상태를 true로 변경
setState(() => _isLoading = true);

// ✅ 필요한 주석 — 이유가 비명시적일 때
// 카카오맵 SDK가 마커 이미지를 PNG Uint8List로만 받아서 직접 인코딩
final markerBytes = await _buildMarkerPng(spot);
```
