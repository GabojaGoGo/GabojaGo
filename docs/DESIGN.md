# DESIGN.md — 가보자GO (TripMate)

> Airbnb 디자인 철학을 기반으로 한 국내 여행 코스 추천 앱 디자인 시스템.  
> 따뜻하고 친근한 분위기, 사진 주도 레이아웃, 8px 그리드 기반.

---

## 1. 비주얼 테마 & 분위기

**핵심 가치:** 설렘, 신뢰, 친근함  
**분위기 키워드:** 따뜻한 자연광 / 여행 사진 중심 / 깔끔하고 여백이 넉넉한 / 한국적 감성

- 콘텐츠(사진·지도)가 UI를 지배하고, 크롬은 최소화
- 여행지 이미지가 항상 카드의 시각적 주인공
- 텍스트는 읽기 쉽고 층위가 명확하게 — 제목·부제·캡션 3단계
- 밝은 흰/회색 배경으로 이미지 색상이 돋보이게

---

## 2. 컬러 팔레트

### 브랜드 컬러
| 역할 | 값 | 사용처 |
|------|-----|--------|
| Primary | `Color(0xFF2E7D6B)` | CTA 버튼, 액센트, 선택 상태 |
| Primary Container | Material 3 자동 생성 (밝은 민트) | 칩 선택, 배지 배경 |
| On Primary | `Colors.white` | Primary 위 텍스트/아이콘 |

### 배경 & 서피스
| 역할 | 값 | 사용처 |
|------|-----|--------|
| Scaffold BG | `Color(0xFFF8F9FA)` | 모든 화면 배경 |
| Card / Sheet | `Colors.white` | 카드, 바텀시트, 다이얼로그 |
| Divider / Border | `Color(0xFFE8EAED)` | 구분선, 카드 테두리 |
| Surface Dim | `Color(0xFFF2F3F5)` | 비활성 카드, 스켈레톤 배경 |

### 텍스트
| 역할 | 값 | 사용처 |
|------|-----|--------|
| Primary Text | `Color(0xFF1A1A1A)` | 제목, 본문 |
| Secondary Text | `Color(0xFF6B7280)` | 부제목, 메타 정보 |
| Caption / Hint | `Color(0xFF9CA3AF)` | 플레이스홀더, 캡션 |
| Link / Action | `Color(0xFF2E7D6B)` | 인터랙션 텍스트 |

### 시맨틱
| 역할 | 값 | 사용처 |
|------|-----|--------|
| 혼잡 — 여유 | `Color(0xFF2E7D6B)` | 혼잡도 Chip |
| 혼잡 — 보통 | `Color(0xFFF59E0B)` | 혼잡도 Chip |
| 혼잡 — 혼잡 | `Color(0xFFEF4444)` | 혼잡도 Chip |
| 슬롯 — 관광 | `Color(0xFF2E7D6B)` | 코스 슬롯 아이콘 |
| 슬롯 — 식사 | `Color(0xFFF97316)` | 코스 슬롯 아이콘 |
| 슬롯 — 숙박 | `Color(0xFF8B5CF6)` | 코스 슬롯 아이콘 |

---

## 3. 타이포그래피

Flutter `TextStyle` 기준. 시스템 폰트 사용 (iOS: SF Pro / Android: Roboto → Noto Sans KR 계열).

| 용도 | fontSize | fontWeight | letterSpacing | 사용처 |
|------|----------|-----------|---------------|--------|
| Display | 28 | w700 | -0.5 | 히어로 헤딩, 로딩 화면 |
| Headline | 22 | w700 | -0.3 | 화면 제목, 코스명 |
| Title | 18 | w600 | -0.2 | 섹션 헤더, 카드 제목 |
| Body Large | 16 | w500 | 0 | 카드 설명, 리스트 항목 |
| Body | 14 | w400 | 0 | 일반 본문 |
| Caption | 12 | w400 | 0.1 | 메타 정보, 날짜, 거리 |
| Label | 11 | w500 | 0.3 | Chip 텍스트, 배지 |

**규칙:**
- Heading 계열은 항상 음수 letterSpacing (텍스트를 더 따뜻하고 친밀하게)
- 한국어 본문: `height: 1.6` (행간 넉넉하게)
- 영문 숫자 혼용 시 `fontFeatures: [FontFeature.tabularFigures()]`

---

## 4. 컴포넌트 스타일

