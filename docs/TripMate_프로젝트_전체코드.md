# TripMate - 프로젝트 전체 코드

> **앱 이름**: TripMate (트립메이트)
> **목적**: 국내 여행자 대상 — 여행 혜택 안내 + AI 여행 플래너
> **플랫폼**: Flutter (Android/iOS)
> **상태**: UI 목업 (더미 데이터 기반)
> **생성일**: 2026-03-31

---

## 목차

1. [파일 구조](#파일-구조)
2. [pubspec.yaml](#pubspecyaml)
3. [lib/main.dart](#libmaindart)
4. [lib/widgets/benefit_chip.dart](#libwidgetsbenefit_chipdart)
5. [lib/widgets/spot_card.dart](#libwidgetsspot_carddart)
6. [lib/widgets/subsidy_banner.dart](#libwidgetssubsidy_bannerdart)
7. [lib/screens/home_screen.dart](#libscreenshome_screendart)
8. [lib/screens/subsidy_screen.dart](#libscreenssubsidy_screendart)
9. [lib/screens/benefit_detail_screen.dart](#libscreensbenefit_detail_screendart)
10. [lib/screens/planner_screen.dart](#libscreensplanner_screendart)
11. [lib/screens/receipt_screen.dart](#libscreensreceipt_screendart)
12. [lib/screens/my_trip_screen.dart](#libscreensmy_trip_screendart)
13. [디자인 가이드](#디자인-가이드)

---

## 파일 구조

```
tripmate/
├── pubspec.yaml
├── ios/Runner/Info.plist          (Impeller 비활성화 설정 포함)
├── lib/
│   ├── main.dart                  # 앱 진입점, 테마 설정, BottomNavigationBar
│   ├── screens/
│   │   ├── home_screen.dart       # 홈 화면
│   │   ├── subsidy_screen.dart    # 혜택 목록 화면
│   │   ├── benefit_detail_screen.dart  # 혜택 상세 화면
│   │   ├── planner_screen.dart    # AI 여행 플래너 화면
│   │   ├── receipt_screen.dart    # 영수증 스캔 화면
│   │   └── my_trip_screen.dart    # 나의 여행 기록 화면
│   └── widgets/
│       ├── benefit_chip.dart      # 혜택/뱃지 칩 (재사용)
│       ├── spot_card.dart         # 여행지 카드 (재사용)
│       └── subsidy_banner.dart    # 보조금 배너 카드 (재사용)
└── test/
    └── widget_test.dart
```

---

## pubspec.yaml

```yaml
name: tripmate
description: "A new Flutter project."
publish_to: 'none'

version: 1.0.0+1

environment:
  sdk: ^3.11.4

dependencies:
  flutter:
    sdk: flutter
  cupertino_icons: ^1.0.8
  flutter_staggered_grid_view: ^0.7.0

dev_dependencies:
  flutter_test:
    sdk: flutter
  flutter_lints: ^6.0.0

flutter:
  uses-material-design: true
```

---

## lib/main.dart

```dart
// main.dart
// 앱 진입점 — Material 3 테마 설정 및 BottomNavigationBar 4탭 구성
// 탭: 홈 / 혜택 / 플래너 / 기록

import 'package:flutter/material.dart';
import 'screens/home_screen.dart';
import 'screens/subsidy_screen.dart';
import 'screens/planner_screen.dart';
import 'screens/my_trip_screen.dart';

void main() {
  runApp(const TripMateApp());
}

class TripMateApp extends StatelessWidget {
  const TripMateApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'TripMate',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        colorSchemeSeed: const Color(0xFF2E7D6B),
        appBarTheme: const AppBarTheme(
          backgroundColor: Colors.white,
          foregroundColor: Color(0xFF1A1A1A),
          elevation: 0,
          scrolledUnderElevation: 1,
          centerTitle: false,
          titleTextStyle: TextStyle(
            color: Color(0xFF1A1A1A),
            fontSize: 20,
            fontWeight: FontWeight.w700,
          ),
        ),
        scaffoldBackgroundColor: const Color(0xFFF8F9FA),
        cardTheme: CardThemeData(
          elevation: 0,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
            side: BorderSide(color: Colors.grey),
          ),
          color: Colors.white,
        ),
      ),
      home: const MainShell(),
    );
  }
}

/// 메인 쉘 — BottomNavigationBar로 4개 탭 전환 관리
class MainShell extends StatefulWidget {
  const MainShell({super.key});

  @override
  State<MainShell> createState() => _MainShellState();
}

class _MainShellState extends State<MainShell> {
  int _currentIndex = 0;

  // 탭별 화면 목록 (IndexedStack으로 상태 유지)
  static const List<Widget> _screens = [
    HomeScreen(),
    SubsidyScreen(),
    PlannerScreen(),
    MyTripScreen(),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: IndexedStack(
        index: _currentIndex,
        children: _screens,
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _currentIndex,
        onDestinationSelected: (index) {
          setState(() => _currentIndex = index);
        },
        backgroundColor: Colors.white,
        indicatorColor: const Color(0xFF2E7D6B).withValues(alpha: 0.15),
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.home_outlined),
            selectedIcon: Icon(Icons.home, color: Color(0xFF2E7D6B)),
            label: '홈',
          ),
          NavigationDestination(
            icon: Icon(Icons.redeem_outlined),
            selectedIcon: Icon(Icons.redeem, color: Color(0xFF2E7D6B)),
            label: '혜택',
          ),
          NavigationDestination(
            icon: Icon(Icons.map_outlined),
            selectedIcon: Icon(Icons.map, color: Color(0xFF2E7D6B)),
            label: '플래너',
          ),
          NavigationDestination(
            icon: Icon(Icons.person_outline),
            selectedIcon: Icon(Icons.person, color: Color(0xFF2E7D6B)),
            label: '기록',
          ),
        ],
      ),
    );
  }
}
```

---

## lib/widgets/benefit_chip.dart

```dart
// benefit_chip.dart
// 재사용 가능한 혜택/뱃지 칩 위젯
// 색상과 텍스트를 파라미터로 받아 다양한 상태(마감, 잔여, 성공 등)에 활용

import 'package:flutter/material.dart';

class BenefitChip extends StatelessWidget {
  final String label;
  final Color backgroundColor;
  final Color textColor;
  final IconData? icon;

  const BenefitChip({
    super.key,
    required this.label,
    required this.backgroundColor,
    required this.textColor,
    this.icon,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: backgroundColor,
        borderRadius: BorderRadius.circular(20),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icon != null) ...[
            Icon(icon, size: 12, color: textColor),
            const SizedBox(width: 4),
          ],
          Text(
            label,
            style: TextStyle(
              color: textColor,
              fontSize: 11,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }
}
```

---

## lib/widgets/spot_card.dart

```dart
// spot_card.dart
// 여행지 카드 위젯 (가로 스크롤 섹션에서 재사용)
// 지역명, 혼잡도 뱃지, 대표 이미지 플레이스홀더를 표시

import 'package:flutter/material.dart';
import 'benefit_chip.dart';

/// TourAPI 응답 구조를 흉내낸 여행지 더미 데이터 모델
class SpotData {
  final String areaName;      // 지역명 (TourAPI: areacode 대응)
  final String spotName;      // 장소명 (TourAPI: title 대응)
  final String category;      // 카테고리 (TourAPI: cat1 대응)
  final String congestion;    // 혼잡도: '낮음' | '보통' | '높음'
  final Color placeholderColor;

  const SpotData({
    required this.areaName,
    required this.spotName,
    required this.category,
    required this.congestion,
    required this.placeholderColor,
  });
}

class SpotCard extends StatelessWidget {
  final SpotData spot;

  const SpotCard({super.key, required this.spot});

  Color get _congestionColor {
    switch (spot.congestion) {
      case '낮음':
        return const Color(0xFF1B8C6E);
      case '보통':
        return const Color(0xFFF57C00);
      default:
        return const Color(0xFFD84315);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 160,
      margin: const EdgeInsets.only(right: 12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.grey.shade200),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 이미지 플레이스홀더
          Container(
            height: 110,
            decoration: BoxDecoration(
              color: spot.placeholderColor.withValues(alpha: 0.2),
              borderRadius: const BorderRadius.vertical(top: Radius.circular(12)),
            ),
            child: Center(
              child: Icon(
                Icons.landscape_outlined,
                size: 40,
                color: spot.placeholderColor,
              ),
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(10),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  spot.spotName,
                  style: const TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w700,
                    color: Color(0xFF1A1A1A),
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 2),
                Text(
                  spot.areaName,
                  style: TextStyle(
                    fontSize: 11,
                    color: Colors.grey.shade600,
                  ),
                ),
                const SizedBox(height: 6),
                BenefitChip(
                  label: '혼잡도 ${spot.congestion}',
                  backgroundColor: _congestionColor.withValues(alpha: 0.1),
                  textColor: _congestionColor,
                  icon: Icons.people_outline,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
```

---

## lib/widgets/subsidy_banner.dart

```dart
// subsidy_banner.dart
// 보조금 배너 카드 위젯 (홈 화면, 보조금 화면에서 재사용)
// 제목, 부제, 상태 칩(마감 D-day / 잔여 예산), CTA 버튼 포함

import 'package:flutter/material.dart';
import 'benefit_chip.dart';

class SubsidyBanner extends StatelessWidget {
  final String title;
  final String subtitle;
  final String deadline;     // 예: "D-23 마감"
  final String budgetLeft;   // 예: "잔여 예산 68%"
  final VoidCallback? onTap;

  const SubsidyBanner({
    super.key,
    required this.title,
    required this.subtitle,
    required this.deadline,
    required this.budgetLeft,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: [Color(0xFF5C4AE3), Color(0xFF7B6CF0)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // 상태 칩 행
            Row(
              children: [
                BenefitChip(
                  label: deadline,
                  backgroundColor: const Color(0xFFD84315).withValues(alpha: 0.9),
                  textColor: Colors.white,
                  icon: Icons.timer_outlined,
                ),
                const SizedBox(width: 8),
                BenefitChip(
                  label: budgetLeft,
                  backgroundColor: const Color(0xFF1B8C6E).withValues(alpha: 0.85),
                  textColor: Colors.white,
                  icon: Icons.account_balance_wallet_outlined,
                ),
              ],
            ),
            const SizedBox(height: 12),
            // 제목
            Text(
              title,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 18,
                fontWeight: FontWeight.w800,
                height: 1.3,
              ),
            ),
            const SizedBox(height: 6),
            // 부제
            Text(
              subtitle,
              style: TextStyle(
                color: Colors.white.withValues(alpha: 0.85),
                fontSize: 13,
              ),
            ),
            const SizedBox(height: 16),
            // CTA 버튼
            SizedBox(
              width: double.infinity,
              child: ElevatedButton(
                onPressed: onTap,
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.white,
                  foregroundColor: const Color(0xFF5C4AE3),
                  elevation: 0,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(10),
                  ),
                  padding: const EdgeInsets.symmetric(vertical: 12),
                ),
                child: const Text(
                  '신청 가이드 보기',
                  style: TextStyle(fontWeight: FontWeight.w700, fontSize: 14),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
```

---

## lib/screens/home_screen.dart

```dart
// home_screen.dart
// 홈 화면 — 인사 텍스트, 보조금 배너, 숙박세일페스타 배너,
// 한산한 여행지 가로 스크롤 카드, 이번 주 근처 축제 리스트

import 'package:flutter/material.dart';
import '../widgets/subsidy_banner.dart';
import '../widgets/spot_card.dart';
import '../widgets/benefit_chip.dart';

// ─────────────────────────────────────────────
// 더미 데이터 (TourAPI 응답 구조 흉내)
// ─────────────────────────────────────────────

/// 한산한 여행지 더미 데이터
const List<SpotData> _quietSpots = [
  SpotData(
    areaName: '강원 홍천',
    spotName: '홍천 강변 오토캠핑장',
    category: 'A03',  // TourAPI cat1: 레포츠
    congestion: '낮음',
    placeholderColor: Color(0xFF2E7D6B),
  ),
  SpotData(
    areaName: '전남 고흥',
    spotName: '나로도 우주센터',
    category: 'A02',  // TourAPI cat1: 문화시설
    congestion: '낮음',
    placeholderColor: Color(0xFF1565C0),
  ),
  SpotData(
    areaName: '경북 의성',
    spotName: '의성 산운 생태공원',
    category: 'A01',  // TourAPI cat1: 자연
    congestion: '낮음',
    placeholderColor: Color(0xFF558B2F),
  ),
];

/// 이번 주 근처 축제 더미 데이터
const List<Map<String, String>> _festivals = [
  {
    'title': '홍천 강 축제',                  // TourAPI: title
    'area': '강원 홍천',                      // TourAPI: addr1
    'date': '2026.04.05 ~ 04.07',            // TourAPI: eventstartdate
    'category': '문화관광축제',               // TourAPI: cat2
  },
  {
    'title': '의성 마늘 한우 축제',
    'area': '경북 의성',
    'date': '2026.04.10 ~ 04.12',
    'category': '음식특산물축제',
  },
];

// ─────────────────────────────────────────────
// 홈 화면 위젯
// ─────────────────────────────────────────────
class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Row(
          children: [
            Text('🧭', style: TextStyle(fontSize: 20)),
            SizedBox(width: 6),
            Text('TripMate'),
          ],
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.notifications_outlined),
            onPressed: () {},
          ),
        ],
      ),
      body: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // 인사 텍스트
            const Padding(
              padding: EdgeInsets.fromLTRB(16, 20, 16, 8),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '안녕하세요,',
                    style: TextStyle(
                      fontSize: 22,
                      fontWeight: FontWeight.w800,
                      color: Color(0xFF1A1A1A),
                    ),
                  ),
                  Text(
                    '어디로 떠나볼까요? ✈️',
                    style: TextStyle(
                      fontSize: 22,
                      fontWeight: FontWeight.w800,
                      color: Color(0xFF1A1A1A),
                    ),
                  ),
                ],
              ),
            ),

            // 보조금 메인 배너
            SubsidyBanner(
              title: '지역사랑 휴가지원\n여행비 50% 돌려받기',
              subtitle: '인구감소지역 20곳 · 개인 최대 10만원 환급',
              deadline: 'D-23 마감',
              budgetLeft: '잔여 예산 68%',
              onTap: () {},
            ),

            // 숙박세일페스타 작은 배너
            _SaleFestaMiniBanner(),

            const SizedBox(height: 24),

            // 지금 한산한 여행지 섹션
            const Padding(
              padding: EdgeInsets.symmetric(horizontal: 16),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    '지금 한산한 여행지',
                    style: TextStyle(
                      fontSize: 17,
                      fontWeight: FontWeight.w700,
                      color: Color(0xFF1A1A1A),
                    ),
                  ),
                  Text(
                    '전체보기',
                    style: TextStyle(
                      fontSize: 13,
                      color: Color(0xFF2E7D6B),
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 12),
            SizedBox(
              height: 210,
              child: ListView.builder(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.only(left: 16),
                itemCount: _quietSpots.length,
                itemBuilder: (context, index) {
                  return SpotCard(spot: _quietSpots[index]);
                },
              ),
            ),

            const SizedBox(height: 24),

            // 이번 주 근처 축제 섹션
            const Padding(
              padding: EdgeInsets.symmetric(horizontal: 16),
              child: Text(
                '이번 주 근처 축제',
                style: TextStyle(
                  fontSize: 17,
                  fontWeight: FontWeight.w700,
                  color: Color(0xFF1A1A1A),
                ),
              ),
            ),
            const SizedBox(height: 12),
            ..._festivals.map((f) => _FestivalListItem(festival: f)),
            const SizedBox(height: 32),
          ],
        ),
      ),
    );
  }
}

/// 숙박세일페스타 미니 배너
class _SaleFestaMiniBanner extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      decoration: BoxDecoration(
        color: const Color(0xFFFFF8E1),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: const Color(0xFFFFE082)),
      ),
      child: Row(
        children: [
          const Text('🏨', style: TextStyle(fontSize: 24)),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  '2026 봄 숙박세일페스타',
                  style: TextStyle(
                    fontWeight: FontWeight.w700,
                    fontSize: 14,
                    color: Color(0xFF5D4037),
                  ),
                ),
                Text(
                  '4월 1일 오픈 예정 · 최대 50% 할인',
                  style: TextStyle(
                    fontSize: 12,
                    color: Colors.brown.shade400,
                  ),
                ),
              ],
            ),
          ),
          BenefitChip(
            label: 'D-2',
            backgroundColor: const Color(0xFFFF8F00).withValues(alpha: 0.15),
            textColor: const Color(0xFFFF8F00),
          ),
        ],
      ),
    );
  }
}

/// 축제 리스트 아이템
class _FestivalListItem extends StatelessWidget {
  final Map<String, String> festival;
  const _FestivalListItem({required this.festival});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 5),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.grey.shade200),
      ),
      child: Row(
        children: [
          Container(
            width: 48,
            height: 48,
            decoration: BoxDecoration(
              color: const Color(0xFF2E7D6B).withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(10),
            ),
            child: const Icon(
              Icons.festival_outlined,
              color: Color(0xFF2E7D6B),
              size: 26,
            ),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  festival['title'] ?? '',
                  style: const TextStyle(
                    fontWeight: FontWeight.w700,
                    fontSize: 14,
                    color: Color(0xFF1A1A1A),
                  ),
                ),
                const SizedBox(height: 3),
                Text(
                  '${festival['area']} · ${festival['date']}',
                  style: TextStyle(
                    fontSize: 12,
                    color: Colors.grey.shade600,
                  ),
                ),
              ],
            ),
          ),
          BenefitChip(
            label: festival['category'] ?? '',
            backgroundColor: const Color(0xFF5C4AE3).withValues(alpha: 0.08),
            textColor: const Color(0xFF5C4AE3),
          ),
        ],
      ),
    );
  }
}
```

---

## lib/screens/subsidy_screen.dart

```dart
// subsidy_screen.dart
// 혜택 목록 화면 — 여행 관련 혜택 배너 카드 목록
// 각 카드 탭 시 benefit_detail_screen.dart로 이동

import 'package:flutter/material.dart';
import 'benefit_detail_screen.dart';
import '../widgets/benefit_chip.dart';

// ─────────────────────────────────────────────
// 혜택 더미 데이터
// ─────────────────────────────────────────────

/// 혜택 항목 모델 (실제 API 응답 구조 흉내)
class BenefitItem {
  final String id;
  final String title;
  final String subtitle;
  final String description;
  final String statusLabel;   // 칩 텍스트
  final StatusType statusType;
  final Color gradientStart;
  final Color gradientEnd;
  final IconData icon;
  final String category;      // 혜택 분류

  const BenefitItem({
    required this.id,
    required this.title,
    required this.subtitle,
    required this.description,
    required this.statusLabel,
    required this.statusType,
    required this.gradientStart,
    required this.gradientEnd,
    required this.icon,
    required this.category,
  });
}

enum StatusType { deadline, upcoming, ongoing, monthly }

/// 혜택 목록 더미 데이터
const List<BenefitItem> _benefits = [
  BenefitItem(
    id: 'vacation_support',
    title: '지역사랑 휴가지원',
    subtitle: '여행비 최대 50% 환급 · 인구감소지역 20곳',
    description: '지자체 협력 프로그램으로 인구감소지역 여행 시 숙박·식사·관광 비용의 50%를 지역화폐로 환급해 드립니다.',
    statusLabel: 'D-23 마감',
    statusType: StatusType.deadline,
    gradientStart: Color(0xFF5C4AE3),
    gradientEnd: Color(0xFF7B6CF0),
    icon: Icons.location_on_outlined,
    category: '정부지원',
  ),
  BenefitItem(
    id: 'sale_festa',
    title: '2026 봄 숙박세일페스타',
    subtitle: '전국 숙박 최대 50% 할인 · 4월 1일 오픈',
    description: '한국관광공사와 문화체육관광부가 함께하는 전국 숙박 할인 행사. 참여 숙소 수천 곳에서 특가 혜택을 누리세요.',
    statusLabel: '4/1 오픈 예정',
    statusType: StatusType.upcoming,
    gradientStart: Color(0xFFE65100),
    gradientEnd: Color(0xFFFF8F00),
    icon: Icons.hotel_outlined,
    category: '할인행사',
  ),
  BenefitItem(
    id: 'stay_korea',
    title: '대한민국 숙박 대전',
    subtitle: '온라인 여행사 연계 특가 · 상시 진행',
    description: '주요 온라인 여행사(OTA)와 협력하여 전국 숙박시설 특가를 상시 제공합니다. 앱 전용 추가 할인 쿠폰도 있어요.',
    statusLabel: '상시 진행 중',
    statusType: StatusType.ongoing,
    gradientStart: Color(0xFF1B8C6E),
    gradientEnd: Color(0xFF2E7D6B),
    icon: Icons.confirmation_number_outlined,
    category: '할인쿠폰',
  ),
  BenefitItem(
    id: 'gift_voucher',
    title: '온누리상품권 여행 환급',
    subtitle: '전통시장·지역상점 결제 시 월 최대 3만원',
    description: '여행지 전통시장 및 지역 소상공인 매장에서 온누리상품권으로 결제하면 매월 최대 3만원을 캐시백으로 돌려드립니다.',
    statusLabel: '월 최대 3만원',
    statusType: StatusType.monthly,
    gradientStart: Color(0xFF283593),
    gradientEnd: Color(0xFF3949AB),
    icon: Icons.savings_outlined,
    category: '캐시백',
  ),
];

// ─────────────────────────────────────────────
// 혜택 목록 화면 위젯
// ─────────────────────────────────────────────
class SubsidyScreen extends StatelessWidget {
  const SubsidyScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('여행 혜택'),
      ),
      body: ListView(
        padding: const EdgeInsets.symmetric(vertical: 8),
        children: [
          // 헤더 안내 문구
          const Padding(
            padding: EdgeInsets.fromLTRB(16, 12, 16, 16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '여행 혜택 한눈에 보기',
                  style: TextStyle(
                    fontSize: 20,
                    fontWeight: FontWeight.w800,
                    color: Color(0xFF1A1A1A),
                  ),
                ),
                SizedBox(height: 4),
                Text(
                  '정부·지자체·카드사 혜택을 모아 쉽게 확인하세요',
                  style: TextStyle(fontSize: 13, color: Colors.grey),
                ),
              ],
            ),
          ),

          // 혜택 배너 카드 목록
          ..._benefits.map((benefit) => _BenefitBannerCard(benefit: benefit)),

          const SizedBox(height: 24),
        ],
      ),
    );
  }
}

/// 혜택 배너 카드 위젯
class _BenefitBannerCard extends StatelessWidget {
  final BenefitItem benefit;
  const _BenefitBannerCard({required this.benefit});

  Color get _statusColor {
    switch (benefit.statusType) {
      case StatusType.deadline:
        return const Color(0xFFD84315);
      case StatusType.upcoming:
        return const Color(0xFFE65100);
      case StatusType.ongoing:
        return const Color(0xFF1B8C6E);
      case StatusType.monthly:
        return const Color(0xFF283593);
    }
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: () {
        Navigator.push(
          context,
          MaterialPageRoute(
            builder: (_) => BenefitDetailScreen(benefit: benefit),
          ),
        );
      },
      child: Container(
        margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: Colors.grey.shade200),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.04),
              blurRadius: 8,
              offset: const Offset(0, 2),
            ),
          ],
        ),
        child: Row(
          children: [
            // 좌측 아이콘 영역 (그라디언트 배경)
            Container(
              width: 88,
              height: 100,
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  colors: [benefit.gradientStart, benefit.gradientEnd],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
                borderRadius: const BorderRadius.horizontal(
                  left: Radius.circular(15),
                ),
              ),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(benefit.icon, color: Colors.white, size: 30),
                  const SizedBox(height: 6),
                  Container(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 6, vertical: 2),
                    decoration: BoxDecoration(
                      color: Colors.white.withValues(alpha: 0.25),
                      borderRadius: BorderRadius.circular(6),
                    ),
                    child: Text(
                      benefit.category,
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 10,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                ],
              ),
            ),
            // 우측 텍스트 영역
            Expanded(
              child: Padding(
                padding: const EdgeInsets.symmetric(
                    horizontal: 14, vertical: 14),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // 상태 칩
                    BenefitChip(
                      label: benefit.statusLabel,
                      backgroundColor: _statusColor.withValues(alpha: 0.1),
                      textColor: _statusColor,
                    ),
                    const SizedBox(height: 6),
                    // 제목
                    Text(
                      benefit.title,
                      style: const TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.w700,
                        color: Color(0xFF1A1A1A),
                      ),
                    ),
                    const SizedBox(height: 3),
                    // 부제
                    Text(
                      benefit.subtitle,
                      style: TextStyle(
                        fontSize: 12,
                        color: Colors.grey.shade600,
                        height: 1.4,
                      ),
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ],
                ),
              ),
            ),
            // 화살표
            Padding(
              padding: const EdgeInsets.only(right: 12),
              child: Icon(
                Icons.chevron_right,
                color: Colors.grey.shade400,
                size: 20,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
```

---

## lib/screens/benefit_detail_screen.dart

```dart
// benefit_detail_screen.dart
// 혜택 상세 화면 — 배너 카드 탭 시 진입
// 히어로 헤더 + 혜택 ID별 상세 내용 분기 표시

import 'package:flutter/material.dart';
import 'subsidy_screen.dart' show BenefitItem;
import '../widgets/benefit_chip.dart';

// ─────────────────────────────────────────────
// 더미 데이터 (각 혜택별 상세)
// ─────────────────────────────────────────────

/// 휴가지원 대상 지역 더미 데이터
const List<Map<String, dynamic>> _subsidyRegions = [
  {
    'areaCode': '32',
    'sigunguCode': '10',
    'name': '강원 홍천',
    'budgetRate': 0.32,
    'maxAmount': 100000,
    'category': '자연/캠핑',
  },
  {
    'areaCode': '37',
    'sigunguCode': '24',
    'name': '경북 의성',
    'budgetRate': 0.61,
    'maxAmount': 80000,
    'category': '농촌체험',
  },
  {
    'areaCode': '38',
    'sigunguCode': '13',
    'name': '전남 고흥',
    'budgetRate': 0.75,
    'maxAmount': 100000,
    'category': '해양/우주',
  },
  {
    'areaCode': '35',
    'sigunguCode': '14',
    'name': '충남 서천',
    'budgetRate': 0.88,
    'maxAmount': 60000,
    'category': '생태/갯벌',
  },
  {
    'areaCode': '39',
    'sigunguCode': '05',
    'name': '전북 진안',
    'budgetRate': 0.54,
    'maxAmount': 80000,
    'category': '산악/힐링',
  },
];

/// 신청 방법 4단계
const List<Map<String, String>> _steps = [
  {
    'step': '01',
    'title': '사전 신청',
    'desc': '여행 전 해당 지자체 홈페이지 또는 앱에서 신청·승인을 받으세요.',
    'icon': 'assignment',
  },
  {
    'step': '02',
    'title': '여행 실행',
    'desc': '승인된 지역에서 숙박, 관광, 식사 등 여행을 즐기세요.',
    'icon': 'flight_takeoff',
  },
  {
    'step': '03',
    'title': '영수증 제출',
    'desc': '앱 내 OCR 스캔으로 영수증을 간편하게 제출하세요.',
    'icon': 'receipt_long',
  },
  {
    'step': '04',
    'title': '지역화폐 환급',
    'desc': '심사 완료 후 지역화폐 또는 현금으로 환급됩니다 (영업일 7일 이내).',
    'icon': 'savings',
  },
];

// ─────────────────────────────────────────────
// 혜택 상세 화면 위젯
// ─────────────────────────────────────────────
class BenefitDetailScreen extends StatelessWidget {
  final BenefitItem benefit;
  const BenefitDetailScreen({super.key, required this.benefit});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF8F9FA),
      body: CustomScrollView(
        slivers: [
          // 히어로 헤더 (그라디언트 AppBar)
          SliverAppBar(
            expandedHeight: 200,
            pinned: true,
            backgroundColor: benefit.gradientStart,
            iconTheme: const IconThemeData(color: Colors.white),
            flexibleSpace: FlexibleSpaceBar(
              background: Container(
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    colors: [benefit.gradientStart, benefit.gradientEnd],
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                  ),
                ),
                child: SafeArea(
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(20, 48, 20, 20),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisAlignment: MainAxisAlignment.end,
                      children: [
                        BenefitChip(
                          label: benefit.statusLabel,
                          backgroundColor: Colors.white.withValues(alpha: 0.25),
                          textColor: Colors.white,
                        ),
                        const SizedBox(height: 8),
                        Text(
                          benefit.title,
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 22,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          benefit.subtitle,
                          style: TextStyle(
                            color: Colors.white.withValues(alpha: 0.85),
                            fontSize: 13,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ),

          // 본문 내용
          SliverToBoxAdapter(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // 혜택 설명 카드
                _DescriptionCard(benefit: benefit),

                // 혜택 ID별 상세 내용 분기
                _buildDetailContent(context),

                const SizedBox(height: 32),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildDetailContent(BuildContext context) {
    switch (benefit.id) {
      case 'vacation_support':
        return _VacationSupportDetail(context: context);
      case 'sale_festa':
        return _SaleFestaDetail();
      case 'stay_korea':
        return _StayKoreaDetail();
      case 'gift_voucher':
        return _GiftVoucherDetail();
      default:
        return const SizedBox.shrink();
    }
  }
}

/// 혜택 설명 공통 카드
class _DescriptionCard extends StatelessWidget {
  final BenefitItem benefit;
  const _DescriptionCard({required this.benefit});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.fromLTRB(16, 16, 16, 0),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.grey.shade200),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 42,
            height: 42,
            decoration: BoxDecoration(
              gradient: LinearGradient(
                colors: [benefit.gradientStart, benefit.gradientEnd],
              ),
              borderRadius: BorderRadius.circular(10),
            ),
            child: Icon(benefit.icon, color: Colors.white, size: 22),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              benefit.description,
              style: TextStyle(
                fontSize: 13,
                color: Colors.grey.shade700,
                height: 1.6,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

// ─────────────────────────────────────────────
// 1. 지역사랑 휴가지원 상세
// ─────────────────────────────────────────────
class _VacationSupportDetail extends StatelessWidget {
  final BuildContext context;
  const _VacationSupportDetail({required this.context});

  @override
  Widget build(BuildContext ctx) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const _SectionTitle(title: '지원 가능 지역'),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Text(
            '인구감소지역 20곳 중 현재 신청 가능한 지역',
            style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
          ),
        ),
        const SizedBox(height: 10),
        GridView.builder(
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          padding: const EdgeInsets.symmetric(horizontal: 16),
          gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
            crossAxisCount: 2,
            crossAxisSpacing: 10,
            mainAxisSpacing: 10,
            childAspectRatio: 1.2,
          ),
          itemCount: _subsidyRegions.length,
          itemBuilder: (_, index) =>
              _RegionCard(region: _subsidyRegions[index], parentContext: ctx),
        ),
        const _SectionTitle(title: '신청 방법'),
        ..._steps.asMap().entries.map((e) => _StepCard(
              step: e.value,
              isLast: e.key == _steps.length - 1,
            )),
      ],
    );
  }
}

/// 지역 카드 위젯
class _RegionCard extends StatelessWidget {
  final Map<String, dynamic> region;
  final BuildContext parentContext;
  const _RegionCard({required this.region, required this.parentContext});

  @override
  Widget build(BuildContext context) {
    final double rate = region['budgetRate'] as double;
    final int spentPercent = ((1 - rate) * 100).round();
    final Color progressColor =
        rate > 0.5 ? const Color(0xFF1B8C6E) : const Color(0xFFD84315);

    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.grey.shade200),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                region['name'] as String,
                style: const TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w700,
                  color: Color(0xFF1A1A1A),
                ),
              ),
              const SizedBox(height: 3),
              BenefitChip(
                label: region['category'] as String,
                backgroundColor:
                    const Color(0xFF2E7D6B).withValues(alpha: 0.08),
                textColor: const Color(0xFF2E7D6B),
              ),
              const SizedBox(height: 8),
              Text(
                '잔여 예산 ${(rate * 100).round()}%',
                style: TextStyle(fontSize: 11, color: Colors.grey.shade600),
              ),
              const SizedBox(height: 4),
              ClipRRect(
                borderRadius: BorderRadius.circular(4),
                child: LinearProgressIndicator(
                  value: rate,
                  backgroundColor: Colors.grey.shade200,
                  valueColor: AlwaysStoppedAnimation<Color>(progressColor),
                  minHeight: 6,
                ),
              ),
              const SizedBox(height: 2),
              Text(
                '$spentPercent% 소진',
                style: const TextStyle(
                    fontSize: 10, color: Color(0xFFD84315)),
              ),
            ],
          ),
          SizedBox(
            width: double.infinity,
            child: ElevatedButton(
              onPressed: () {
                ScaffoldMessenger.of(parentContext).showSnackBar(
                  SnackBar(
                    content: Text(
                        '${region['name']} 지자체 신청 페이지로 이동합니다'),
                    backgroundColor: const Color(0xFF5C4AE3),
                    behavior: SnackBarBehavior.floating,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(10),
                    ),
                  ),
                );
              },
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFF5C4AE3),
                foregroundColor: Colors.white,
                elevation: 0,
                padding: const EdgeInsets.symmetric(vertical: 6),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(8),
                ),
                textStyle: const TextStyle(
                    fontSize: 12, fontWeight: FontWeight.w700),
              ),
              child: const Text('신청하기'),
            ),
          ),
        ],
      ),
    );
  }
}

/// 신청 스텝 카드
class _StepCard extends StatelessWidget {
  final Map<String, String> step;
  final bool isLast;
  const _StepCard({required this.step, required this.isLast});

  IconData _iconData(String name) {
    switch (name) {
      case 'assignment':
        return Icons.assignment_outlined;
      case 'flight_takeoff':
        return Icons.flight_takeoff_outlined;
      case 'receipt_long':
        return Icons.receipt_long_outlined;
      case 'savings':
        return Icons.savings_outlined;
      default:
        return Icons.circle_outlined;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: IntrinsicHeight(
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Column(
              children: [
                Container(
                  width: 40,
                  height: 40,
                  decoration: BoxDecoration(
                    color: const Color(0xFF5C4AE3),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Center(
                    child: Text(
                      step['step']!,
                      style: const TextStyle(
                        color: Colors.white,
                        fontWeight: FontWeight.w800,
                        fontSize: 13,
                      ),
                    ),
                  ),
                ),
                if (!isLast)
                  Expanded(
                    child: Container(
                      width: 2,
                      color: const Color(0xFF5C4AE3).withValues(alpha: 0.2),
                    ),
                  ),
              ],
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Container(
                margin: EdgeInsets.only(bottom: isLast ? 0 : 12),
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: Colors.grey.shade200),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Icon(_iconData(step['icon']!),
                            size: 20, color: const Color(0xFF5C4AE3)),
                        const SizedBox(width: 8),
                        Text(
                          step['title']!,
                          style: const TextStyle(
                            fontSize: 14,
                            fontWeight: FontWeight.w700,
                            color: Color(0xFF1A1A1A),
                          ),
                        ),
                        if (step['step'] == '03') ...[
                          const Spacer(),
                          BenefitChip(
                            label: 'OCR 스캔',
                            backgroundColor: const Color(0xFF1B8C6E)
                                .withValues(alpha: 0.1),
                            textColor: const Color(0xFF1B8C6E),
                            icon: Icons.document_scanner_outlined,
                          ),
                        ],
                      ],
                    ),
                    const SizedBox(height: 6),
                    Text(
                      step['desc']!,
                      style: TextStyle(
                        fontSize: 13,
                        color: Colors.grey.shade600,
                        height: 1.4,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ─────────────────────────────────────────────
// 2. 숙박세일페스타 상세
// ─────────────────────────────────────────────
class _SaleFestaDetail extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        const SizedBox(height: 16),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Container(
            width: double.infinity,
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              color: const Color(0xFFFFF8E1),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: const Color(0xFFFFE082)),
            ),
            child: Column(
              children: [
                const Text('🏨', style: TextStyle(fontSize: 40)),
                const SizedBox(height: 8),
                const Text(
                  '4월 1일 오전 10:00 오픈 예정',
                  style: TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w700,
                    color: Color(0xFF5D4037),
                  ),
                ),
                const SizedBox(height: 16),
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    _TimeUnit(value: '01', label: '일'),
                    const _Colon(),
                    _TimeUnit(value: '13', label: '시간'),
                    const _Colon(),
                    _TimeUnit(value: '42', label: '분'),
                  ],
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 12),
        ...[
          _SimpleInfoCard(
            icon: Icons.percent_outlined,
            title: '최대 50% 할인 쿠폰',
            desc: '전국 참여 숙박업소에서 사용 가능한 할인 쿠폰을 선착순 제공합니다.',
            color: const Color(0xFFE65100),
          ),
          _SimpleInfoCard(
            icon: Icons.confirmation_number_outlined,
            title: '1인 1쿠폰 원칙',
            desc: '본인 인증 후 1인당 1매 발급. 유효기간 내 1회 사용 가능합니다.',
            color: const Color(0xFFE65100),
          ),
          _SimpleInfoCard(
            icon: Icons.notifications_active_outlined,
            title: '오픈 알림 받기',
            desc: '알림을 설정하면 오픈 30분 전 미리 안내해 드립니다.',
            color: const Color(0xFFE65100),
            actionLabel: '알림 설정',
          ),
        ],
      ],
    );
  }
}

// ─────────────────────────────────────────────
// 3. 대한민국 숙박 대전 상세
// ─────────────────────────────────────────────
class _StayKoreaDetail extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        ...[
          _SimpleInfoCard(
            icon: Icons.local_offer_outlined,
            title: '앱 전용 추가 10% 할인',
            desc: 'TripMate 앱을 통해 예약 시 제휴 숙소에서 추가 10% 할인이 적용됩니다.',
            color: const Color(0xFF1B8C6E),
          ),
          _SimpleInfoCard(
            icon: Icons.star_outline,
            title: '특급호텔 ~ 게스트하우스 전 범위',
            desc: '특1급 호텔부터 펜션, 게스트하우스까지 전국 5,000여 개 숙소가 참여합니다.',
            color: const Color(0xFF1B8C6E),
          ),
          _SimpleInfoCard(
            icon: Icons.calendar_today_outlined,
            title: '주중 추가 할인',
            desc: '월~목요일 체크인 시 주말 대비 최대 20% 추가 할인 혜택이 제공됩니다.',
            color: const Color(0xFF1B8C6E),
          ),
          _SimpleInfoCard(
            icon: Icons.phonelink_outlined,
            title: '즉시 예약 확정',
            desc: '앱 내 실시간 예약으로 즉시 확정. 무료 취소 정책 숙소도 다수 포함됩니다.',
            color: const Color(0xFF1B8C6E),
          ),
        ],
      ],
    );
  }
}

// ─────────────────────────────────────────────
// 4. 온누리상품권 여행 환급 상세
// ─────────────────────────────────────────────
class _GiftVoucherDetail extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        ...[
          _SimpleInfoCard(
            icon: Icons.store_outlined,
            title: '전통시장·지역 소상공인 대상',
            desc: '여행지 전통시장 및 온누리상품권 가맹 지역상점에서 결제 시 적용됩니다.',
            color: const Color(0xFF283593),
          ),
          _SimpleInfoCard(
            icon: Icons.calculate_outlined,
            title: '월 최대 3만원 캐시백',
            desc: '월 10만원 이상 결제 시 30% 캐시백. 캐시백은 익월 10일 지급됩니다.',
            color: const Color(0xFF283593),
          ),
          _SimpleInfoCard(
            icon: Icons.qr_code_outlined,
            title: 'QR·모바일 상품권 지원',
            desc: '앱에서 QR코드로 즉시 구매 및 결제. 잔액 이월도 가능합니다.',
            color: const Color(0xFF283593),
          ),
          _SimpleInfoCard(
            icon: Icons.map_outlined,
            title: '가맹점 지도 연동',
            desc: '여행지 근처 온누리상품권 가맹점을 지도에서 바로 확인할 수 있습니다.',
            color: const Color(0xFF283593),
            actionLabel: '가맹점 찾기',
          ),
        ],
      ],
    );
  }
}

// ─────────────────────────────────────────────
// 공통 보조 위젯
// ─────────────────────────────────────────────

/// 섹션 제목
class _SectionTitle extends StatelessWidget {
  final String title;
  const _SectionTitle({required this.title});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 24, 16, 10),
      child: Text(
        title,
        style: const TextStyle(
          fontSize: 17,
          fontWeight: FontWeight.w700,
          color: Color(0xFF1A1A1A),
        ),
      ),
    );
  }
}

/// 간단 안내 카드
class _SimpleInfoCard extends StatelessWidget {
  final IconData icon;
  final String title;
  final String desc;
  final Color color;
  final String? actionLabel;

  const _SimpleInfoCard({
    required this.icon,
    required this.title,
    required this.desc,
    required this.color,
    this.actionLabel,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.fromLTRB(16, 0, 16, 10),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.grey.shade200),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 42,
            height: 42,
            decoration: BoxDecoration(
              color: color.withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(10),
            ),
            child: Icon(icon, color: color, size: 22),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: const TextStyle(
                    fontWeight: FontWeight.w700,
                    fontSize: 14,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  desc,
                  style: TextStyle(
                    fontSize: 12,
                    color: Colors.grey.shade600,
                    height: 1.4,
                  ),
                ),
                if (actionLabel != null) ...[
                  const SizedBox(height: 8),
                  TextButton(
                    onPressed: () {},
                    style: TextButton.styleFrom(
                      padding: EdgeInsets.zero,
                      minimumSize: Size.zero,
                      tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                      foregroundColor: color,
                    ),
                    child: Text(
                      '$actionLabel →',
                      style: const TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// 카운트다운 시간 단위
class _TimeUnit extends StatelessWidget {
  final String value;
  final String label;
  const _TimeUnit({required this.value, required this.label});

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Container(
          width: 56,
          padding: const EdgeInsets.symmetric(vertical: 10),
          decoration: BoxDecoration(
            color: const Color(0xFFE65100).withValues(alpha: 0.1),
            borderRadius: BorderRadius.circular(10),
          ),
          child: Text(
            value,
            textAlign: TextAlign.center,
            style: const TextStyle(
              fontSize: 22,
              fontWeight: FontWeight.w800,
              color: Color(0xFFE65100),
            ),
          ),
        ),
        const SizedBox(height: 4),
        Text(label, style: const TextStyle(fontSize: 11, color: Colors.grey)),
      ],
    );
  }
}

class _Colon extends StatelessWidget {
  const _Colon();
  @override
  Widget build(BuildContext context) {
    return const Padding(
      padding: EdgeInsets.symmetric(horizontal: 6),
      child: Text(
        ':',
        style: TextStyle(
          fontSize: 20,
          fontWeight: FontWeight.w800,
          color: Color(0xFFE65100),
        ),
      ),
    );
  }
}
```

---

## lib/screens/planner_screen.dart

```dart
// planner_screen.dart
// 여행 플래너 화면 — 무드 선택 칩, 여행지 검색바, 추천 코스 카드, 날씨 배너
// 무드 칩 선택 시 setState로 추천 코스 더미 데이터 교체

import 'package:flutter/material.dart';
import '../widgets/benefit_chip.dart';
import 'receipt_screen.dart';

// ─────────────────────────────────────────────
// 더미 데이터 (TourAPI 코스 정보 구조 흉내)
// ─────────────────────────────────────────────

/// 무드 칩 목록
const List<Map<String, dynamic>> _moods = [
  {'label': '조용한 힐링', 'icon': Icons.spa_outlined},
  {'label': '활기찬 탐방', 'icon': Icons.directions_run_outlined},
  {'label': '맛집 투어', 'icon': Icons.restaurant_outlined},
  {'label': '자연 트레킹', 'icon': Icons.terrain_outlined},
  {'label': '역사 문화', 'icon': Icons.account_balance_outlined},
];

/// 무드별 추천 코스 더미 데이터 (TourAPI: contentid, contenttypeid 구조)
const Map<String, List<Map<String, dynamic>>> _moodCourses = {
  '조용한 힐링': [
    {
      'contentId': 'C001',            // TourAPI contentid
      'contentTypeId': '25',          // TourAPI contenttypeid (여행코스)
      'title': '홍천 강변 힐링 코스',
      'totalDistance': '12km',
      'estimatedTime': '4시간',
      'isSubsidyTarget': true,        // 보조금 대상 지역 여부
      'places': ['홍천강 오토캠핑장', '수타사', '홍천 자연휴양림'],
    },
    {
      'contentId': 'C002',
      'contentTypeId': '25',
      'title': '진안 마이산 명상 코스',
      'totalDistance': '8km',
      'estimatedTime': '3시간',
      'isSubsidyTarget': true,
      'places': ['마이산 탑사', '마이산 도립공원', '진안 고원 한옥마을'],
    },
  ],
  '활기찬 탐방': [
    {
      'contentId': 'C003',
      'contentTypeId': '25',
      'title': '제주 올레길 탐방 코스',
      'totalDistance': '20km',
      'estimatedTime': '6시간',
      'isSubsidyTarget': false,
      'places': ['성산일출봉', '섭지코지', '우도'],
    },
  ],
  '맛집 투어': [
    {
      'contentId': 'C004',
      'contentTypeId': '25',
      'title': '전주 한옥마을 미식 코스',
      'totalDistance': '5km',
      'estimatedTime': '3시간',
      'isSubsidyTarget': false,
      'places': ['전주 비빔밥 골목', '한옥마을 시장', '모악산 전통찻집'],
    },
    {
      'contentId': 'C005',
      'contentTypeId': '25',
      'title': '의성 마늘 향토 코스',
      'totalDistance': '15km',
      'estimatedTime': '4시간',
      'isSubsidyTarget': true,
      'places': ['의성 마늘 테마파크', '산운 생태공원', '의성 향토음식점'],
    },
  ],
  '자연 트레킹': [
    {
      'contentId': 'C006',
      'contentTypeId': '25',
      'title': '고흥 천등산 트레킹',
      'totalDistance': '18km',
      'estimatedTime': '6시간',
      'isSubsidyTarget': true,
      'places': ['천등산 정상', '팔영산 전망대', '나로도 해수욕장'],
    },
  ],
  '역사 문화': [
    {
      'contentId': 'C007',
      'contentTypeId': '25',
      'title': '서천 문화유산 탐방',
      'totalDistance': '10km',
      'estimatedTime': '4시간',
      'isSubsidyTarget': true,
      'places': ['서천 한산모시관', '국립생태원', '서천 성흥산성'],
    },
  ],
};

// ─────────────────────────────────────────────
// 플래너 화면 위젯
// ─────────────────────────────────────────────
class PlannerScreen extends StatefulWidget {
  const PlannerScreen({super.key});

  @override
  State<PlannerScreen> createState() => _PlannerScreenState();
}

class _PlannerScreenState extends State<PlannerScreen> {
  // 선택된 무드 (기본값: 조용한 힐링)
  String _selectedMood = '조용한 힐링';
  final TextEditingController _searchController = TextEditingController();

  // 현재 선택된 무드의 추천 코스
  List<Map<String, dynamic>> get _currentCourses =>
      _moodCourses[_selectedMood] ?? [];

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('AI 여행 플래너'),
        actions: [
          IconButton(
            icon: const Icon(Icons.tune_outlined),
            onPressed: () {},
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () {
          Navigator.push(
            context,
            MaterialPageRoute(builder: (_) => const ReceiptScreen()),
          );
        },
        icon: const Icon(Icons.receipt_long_outlined),
        label: const Text('영수증 스캔'),
        backgroundColor: const Color(0xFF1B8C6E),
        foregroundColor: Colors.white,
      ),
      body: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // 날씨 배너
            _WeatherBanner(),

            const Padding(
              padding: EdgeInsets.fromLTRB(16, 20, 16, 10),
              child: Text(
                '어떤 여행을 원하세요?',
                style: TextStyle(
                  fontSize: 17,
                  fontWeight: FontWeight.w700,
                  color: Color(0xFF1A1A1A),
                ),
              ),
            ),

            // 무드 선택 칩 가로 스크롤
            SizedBox(
              height: 44,
              child: ListView.builder(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.symmetric(horizontal: 16),
                itemCount: _moods.length,
                itemBuilder: (context, index) {
                  final mood = _moods[index];
                  final isSelected = _selectedMood == mood['label'];
                  return GestureDetector(
                    onTap: () {
                      setState(() {
                        _selectedMood = mood['label'] as String;
                      });
                    },
                    child: AnimatedContainer(
                      duration: const Duration(milliseconds: 200),
                      margin: const EdgeInsets.only(right: 8),
                      padding: const EdgeInsets.symmetric(
                          horizontal: 14, vertical: 8),
                      decoration: BoxDecoration(
                        color: isSelected
                            ? const Color(0xFF2E7D6B)
                            : Colors.white,
                        borderRadius: BorderRadius.circular(22),
                        border: Border.all(
                          color: isSelected
                              ? const Color(0xFF2E7D6B)
                              : Colors.grey.shade300,
                        ),
                      ),
                      child: Row(
                        children: [
                          Icon(
                            mood['icon'] as IconData,
                            size: 16,
                            color: isSelected
                                ? Colors.white
                                : Colors.grey.shade700,
                          ),
                          const SizedBox(width: 6),
                          Text(
                            mood['label'] as String,
                            style: TextStyle(
                              fontSize: 13,
                              fontWeight: FontWeight.w600,
                              color: isSelected
                                  ? Colors.white
                                  : Colors.grey.shade700,
                            ),
                          ),
                        ],
                      ),
                    ),
                  );
                },
              ),
            ),

            // 검색바
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
              child: TextField(
                controller: _searchController,
                decoration: InputDecoration(
                  hintText: '여행지, 지역명 검색...',
                  hintStyle: TextStyle(color: Colors.grey.shade400),
                  prefixIcon: const Icon(Icons.search, color: Colors.grey),
                  filled: true,
                  fillColor: Colors.white,
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                    borderSide: BorderSide(color: Colors.grey.shade200),
                  ),
                  enabledBorder: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                    borderSide: BorderSide(color: Colors.grey.shade200),
                  ),
                  focusedBorder: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                    borderSide:
                        const BorderSide(color: Color(0xFF2E7D6B), width: 1.5),
                  ),
                  contentPadding: const EdgeInsets.symmetric(vertical: 12),
                ),
              ),
            ),

            // 추천 코스 섹션 제목
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 24, 16, 12),
              child: Row(
                children: [
                  Text(
                    '"$_selectedMood" 추천 코스',
                    style: const TextStyle(
                      fontSize: 17,
                      fontWeight: FontWeight.w700,
                      color: Color(0xFF1A1A1A),
                    ),
                  ),
                  const SizedBox(width: 8),
                  BenefitChip(
                    label: '${_currentCourses.length}개',
                    backgroundColor: const Color(0xFF2E7D6B).withValues(alpha: 0.1),
                    textColor: const Color(0xFF2E7D6B),
                  ),
                ],
              ),
            ),

            // 추천 코스 카드 목록
            if (_currentCourses.isEmpty)
              const Padding(
                padding: EdgeInsets.all(32),
                child: Center(
                  child: Text(
                    '추천 코스를 불러오는 중...',
                    style: TextStyle(color: Colors.grey),
                  ),
                ),
              )
            else
              ..._currentCourses.map((course) => _CourseCard(course: course)),

            const SizedBox(height: 32),
          ],
        ),
      ),
    );
  }
}

/// 날씨 배너 위젯
class _WeatherBanner extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.fromLTRB(16, 16, 16, 0),
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      decoration: BoxDecoration(
        color: const Color(0xFFE3F2FD),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: const Color(0xFF90CAF9)),
      ),
      child: Row(
        children: [
          const Text('☀️', style: TextStyle(fontSize: 28)),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  '이번 주말 홍천 — 맑음 · 코스 유지',
                  style: TextStyle(
                    fontWeight: FontWeight.w700,
                    fontSize: 14,
                    color: Color(0xFF1565C0),
                  ),
                ),
                Text(
                  '최고 18°C · 최저 6°C · 강수확률 5%',
                  style: TextStyle(
                    fontSize: 12,
                    color: Colors.blue.shade600,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// 추천 코스 카드 위젯
class _CourseCard extends StatelessWidget {
  final Map<String, dynamic> course;
  const _CourseCard({required this.course});

  @override
  Widget build(BuildContext context) {
    final bool isSubsidy = course['isSubsidyTarget'] as bool;

    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: isSubsidy
              ? const Color(0xFF5C4AE3).withValues(alpha: 0.4)
              : Colors.grey.shade200,
          width: isSubsidy ? 1.5 : 1,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 보조금 대상 강조 배너
          if (isSubsidy)
            Container(
              width: double.infinity,
              padding:
                  const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
              decoration: BoxDecoration(
                color: const Color(0xFF5C4AE3).withValues(alpha: 0.07),
                borderRadius:
                    const BorderRadius.vertical(top: Radius.circular(11)),
              ),
              child: const Row(
                children: [
                  Icon(Icons.verified_outlined,
                      size: 15, color: Color(0xFF5C4AE3)),
                  SizedBox(width: 6),
                  Text(
                    '보조금 대상 지역 코스 · 최대 10만원 환급 가능',
                    style: TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                      color: Color(0xFF5C4AE3),
                    ),
                  ),
                ],
              ),
            ),
          Padding(
            padding: const EdgeInsets.all(14),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  course['title'] as String,
                  style: const TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.w700,
                    color: Color(0xFF1A1A1A),
                  ),
                ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    _InfoPill(
                      icon: Icons.straighten_outlined,
                      label: course['totalDistance'] as String,
                    ),
                    const SizedBox(width: 8),
                    _InfoPill(
                      icon: Icons.schedule_outlined,
                      label: course['estimatedTime'] as String,
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                ...(course['places'] as List<String>)
                    .asMap()
                    .entries
                    .map((e) => _PlaceItem(
                          index: e.key + 1,
                          name: e.value,
                          isLast: e.key ==
                              (course['places'] as List).length - 1,
                        )),
                const SizedBox(height: 8),
                SizedBox(
                  width: double.infinity,
                  child: OutlinedButton(
                    onPressed: () {},
                    style: OutlinedButton.styleFrom(
                      foregroundColor: const Color(0xFF2E7D6B),
                      side: const BorderSide(color: Color(0xFF2E7D6B)),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(10),
                      ),
                    ),
                    child: const Text(
                      '이 코스로 여행 계획하기',
                      style: TextStyle(fontWeight: FontWeight.w600),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// 코스 정보 필 위젯
class _InfoPill extends StatelessWidget {
  final IconData icon;
  final String label;
  const _InfoPill({required this.icon, required this.label});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: Colors.grey.shade100,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 14, color: Colors.grey.shade600),
          const SizedBox(width: 4),
          Text(
            label,
            style: TextStyle(
              fontSize: 12,
              color: Colors.grey.shade700,
              fontWeight: FontWeight.w500,
            ),
          ),
        ],
      ),
    );
  }
}

/// 코스 내 장소 아이템
class _PlaceItem extends StatelessWidget {
  final int index;
  final String name;
  final bool isLast;
  const _PlaceItem(
      {required this.index, required this.name, required this.isLast});

  @override
  Widget build(BuildContext context) {
    return IntrinsicHeight(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Column(
            children: [
              Container(
                width: 24,
                height: 24,
                decoration: BoxDecoration(
                  color: const Color(0xFF2E7D6B),
                  shape: BoxShape.circle,
                ),
                child: Center(
                  child: Text(
                    '$index',
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 11,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
              ),
              if (!isLast)
                Expanded(
                  child: Container(
                    width: 1.5,
                    color: const Color(0xFF2E7D6B).withValues(alpha: 0.25),
                  ),
                ),
            ],
          ),
          const SizedBox(width: 10),
          Padding(
            padding: EdgeInsets.only(bottom: isLast ? 0 : 10, top: 3),
            child: Text(
              name,
              style: const TextStyle(
                fontSize: 13,
                color: Color(0xFF333333),
                fontWeight: FontWeight.w500,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
```

---

## lib/screens/receipt_screen.dart

```dart
// receipt_screen.dart
// 영수증 스캔 화면 — 카메라 뷰파인더 영역, 갤러리/촬영 버튼
// 버튼 클릭 시 1.5초 로딩 후 더미 OCR 결과를 애니메이션과 함께 표시

import 'package:flutter/material.dart';
import '../widgets/benefit_chip.dart';

// ─────────────────────────────────────────────
// 더미 OCR 결과 데이터
// ─────────────────────────────────────────────

/// OCR 인식 결과 더미 데이터 (실제 OCR API 응답 구조 흉내)
const Map<String, dynamic> _dummyOcrResult = {
  'merchantName': '홍천 자연펜션',
  'amount': 85000,
  'category': '숙박업',
  'isEligible': true,
  'refundRate': 0.5,
  'receiptDate': '2026.03.30',
  'businessRegNo': '123-45-67890',
};

/// 누적 영수증 더미 데이터
const List<Map<String, dynamic>> _previousReceipts = [
  {
    'merchantName': '홍천 계곡 식당',
    'amount': 35000,
    'refundAmount': 17500,
    'isEligible': true,
  },
  {
    'merchantName': '수타사 전통찻집',
    'amount': 15000,
    'refundAmount': 7500,
    'isEligible': true,
  },
];

const int _prevRefundTotal = 25000;

// ─────────────────────────────────────────────
// 영수증 스캔 화면 위젯
// ─────────────────────────────────────────────
class ReceiptScreen extends StatefulWidget {
  const ReceiptScreen({super.key});

  @override
  State<ReceiptScreen> createState() => _ReceiptScreenState();
}

class _ReceiptScreenState extends State<ReceiptScreen>
    with TickerProviderStateMixin {
  bool _isLoading = false;
  bool _showResult = false;

  late AnimationController _fadeController;
  late Animation<double> _fadeAnimation;
  late AnimationController _slideController;
  late Animation<Offset> _slideAnimation;

  @override
  void initState() {
    super.initState();
    _fadeController = AnimationController(
      duration: const Duration(milliseconds: 500),
      vsync: this,
    );
    _fadeAnimation =
        CurvedAnimation(parent: _fadeController, curve: Curves.easeIn);

    _slideController = AnimationController(
      duration: const Duration(milliseconds: 400),
      vsync: this,
    );
    _slideAnimation = Tween<Offset>(
      begin: const Offset(0, 0.3),
      end: Offset.zero,
    ).animate(CurvedAnimation(parent: _slideController, curve: Curves.easeOut));
  }

  @override
  void dispose() {
    _fadeController.dispose();
    _slideController.dispose();
    super.dispose();
  }

  /// 스캔 버튼 핸들러 — 1.5초 로딩 후 더미 OCR 결과 표시
  Future<void> _handleScan() async {
    setState(() {
      _isLoading = true;
      _showResult = false;
    });
    _fadeController.reset();
    _slideController.reset();

    await Future.delayed(const Duration(milliseconds: 1500));

    if (!mounted) return;
    setState(() {
      _isLoading = false;
      _showResult = true;
    });
    _fadeController.forward();
    _slideController.forward();
  }

  int get _currentRefund =>
      (_dummyOcrResult['amount'] as int) *
      (_dummyOcrResult['refundRate'] as double) ~/
      1;

  int get _totalRefund => _prevRefundTotal + _currentRefund;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('영수증 스캔'),
        actions: [
          TextButton(
            onPressed: () {},
            child: const Text(
              '이력 보기',
              style: TextStyle(color: Color(0xFF2E7D6B)),
            ),
          ),
        ],
      ),
      body: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _ViewfinderArea(isLoading: _isLoading),
            const SizedBox(height: 16),

            // 갤러리 / 촬영 버튼
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Row(
                children: [
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: _isLoading ? null : _handleScan,
                      icon: const Icon(Icons.photo_library_outlined),
                      label: const Text('갤러리에서 선택'),
                      style: OutlinedButton.styleFrom(
                        foregroundColor: const Color(0xFF2E7D6B),
                        side: const BorderSide(color: Color(0xFF2E7D6B)),
                        padding: const EdgeInsets.symmetric(vertical: 14),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12),
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: ElevatedButton.icon(
                      onPressed: _isLoading ? null : _handleScan,
                      icon: const Icon(Icons.camera_alt_outlined),
                      label: const Text('촬영하기'),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: const Color(0xFF2E7D6B),
                        foregroundColor: Colors.white,
                        elevation: 0,
                        padding: const EdgeInsets.symmetric(vertical: 14),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12),
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),

            const SizedBox(height: 20),

            if (_showResult)
              FadeTransition(
                opacity: _fadeAnimation,
                child: SlideTransition(
                  position: _slideAnimation,
                  child: _OcrResultCard(
                    result: _dummyOcrResult,
                    currentRefund: _currentRefund,
                  ),
                ),
              ),

            if (_showResult)
              FadeTransition(
                opacity: _fadeAnimation,
                child: _TotalSavingsBanner(totalRefund: _totalRefund),
              ),

            if (_previousReceipts.isNotEmpty) ...[
              const Padding(
                padding: EdgeInsets.fromLTRB(16, 20, 16, 10),
                child: Text(
                  '이번 여행 스캔 내역',
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w700,
                    color: Color(0xFF1A1A1A),
                  ),
                ),
              ),
              ..._previousReceipts.map((r) => _PreviousReceiptItem(data: r)),
            ],

            const SizedBox(height: 32),
          ],
        ),
      ),
    );
  }
}

/// 카메라 뷰파인더 영역 위젯
class _ViewfinderArea extends StatelessWidget {
  final bool isLoading;
  const _ViewfinderArea({required this.isLoading});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.all(16),
      height: 240,
      decoration: BoxDecoration(
        color: Colors.grey.shade800,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Stack(
        children: [
          Center(
            child: isLoading
                ? Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      const CircularProgressIndicator(color: Colors.white),
                      const SizedBox(height: 16),
                      Text(
                        'OCR 분석 중...',
                        style: TextStyle(
                          color: Colors.white.withValues(alpha: 0.8),
                          fontSize: 14,
                        ),
                      ),
                    ],
                  )
                : Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(
                        Icons.receipt_long_outlined,
                        size: 48,
                        color: Colors.white.withValues(alpha: 0.4),
                      ),
                      const SizedBox(height: 12),
                      Text(
                        '영수증을 가이드 안에 맞춰주세요',
                        style: TextStyle(
                          color: Colors.white.withValues(alpha: 0.6),
                          fontSize: 13,
                        ),
                      ),
                    ],
                  ),
          ),
          if (!isLoading) const _CornerGuides(),
        ],
      ),
    );
  }
}

/// 뷰파인더 코너 가이드
class _CornerGuides extends StatelessWidget {
  const _CornerGuides();

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(30),
      child: Stack(
        children: [
          Positioned(
            top: 0,
            left: 0,
            child: _Corner(corners: {'top': true, 'left': true}),
          ),
          Positioned(
            top: 0,
            right: 0,
            child: _Corner(corners: {'top': true, 'right': true}),
          ),
          Positioned(
            bottom: 0,
            left: 0,
            child: _Corner(corners: {'bottom': true, 'left': true}),
          ),
          Positioned(
            bottom: 0,
            right: 0,
            child: _Corner(corners: {'bottom': true, 'right': true}),
          ),
        ],
      ),
    );
  }
}

class _Corner extends StatelessWidget {
  final Map<String, bool> corners;
  const _Corner({required this.corners});

  @override
  Widget build(BuildContext context) {
    const double size = 24;
    const double thickness = 3;
    const Color color = Color(0xFF2E7D6B);

    return SizedBox(
      width: size,
      height: size,
      child: CustomPaint(
        painter: _CornerPainter(
            corners: corners, thickness: thickness, color: color),
      ),
    );
  }
}

class _CornerPainter extends CustomPainter {
  final Map<String, bool> corners;
  final double thickness;
  final Color color;

  _CornerPainter(
      {required this.corners, required this.thickness, required this.color});

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = color
      ..strokeWidth = thickness
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.round;

    final bool top = corners['top'] ?? false;
    final bool bottom = corners['bottom'] ?? false;
    final bool left = corners['left'] ?? false;
    final bool right = corners['right'] ?? false;

    if (top && left) {
      canvas.drawLine(const Offset(0, 0), Offset(size.width, 0), paint);
      canvas.drawLine(const Offset(0, 0), Offset(0, size.height), paint);
    }
    if (top && right) {
      canvas.drawLine(Offset(0, 0), Offset(size.width, 0), paint);
      canvas.drawLine(
          Offset(size.width, 0), Offset(size.width, size.height), paint);
    }
    if (bottom && left) {
      canvas.drawLine(
          Offset(0, size.height), Offset(size.width, size.height), paint);
      canvas.drawLine(Offset(0, 0), Offset(0, size.height), paint);
    }
    if (bottom && right) {
      canvas.drawLine(
          Offset(0, size.height), Offset(size.width, size.height), paint);
      canvas.drawLine(
          Offset(size.width, 0), Offset(size.width, size.height), paint);
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

/// OCR 결과 카드 위젯
class _OcrResultCard extends StatelessWidget {
  final Map<String, dynamic> result;
  final int currentRefund;

  const _OcrResultCard({required this.result, required this.currentRefund});

  @override
  Widget build(BuildContext context) {
    final bool isEligible = result['isEligible'] as bool;

    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: isEligible
              ? const Color(0xFF1B8C6E).withValues(alpha: 0.4)
              : Colors.grey.shade300,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(Icons.document_scanner_outlined,
                  color: Color(0xFF2E7D6B), size: 20),
              const SizedBox(width: 8),
              const Text(
                'OCR 인식 결과',
                style: TextStyle(
                  fontSize: 15,
                  fontWeight: FontWeight.w700,
                  color: Color(0xFF1A1A1A),
                ),
              ),
              const Spacer(),
              BenefitChip(
                label: isEligible ? '인식 완료 ✓' : '인식 실패',
                backgroundColor: isEligible
                    ? const Color(0xFF1B8C6E).withValues(alpha: 0.1)
                    : Colors.grey.shade100,
                textColor: isEligible ? const Color(0xFF1B8C6E) : Colors.grey,
              ),
            ],
          ),
          const SizedBox(height: 14),
          const Divider(height: 1),
          const SizedBox(height: 14),
          _ResultRow(
            label: '인식된 가맹점',
            value: result['merchantName'] as String,
            valueStyle: const TextStyle(
              fontWeight: FontWeight.w700,
              fontSize: 14,
              color: Color(0xFF1A1A1A),
            ),
          ),
          const SizedBox(height: 10),
          _ResultRow(
            label: '결제 금액',
            value: '${_formatAmount(result['amount'] as int)}원',
          ),
          const SizedBox(height: 10),
          _ResultRow(
            label: '영수증 날짜',
            value: result['receiptDate'] as String,
          ),
          const SizedBox(height: 10),
          _ResultRow(
            label: '인정 여부',
            value: '환급 인정 업종 ✓',
            valueStyle: const TextStyle(
              fontWeight: FontWeight.w700,
              fontSize: 14,
              color: Color(0xFF1B8C6E),
            ),
          ),
          const SizedBox(height: 14),
          const Divider(height: 1),
          const SizedBox(height: 14),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
            decoration: BoxDecoration(
              color: const Color(0xFF1B8C6E).withValues(alpha: 0.07),
              borderRadius: BorderRadius.circular(10),
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text(
                  '예상 환급액',
                  style: TextStyle(
                    fontWeight: FontWeight.w600,
                    fontSize: 14,
                    color: Color(0xFF1B8C6E),
                  ),
                ),
                Text(
                  '${_formatAmount(currentRefund)}원',
                  style: const TextStyle(
                    fontWeight: FontWeight.w800,
                    fontSize: 20,
                    color: Color(0xFF1B8C6E),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  String _formatAmount(int amount) {
    return amount.toString().replaceAllMapped(
          RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'),
          (m) => '${m[1]},',
        );
  }
}

class _ResultRow extends StatelessWidget {
  final String label;
  final String value;
  final TextStyle? valueStyle;

  const _ResultRow({
    required this.label,
    required this.value,
    this.valueStyle,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(
          label,
          style: TextStyle(fontSize: 13, color: Colors.grey.shade600),
        ),
        Text(
          value,
          style: valueStyle ??
              const TextStyle(
                fontWeight: FontWeight.w600,
                fontSize: 14,
                color: Color(0xFF1A1A1A),
              ),
        ),
      ],
    );
  }
}

class _TotalSavingsBanner extends StatelessWidget {
  final int totalRefund;
  const _TotalSavingsBanner({required this.totalRefund});

  String _formatAmount(int amount) {
    return amount.toString().replaceAllMapped(
          RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'),
          (m) => '${m[1]},',
        );
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: [Color(0xFF1B8C6E), Color(0xFF2E7D6B)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          const Icon(Icons.savings_outlined, color: Colors.white, size: 28),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  '이번 여행 예상 환급 합계',
                  style: TextStyle(color: Colors.white70, fontSize: 12),
                ),
                Text(
                  '${_formatAmount(totalRefund)}원',
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 24,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _PreviousReceiptItem extends StatelessWidget {
  final Map<String, dynamic> data;
  const _PreviousReceiptItem({required this.data});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: Colors.grey.shade200),
      ),
      child: Row(
        children: [
          Container(
            width: 36,
            height: 36,
            decoration: BoxDecoration(
              color: const Color(0xFF1B8C6E).withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(8),
            ),
            child: const Icon(
              Icons.receipt_outlined,
              color: Color(0xFF1B8C6E),
              size: 20,
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  data['merchantName'] as String,
                  style: const TextStyle(
                    fontWeight: FontWeight.w600,
                    fontSize: 13,
                    color: Color(0xFF1A1A1A),
                  ),
                ),
                Text(
                  '${_fmt(data['amount'] as int)}원 결제',
                  style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
                ),
              ],
            ),
          ),
          Text(
            '+${_fmt(data['refundAmount'] as int)}원',
            style: const TextStyle(
              fontWeight: FontWeight.w700,
              fontSize: 14,
              color: Color(0xFF1B8C6E),
            ),
          ),
        ],
      ),
    );
  }

  String _fmt(int v) => v.toString().replaceAllMapped(
        RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'),
        (m) => '${m[1]},',
      );
}
```

---

## lib/screens/my_trip_screen.dart

```dart
// my_trip_screen.dart
// 나의 여행 기록 화면 — 프로필 섹션, 방문 지역 카드 그리드,
// 역대 혜택 리포트, 취향 프로필 LinearProgressIndicator, 버킷리스트

import 'package:flutter/material.dart';
import '../widgets/benefit_chip.dart';

// ─────────────────────────────────────────────
// 더미 데이터
// ─────────────────────────────────────────────

/// 사용자 프로필 더미 데이터
const Map<String, dynamic> _userProfile = {
  'name': '김여행',
  'tripCount': 12,
  'totalSavings': 234000,
  'level': 'GOLD',
};

/// 방문한 지역 더미 데이터
const List<Map<String, dynamic>> _visitedRegions = [
  {
    'areaCode': '32',
    'name': '강원도',
    'spotCount': 5,
    'lastVisit': '2026.03',
    'color': Color(0xFF2E7D6B),
    'icon': Icons.forest_outlined,
    'tags': ['자연', '캠핑'],
  },
  {
    'areaCode': '39',
    'name': '제주도',
    'spotCount': 8,
    'lastVisit': '2025.12',
    'color': Color(0xFF1565C0),
    'icon': Icons.beach_access_outlined,
    'tags': ['바다', '올레길'],
  },
  {
    'areaCode': '37',
    'name': '경상북도',
    'spotCount': 3,
    'lastVisit': '2025.09',
    'color': Color(0xFF5C4AE3),
    'icon': Icons.account_balance_outlined,
    'tags': ['역사', '문화'],
  },
];

/// 역대 혜택 리포트 더미 데이터
const Map<String, dynamic> _benefitReport = {
  'totalRefund': 234000,
  'subsidyCount': 3,
  'visitedAreaCount': 7,
  'avgSavingPerTrip': 19500,
};

/// 취향 프로필 더미 데이터
const List<Map<String, dynamic>> _tasteProfile = [
  {'label': '자연/힐링', 'rate': 0.70, 'color': Color(0xFF2E7D6B)},
  {'label': '맛집 탐방', 'rate': 0.20, 'color': Color(0xFFF57C00)},
  {'label': '역사/문화', 'rate': 0.10, 'color': Color(0xFF5C4AE3)},
];

/// 버킷리스트 더미 데이터
const List<Map<String, String>> _bucketList = [
  {
    'title': '충남 서천 국립생태원 탐방',
    'area': '충남 서천',
    'note': '봄에 꽃 피는 시기에 방문하고 싶다',
  },
  {
    'title': '전남 고흥 나로도 우주센터',
    'area': '전남 고흥',
    'note': '로켓 발사 일정에 맞춰서!',
  },
];

// ─────────────────────────────────────────────
// 나의 여행 기록 화면 위젯
// ─────────────────────────────────────────────
class MyTripScreen extends StatelessWidget {
  const MyTripScreen({super.key});

  String _formatAmount(int amount) {
    return amount.toString().replaceAllMapped(
          RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'),
          (m) => '${m[1]},',
        );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('나의 여행 기록'),
        actions: [
          IconButton(
            icon: const Icon(Icons.settings_outlined),
            onPressed: () {},
          ),
        ],
      ),
      body: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _ProfileSection(
              profile: _userProfile,
              formatAmount: _formatAmount,
            ),

            const Padding(
              padding: EdgeInsets.fromLTRB(16, 24, 16, 12),
              child: Row(
                children: [
                  Text('👣 ', style: TextStyle(fontSize: 18)),
                  Text(
                    '나의 족적',
                    style: TextStyle(
                      fontSize: 17,
                      fontWeight: FontWeight.w700,
                      color: Color(0xFF1A1A1A),
                    ),
                  ),
                ],
              ),
            ),
            GridView.builder(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              padding: const EdgeInsets.symmetric(horizontal: 16),
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 3,
                crossAxisSpacing: 10,
                mainAxisSpacing: 10,
                childAspectRatio: 0.85,
              ),
              itemCount: _visitedRegions.length,
              itemBuilder: (context, index) {
                return _VisitedRegionCard(region: _visitedRegions[index]);
              },
            ),

            const Padding(
              padding: EdgeInsets.fromLTRB(16, 24, 16, 12),
              child: Text(
                '역대 혜택 리포트',
                style: TextStyle(
                  fontSize: 17,
                  fontWeight: FontWeight.w700,
                  color: Color(0xFF1A1A1A),
                ),
              ),
            ),
            _BenefitReportCard(
              report: _benefitReport,
              formatAmount: _formatAmount,
            ),

            const Padding(
              padding: EdgeInsets.fromLTRB(16, 24, 16, 12),
              child: Text(
                '취향 프로필',
                style: TextStyle(
                  fontSize: 17,
                  fontWeight: FontWeight.w700,
                  color: Color(0xFF1A1A1A),
                ),
              ),
            ),
            _TasteProfileCard(tasteData: _tasteProfile),

            const Padding(
              padding: EdgeInsets.fromLTRB(16, 24, 16, 12),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    '버킷리스트',
                    style: TextStyle(
                      fontSize: 17,
                      fontWeight: FontWeight.w700,
                      color: Color(0xFF1A1A1A),
                    ),
                  ),
                  Text(
                    '+ 추가',
                    style: TextStyle(
                      fontSize: 13,
                      color: Color(0xFF2E7D6B),
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ),
            ),
            ..._bucketList.map((b) => _BucketListItem(data: b)),

            const SizedBox(height: 32),
          ],
        ),
      ),
    );
  }
}

class _ProfileSection extends StatelessWidget {
  final Map<String, dynamic> profile;
  final String Function(int) formatAmount;

  const _ProfileSection({
    required this.profile,
    required this.formatAmount,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.all(16),
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: [Color(0xFF2E7D6B), Color(0xFF1B8C6E)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Row(
        children: [
          Container(
            width: 60,
            height: 60,
            decoration: BoxDecoration(
              color: Colors.white.withValues(alpha: 0.25),
              shape: BoxShape.circle,
            ),
            child: const Center(
              child: Text('✈️', style: TextStyle(fontSize: 28)),
            ),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Text(
                      profile['name'] as String,
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 18,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(width: 8),
                    BenefitChip(
                      label: profile['level'] as String,
                      backgroundColor:
                          const Color(0xFFFFD700).withValues(alpha: 0.3),
                      textColor: const Color(0xFFFFD700),
                      icon: Icons.star_rounded,
                    ),
                  ],
                ),
                const SizedBox(height: 6),
                Text(
                  '총 ${profile['tripCount']}번 여행 · 누적 절약 ${formatAmount(profile['totalSavings'] as int)}원',
                  style: TextStyle(
                    color: Colors.white.withValues(alpha: 0.85),
                    fontSize: 13,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _VisitedRegionCard extends StatelessWidget {
  final Map<String, dynamic> region;
  const _VisitedRegionCard({required this.region});

  @override
  Widget build(BuildContext context) {
    final Color color = region['color'] as Color;
    final List<String> tags = region['tags'] as List<String>;

    return Container(
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.grey.shade200),
      ),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Container(
            width: 44,
            height: 44,
            decoration: BoxDecoration(
              color: color.withValues(alpha: 0.12),
              shape: BoxShape.circle,
            ),
            child: Icon(region['icon'] as IconData, color: color, size: 22),
          ),
          const SizedBox(height: 8),
          Text(
            region['name'] as String,
            style: const TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w700,
              color: Color(0xFF1A1A1A),
            ),
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 4),
          Text(
            '${region['spotCount']}곳 방문',
            style: TextStyle(fontSize: 10, color: Colors.grey.shade600),
          ),
          const SizedBox(height: 6),
          Wrap(
            spacing: 4,
            runSpacing: 4,
            alignment: WrapAlignment.center,
            children: tags
                .map((tag) => Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 5, vertical: 2),
                      decoration: BoxDecoration(
                        color: color.withValues(alpha: 0.1),
                        borderRadius: BorderRadius.circular(4),
                      ),
                      child: Text(
                        tag,
                        style: TextStyle(
                            fontSize: 9,
                            color: color,
                            fontWeight: FontWeight.w600),
                      ),
                    ))
                .toList(),
          ),
        ],
      ),
    );
  }
}

class _BenefitReportCard extends StatelessWidget {
  final Map<String, dynamic> report;
  final String Function(int) formatAmount;

  const _BenefitReportCard({
    required this.report,
    required this.formatAmount,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16),
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.grey.shade200),
      ),
      child: Column(
        children: [
          Container(
            padding: const EdgeInsets.symmetric(vertical: 14),
            decoration: BoxDecoration(
              color: const Color(0xFF1B8C6E).withValues(alpha: 0.07),
              borderRadius: BorderRadius.circular(10),
            ),
            child: Center(
              child: Column(
                children: [
                  const Text(
                    '총 환급액',
                    style: TextStyle(fontSize: 13, color: Color(0xFF1B8C6E)),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    '${formatAmount(report['totalRefund'] as int)}원',
                    style: const TextStyle(
                      fontSize: 28,
                      fontWeight: FontWeight.w800,
                      color: Color(0xFF1B8C6E),
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
          Row(
            children: [
              Expanded(
                child: _StatItem(
                  value: '${report['subsidyCount']}건',
                  label: '이용한 보조금',
                  icon: Icons.card_giftcard_outlined,
                ),
              ),
              Container(width: 1, height: 40, color: Colors.grey.shade200),
              Expanded(
                child: _StatItem(
                  value: '${report['visitedAreaCount']}곳',
                  label: '방문 지역',
                  icon: Icons.place_outlined,
                ),
              ),
              Container(width: 1, height: 40, color: Colors.grey.shade200),
              Expanded(
                child: _StatItem(
                  value:
                      '${formatAmount(report['avgSavingPerTrip'] as int)}원',
                  label: '여행당 평균',
                  icon: Icons.trending_up_outlined,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _StatItem extends StatelessWidget {
  final String value;
  final String label;
  final IconData icon;
  const _StatItem(
      {required this.value, required this.label, required this.icon});

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Icon(icon, size: 20, color: const Color(0xFF5C4AE3)),
        const SizedBox(height: 4),
        Text(
          value,
          style: const TextStyle(
            fontWeight: FontWeight.w800,
            fontSize: 15,
            color: Color(0xFF1A1A1A),
          ),
        ),
        Text(
          label,
          style: const TextStyle(fontSize: 10, color: Colors.grey),
        ),
      ],
    );
  }
}

class _TasteProfileCard extends StatelessWidget {
  final List<Map<String, dynamic>> tasteData;
  const _TasteProfileCard({required this.tasteData});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.grey.shade200),
      ),
      child: Column(
        children: tasteData.asMap().entries.map((e) {
          final index = e.key;
          final item = e.value;
          final double rate = item['rate'] as double;
          final Color color = item['color'] as Color;

          return Padding(
            padding: EdgeInsets.only(
                bottom: index < tasteData.length - 1 ? 14 : 0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      item['label'] as String,
                      style: const TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                        color: Color(0xFF1A1A1A),
                      ),
                    ),
                    Text(
                      '${(rate * 100).round()}%',
                      style: TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w700,
                        color: color,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 6),
                ClipRRect(
                  borderRadius: BorderRadius.circular(4),
                  child: LinearProgressIndicator(
                    value: rate,
                    backgroundColor: Colors.grey.shade100,
                    valueColor: AlwaysStoppedAnimation<Color>(color),
                    minHeight: 8,
                  ),
                ),
              ],
            ),
          );
        }).toList(),
      ),
    );
  }
}

class _BucketListItem extends StatelessWidget {
  final Map<String, String> data;
  const _BucketListItem({required this.data});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 5),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.grey.shade200),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Padding(
            padding: EdgeInsets.only(top: 2),
            child: Text('🗺️', style: TextStyle(fontSize: 22)),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  data['title'] ?? '',
                  style: const TextStyle(
                    fontWeight: FontWeight.w700,
                    fontSize: 14,
                    color: Color(0xFF1A1A1A),
                  ),
                ),
                const SizedBox(height: 3),
                Row(
                  children: [
                    const Icon(Icons.place_outlined,
                        size: 13, color: Colors.grey),
                    const SizedBox(width: 3),
                    Text(
                      data['area'] ?? '',
                      style: TextStyle(
                          fontSize: 12, color: Colors.grey.shade600),
                    ),
                  ],
                ),
                const SizedBox(height: 4),
                Text(
                  data['note'] ?? '',
                  style: TextStyle(
                    fontSize: 12,
                    color: Colors.grey.shade500,
                    fontStyle: FontStyle.italic,
                  ),
                ),
              ],
            ),
          ),
          IconButton(
            icon: const Icon(Icons.more_horiz, color: Colors.grey),
            onPressed: () {},
            visualDensity: VisualDensity.compact,
          ),
        ],
      ),
    );
  }
}
```

---

## 디자인 가이드

| 항목 | 값 |
|------|-----|
| **테마** | Material 3, `colorSchemeSeed: #2E7D6B` |
| **앱바** | 흰 배경, 타이틀 좌측 정렬, elevation 0 |
| **카드** | elevation 0, 1px 그레이 테두리, borderRadius 12 |
| **보조금 강조색** | `#5C4AE3` (퍼플) |
| **환급/성공 색상** | `#1B8C6E` (틸) |
| **마감/경고 색상** | `#D84315` (코랄) |
| **배경색** | `#F8F9FA` |
| **텍스트 기본색** | `#1A1A1A` |
| **폰트** | 기본 Roboto |

### 화면 흐름

```
[홈] ──────────────────────────────────────┐
  ├─ 보조금 배너 → (혜택 탭)               │
  ├─ 숙박세일페스타 미니 배너               │
  ├─ 한산한 여행지 가로 스크롤              │
  └─ 이번 주 축제 리스트                   │
                                           │
[혜택] ────────────────────────────────────┤
  ├─ 지역사랑 휴가지원 → [상세: 지역 그리드 + 4단계 스텝]
  ├─ 숙박세일페스타 → [상세: 카운트다운 + 안내카드]
  ├─ 대한민국 숙박 대전 → [상세: 할인 안내카드]
  └─ 온누리상품권 → [상세: 캐시백 안내카드]
                                           │
[플래너] ──────────────────────────────────┤
  ├─ 날씨 배너                              │
  ├─ 무드 칩 (5종) → setState로 코스 변경   │
  ├─ 검색바                                 │
  ├─ 추천 코스 카드 (보조금 대상 강조)      │
  └─ FAB: 영수증 스캔 → [ReceiptScreen]    │
                                           │
[기록] ────────────────────────────────────┘
  ├─ 프로필 (GOLD 레벨)
  ├─ 나의 족적 (3개 지역 그리드)
  ├─ 역대 혜택 리포트 (총 환급액 + 통계)
  ├─ 취향 프로필 (LinearProgressIndicator)
  └─ 버킷리스트 (2개)
```