### 카드 (SpotCard, CourseCard, FestivalCard)
```dart
// 기본 카드
decoration: BoxDecoration(
  color: Colors.white,
  borderRadius: BorderRadius.circular(16),  // Airbnb: 20px → Flutter 16 적용
  boxShadow: [
    BoxShadow(color: Color(0x05000000), blurRadius: 0, spreadRadius: 1),  // 테두리 대체
    BoxShadow(color: Color(0x0A000000), blurRadius: 8, offset: Offset(0, 2)),
    BoxShadow(color: Color(0x14000000), blurRadius: 16, offset: Offset(0, 4)),
  ],
)

// 이미지 영역 (카드 상단)
borderRadius: BorderRadius.vertical(top: Radius.circular(16))
aspectRatio: 16/10  // 관광지 이미지 기본 비율
```

### 버튼
```dart
// Primary CTA (여행 시작하기, 코스 추천받기)
FilledButton: borderRadius 12, padding EdgeInsets.symmetric(h:24, v:14)
textStyle: fontSize 16, fontWeight w600

// Secondary (필터, 더보기)
OutlinedButton: borderRadius 10, borderColor Color(0xFFE8EAED)

// 텍스트 액션
TextButton: color Color(0xFF2E7D6B), fontWeight w600
```

### 칩 (취향, 카테고리, 혼잡도)
```dart
// 선택된 취향 칩
FilterChip: backgroundColor PrimaryContainer, selectedColor Primary
borderRadius: BorderRadius.circular(20)  // 완전 pill
padding: EdgeInsets.symmetric(h:12, v:8)
labelStyle: fontSize 13, fontWeight w500

// 슬롯 타입 칩
borderRadius: BorderRadius.circular(6)
padding: EdgeInsets.symmetric(h:6, v:2)
```

### AppBar
```dart
backgroundColor: Colors.white
foregroundColor: Color(0xFF1A1A1A)
elevation: 0
scrolledUnderElevation: 1  // 스크롤 시 미세한 구분선만
centerTitle: false  // 왼쪽 정렬 — Airbnb 스타일
titleStyle: fontSize 20, fontWeight w700
```

### BottomNavigationBar
```dart
backgroundColor: Colors.white
selectedItemColor: Color(0xFF2E7D6B)
unselectedItemColor: Color(0xFF9CA3AF)
elevation: 8  // 소프트 쉐도우
type: BottomNavigationBarType.fixed
```

### 입력 필드
```dart
InputDecoration(
  filled: true,
  fillColor: Color(0xFFF8F9FA),
  border: OutlineInputBorder(
    borderRadius: BorderRadius.circular(12),
    borderSide: BorderSide(color: Color(0xFFE8EAED)),
  ),
  focusedBorder: OutlineInputBorder(
    borderRadius: BorderRadius.circular(12),
    borderSide: BorderSide(color: Color(0xFF2E7D6B), width: 2),
  ),
)
```

### 히어로 헤더 (SliverAppBar)
```dart
// 코스 결과, 상세 화면
gradient: LinearGradient(
  begin: Alignment.topLeft,
  end: Alignment.bottomRight,
  colors: [colorScheme.primary, colorScheme.tertiary],
)
expandedHeight: 160~200
```

---

## 5. 레이아웃 & 간격

### 8px 기반 간격 스케일
| 토큰 | 값 | 사용처 |
|------|-----|--------|
| xs | 4px | 아이콘-텍스트 사이 |
| sm | 8px | 칩 내부 패딩, 리스트 아이템 간격 |
| md | 12px | 카드 내 패딩 |
| base | 16px | 화면 수평 여백, 섹션 내 항목 간격 |
| lg | 24px | 섹션 헤더 아래 여백 |
| xl | 32px | 섹션 간 여백 |
| 2xl | 48px | 화면 상단 여백 |

### 화면 수평 마진
- 기본: `padding: EdgeInsets.symmetric(horizontal: 16)`
- 풀블리드 (지도, 이미지): 0
- 카드 리스트: `padding: EdgeInsets.all(16)`

### 가로 스크롤 카드 (홈 주변관광지)
```dart
height: 220  // SpotCard 가로 스크롤
padding: EdgeInsets.only(left: 16, right: 4)
itemWidth: 160
spacing: 12  // 카드 사이 간격
```

### DAY 구분 헤더 (코스 상세)
```dart
// _DayDivider
color: PrimaryContainer
padding: EdgeInsets.symmetric(h:16, v:12)
textStyle: fontWeight w700, color Primary
```

---

## 6. 깊이 & 엘리베이션

Airbnb의 3레이어 소프트 쉐도우를 Flutter BoxShadow로 번역:

```dart
// Level 0 — 구분선 대체 (카드 테두리)
BoxShadow(color: Color(0x05000000), blurRadius: 0, spreadRadius: 1)

// Level 1 — 기본 카드
BoxShadow(color: Color(0x0A000000), blurRadius: 8, offset: Offset(0, 2))

// Level 2 — 강조 카드, 모달
BoxShadow(color: Color(0x14000000), blurRadius: 16, offset: Offset(0, 4))
BoxShadow(color: Color(0x1E000000), blurRadius: 24, offset: Offset(0, 8))
```

**규칙:**
- `elevation: 0` + BoxShadow 직접 지정 (Material elevation 피함)
- 바텀시트: Level 2 + `topLeft/topRight: Radius.circular(20)`
- 다이얼로그: Level 2 + `Radius.circular(16)`
- 카드에 절대 날카로운 테두리(solid 1px) 쓰지 않기 → 쉐도우로 대체

---

## 7. 디자인 가이드라인 (Do / Don't)

### ✅ Do
- 여행지 이미지는 항상 `fit: BoxFit.cover`, 16:10 비율 유지
- 취향 칩은 pill 형태(`BorderRadius.circular(20)`)
- 섹션 제목은 항상 좌측 정렬, fontWeight w700
- 로딩 상태에는 shimmer 스켈레톤 또는 CircularProgressIndicator (Primary 색)
- 빈 상태(Empty State)엔 일러스트 + 안내 텍스트 + CTA 버튼 세트
- DAY 구분은 PrimaryContainer 배경의 헤더로 명확히 구분
- 혼잡도 배지는 항상 카드 상단 오른쪽 Positioned

### ❌ Don't
- 하드코딩된 `Colors.blue` / `Colors.red` 사용 — 항상 colorScheme 토큰
- `elevation: 4` 이상의 Material 엘리베이션 — BoxShadow 직접 사용
- 카드에 색 배경 (흰색 유지) — 그라디언트는 히어로 헤더에만
- `fontWeight: w400` 이하 제목 — 최소 w500
- 이미지 없는 카드에 빈 회색 사각형만 표시 — 아이콘 + 텍스트 플레이스홀더
- 지나치게 촘촘한 spacing — 최소 8px 간격 유지
- 취향 설정 화면에서 `selectedColor`가 Primary 아닌 색 사용

---

## 8. 반응형 & 플랫폼 규칙

- **기본 타깃**: 모바일 세로 (375px ~ 430px 폭)
- `MediaQuery.of(context).size.width` 기반 조건부 레이아웃 삼가고, `Flexible` / `Expanded` 우선
- 이미지 로딩: `CachedNetworkImage` + `fit: BoxFit.cover` + `placeholder` 필수
- 안전 영역: `SafeArea` 또는 `MediaQuery.padding` 고려 (notch, home indicator)
- 텍스트 스케일: `textScaler` 재정의 금지 — 접근성 존중

---

## 9. AI 에이전트 프롬프트 가이드

새 화면/컴포넌트 생성 시 아래 체크리스트를 따를 것:

```
1. Scaffold backgroundColor: Color(0xFFF8F9FA)
2. 카드는 흰 배경 + 3레이어 BoxShadow + borderRadius 16
3. 이미지 영역은 16:10 비율, borderRadius 상단만
4. 버튼 Primary: FilledButton, borderRadius 12
5. 섹션 헤더: fontSize 18, fontWeight w700, leftPadding 16
6. 간격 단위: 4/8/12/16/24/32 — 다른 숫자 쓰지 않기
7. colorScheme.primary = 0xFF2E7D6B (teal-green)
8. 텍스트 Primary: 0xFF1A1A1A / Secondary: 0xFF6B7280
9. 로딩 중: Shimmer 스켈레톤 또는 Center(CircularProgressIndicator)
10. AppBar: centerTitle false, elevation 0, scrolledUnderElevation 1
```

**스크린별 레이아웃 패턴:**
- 홈 화면: `CustomScrollView` + `SliverList` (섹션별)
- 코스 결과: `SliverAppBar(expandedHeight:160)` + 카드 그리드/리스트
- 코스 상세: 히어로 이미지 + `_DayDivider` + 슬롯 리스트
- 지도 화면: 풀스크린 KakaoMap + 하단 바텀시트
- 설정/취향: `ListView` + 섹션 구분 + pill 칩 멀티셀렉트
