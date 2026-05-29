// home_screen.dart
// 홈 화면 — 개인화 인사, 여행 시작 CTA, 혜택 카테고리 섹션,
// 이번 주 근처 축제 리스트

import 'dart:math';
import 'package:flutter/material.dart';
import 'package:geolocator/geolocator.dart';
import 'package:tripmate/core/models/user_prefs.dart';
import 'package:tripmate/core/widgets/benefit_chip.dart';
import 'package:tripmate/core/widgets/shimmer_box.dart';
import 'package:tripmate/infrastructure/api_service.dart';
import 'package:tripmate/features/auth/travel_setup_screen.dart';
import 'package:tripmate/features/course/course_loading_screen.dart';
import 'package:tripmate/features/benefit/benefit_detail_screen.dart';
import 'package:tripmate/features/benefit/subsidy_screen.dart' show BenefitItem, StatusType;

// ─────────────────────────────────────────────
// 홈 화면 위젯
// ─────────────────────────────────────────────

/// purpose별 (greeting 문구, CTA 카드 문구, 선호 앵커명) 변형 풀 — 매 화면 진입마다 랜덤 선택
/// 앵커명은 CourseService.ANCHORS의 name()과 일치해야 함
const _kGreetingVariants = <String, List<(String, String, String)>>{
  'resort': [
    ('편안한 숙소 여행, 강원도 어떠세요? 🏨',  '강원도 숙박 여행 코스,\n어떠세요?',    '강릉'),
    ('한적한 호텔 패키지, 제주도로! 🌊',       '제주 프리미엄 숙박 코스,\n어떠세요?',  '서귀포'),
    ('감성 풀빌라 여행, 경주 어떠세요? 🏯',    '경북 힐링 숙박 여행 코스,\n어떠세요?', '경주'),
  ],
  'food': [
    ('맛집 탐방 코스, 안동 찜닭 어떠세요! 🍜', '안동 미식 여행 코스,\n어떠세요?',      '안동'),
    ('전주 비빔밥부터 막걸리까지! 🍶',         '전주 맛집 탐방 코스,\n어떠세요?',      '전주'),
    ('통영 해산물 투어, 떠나볼까요? 🦞',       '통영 해산물 미식 코스,\n어떠세요?',    '통영'),
  ],
  'budget': [
    ('가성비 여행지, 춘천이 딱이에요 💰',       '춘천 가성비 코스,\n어떠세요?',         '춘천'),
    ('알뜰 국내 여행, 대전 어떠세요? 🏕️',      '대전 알뜰 여행 코스,\n어떠세요?',      '대전'),
    ('예산 아껴도 즐거운 강화도 여행! 🌅',      '인천 강화도 가성비 코스,\n어떠세요?',  '인천 강화도'),
  ],
  'nature': [
    ('자연 속 힐링, 이번 주말 속초로! 🌿',      '속초·양양 자연 힐링 코스,\n어떠세요?', '속초·양양'),
    ('비자림·오름 트레킹, 제주로! 🌋',          '제주 자연 탐방 코스,\n어떠세요?',      '제주시'),
    ('지리산 둘레길, 순천에서 시작해요 🍃',     '순천 자연 트레킹 코스,\n어떠세요?',    '순천'),
  ],
  'history': [
    ('천년 역사의 경주, 떠나보세요 🏛️',         '경주 역사 탐방 코스,\n어떠세요?',      '경주'),
    ('조선 왕조의 흔적, 수원 화성! 🏰',         '수원 화성 역사 투어,\n어떠세요?',      '수원 화성'),
    ('백제 문화의 중심, 부여/공주로! 🗺️',       '공주·부여 백제 코스,\n어떠세요?',      '공주·부여'),
  ],
  'activity': [
    ('액티비티 가득한 제주도로 가볼까요? 🧗',   '제주 액티비티 코스,\n어떠세요?',       '제주시'),
    ('래프팅·번지점프, 양양으로! 🏄',           '속초·양양 익스트림 코스,\n어떠세요?',  '속초·양양'),
    ('서핑·스노클링, 거제로! 🏊',               '거제 해양 스포츠 코스,\n어떠세요?',    '거제'),
  ],
};

/// greeting 텍스트 반환 (variantIdx: 화면 진입 시 결정된 랜덤 인덱스)
String _personalizedGreeting(UserPrefs prefs, int variantIdx) {
  if (!prefs.hasPrefs) return '어디로 떠나볼까요? ✈️';
  final variants = _kGreetingVariants[prefs.purposes.first];
  if (variants == null || variants.isEmpty) return '어디로 떠나볼까요? ✈️';
  return variants[variantIdx % variants.length].$1;
}

/// 선택된 variant의 선호 앵커명 반환
String? _preferredAnchor(UserPrefs prefs, int variantIdx) {
  if (!prefs.hasPrefs) return null;
  final variants = _kGreetingVariants[prefs.purposes.first];
  if (variants == null || variants.isEmpty) return null;
  return variants[variantIdx % variants.length].$3;
}

class HomeScreen extends StatefulWidget {
  final UserPrefs userPrefs;
  final VoidCallback? onShowAllBenefits;

  const HomeScreen({super.key, required this.userPrefs, this.onShowAllBenefits});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  // 혜택 카테고리 토글 (0: 예약 전 혜택, 1: 현지 소비 혜택)
  int _benefitCategory = 0;
  // 매 화면 진입마다 다른 인사/CTA 문구를 보여주기 위한 랜덤 인덱스
  late int _greetingVariantIdx;

  // 백엔드 연동 데이터
  List<Map<String, String>> _nearbyFestivalsList = [];
  List<BenefitItem> _benefitsList = [];
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _greetingVariantIdx = Random().nextInt(3);
    _fetchData();
  }

  Future<void> _fetchData() async {
    setState(() => _isLoading = true);

    try {
      final permission = await Geolocator.checkPermission();
      double? lat, lng;
      if (permission != LocationPermission.denied &&
          permission != LocationPermission.deniedForever) {
        final position = await ApiService.getCurrentLocation();
        lat = position.latitude;
        lng = position.longitude;
      }

      final festivalsRaw = lat != null
          ? await ApiService.getNearbyFestivals(lat, lng!)
          : <dynamic>[];
      final benefitsRaw = await ApiService.getBenefits();
      final benefits = benefitsRaw;

      if (!mounted) return;
      setState(() {
        _nearbyFestivalsList = festivalsRaw.map((f) {
          final map = f as Map<String, dynamic>;
          return {
            'title': (map['title'] as String?) ?? '행사 정보 없음',
            'area': (map['location'] as String?) ?? '지역 정보 없음',
            'startDate': (map['startDate'] as String?) ?? '',
            'endDate': (map['endDate'] as String?) ?? '',
            'category': '지역 축제',
          };
        }).toList();
        _benefitsList = benefits.map(BenefitItem.fromJson).toList();
        _isLoading = false;
      });
    } catch (e) {
      debugPrint('Data fetch error: $e');
      if (!mounted) return;
      setState(() => _isLoading = false);
    }
  }

  List<BenefitItem> get _filteredBenefits {
    if (_benefitCategory == 0) {
      // 예약 전 혜택: 숙박 관련
      return _benefitsList.where((b) => b.benefitType == 'pre').toList();
    } else {
      // 현지 소비 혜택: 상품권/지원금
      return _benefitsList.where((b) => b.benefitType == 'local').toList();
    }
  }

  @override
  Widget build(BuildContext context) {
    final hasPrefs = widget.userPrefs.hasPrefs;

    return Scaffold(
      appBar: AppBar(
        title: const Row(
          children: [
            Text('🧭', style: TextStyle(fontSize: 20)),
            SizedBox(width: 6),
            Text('가보자고'),
          ],
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.notifications_outlined),
            onPressed: () {},
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: _fetchData,
        child: SingleChildScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // ── 개인화 인사 ──────────────────────────────
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 20, 16, 4),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      widget.userPrefs.displayName.isNotEmpty
                          ? '${widget.userPrefs.displayName}, 안녕하세요!'
                          : '안녕하세요,',
                      style: const TextStyle(
                        fontSize: 22,
                        fontWeight: FontWeight.w800,
                        color: Color(0xFF1A1A1A),
                        letterSpacing: -0.4,
                      ),
                    ),
                    Text(
                      _personalizedGreeting(widget.userPrefs, _greetingVariantIdx),
                      style: const TextStyle(
                        fontSize: 22,
                        fontWeight: FontWeight.w800,
                        color: Color(0xFF1A1A1A),
                        letterSpacing: -0.4,
                        height: 1.3,
                      ),
                    ),
                    // 취향 칩 — hasPrefs일 때만 표시
                    if (hasPrefs) ...[
                      const SizedBox(height: 10),
                      Wrap(
                        spacing: 6,
                        runSpacing: 6,
                        children: [
                          ...widget.userPrefs.purposes.map((key) {
                            final opt = kPurposeOptions.firstWhere(
                              (o) => o['key'] == key,
                              orElse: () => {'label': key, 'icon': ''},
                            );
                            return _PrefsChip('${opt['icon']} ${opt['label']}');
                          }),
                          _PrefsChip(
                            kDurationOptions.firstWhere(
                              (o) => o['key'] == widget.userPrefs.duration,
                              orElse: () => {'label': widget.userPrefs.duration},
                            )['label']!,
                          ),
                        ],
                      ),
                    ],
                  ],
                ),
              ),

              // ── 여행 시작 CTA 카드 ────────────────────────
              _TravelStartCard(
                hasPrefs: hasPrefs,
                userPrefs: widget.userPrefs,
                variantIdx: _greetingVariantIdx,
              ),

              // ── 혜택 카테고리 섹션 ────────────────────────
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    const Text(
                      '여행 혜택',
                      style: TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.w700,
                        color: Color(0xFF1A1A1A),
                      ),
                    ),
                    GestureDetector(
                      onTap: () => widget.onShowAllBenefits?.call(),
                      child: const Text(
                        '전체보기',
                        style: TextStyle(
                          fontSize: 13,
                          color: Color(0xFF2E7D6B),
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 10),

              // 카테고리 토글
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: Row(
                  children: [
                    _BenefitCategoryTab(
                      label: '예약 전 혜택',
                      icon: Icons.hotel_outlined,
                      selected: _benefitCategory == 0,
                      onTap: () => setState(() => _benefitCategory = 0),
                    ),
                    const SizedBox(width: 8),
                    _BenefitCategoryTab(
                      label: '현지 소비 혜택',
                      icon: Icons.storefront_outlined,
                      selected: _benefitCategory == 1,
                      onTap: () => setState(() => _benefitCategory = 1),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 10),

              // 혜택 카드 리스트
              if (_isLoading)
                ...List.generate(2, (_) => const BenefitCardShimmer())
              else
              ..._filteredBenefits.map(
                (benefit) => _HomeBenefitCard(
                  benefit: benefit,
                  onTap: () => Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (_) => BenefitDetailScreen(benefit: benefit),
                    ),
                  ),
                ),
              ),

              const SizedBox(height: 24),

              // ── 이번 주 근처 축제 섹션 ────────────────────
              const Padding(
                padding: EdgeInsets.symmetric(horizontal: 16),
                child: Text(
                  '이번 주 근처 축제',
                  style: TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.w700,
                    color: Color(0xFF1A1A1A),
                  ),
                ),
              ),
              const SizedBox(height: 12),
              _isLoading
                  ? Column(
                      children: List.generate(
                        3,
                        (_) => const FestivalItemShimmer(),
                      ),
                    )
                  : Column(
                      children: _nearbyFestivalsList
                          .map((f) => _FestivalListItem(festival: f))
                          .toList(),
                    ),

              const SizedBox(height: 32),
            ],
          ),
        ),
      ),
    );
  }
}

// ─── 여행 시작 CTA 카드 ────────────────────────────────────
class _TravelStartCard extends StatelessWidget {
  final bool hasPrefs;
  final UserPrefs userPrefs;
  final int variantIdx;

  const _TravelStartCard({
    required this.hasPrefs,
    required this.userPrefs,
    required this.variantIdx,
  });

  String get _ctaTitle {
    if (!hasPrefs) return '취향에 맞는 여행 코스와\n혜택을 한번에!';
    final variants = _kGreetingVariants[userPrefs.purposes.first];
    if (variants == null || variants.isEmpty) return '새로운 여행 코스를\n찾아볼까요?';
    return variants[variantIdx % variants.length].$2;
  }

  String? get _anchorHint => _preferredAnchor(userPrefs, variantIdx);

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return GestureDetector(
      onTap: () {
        if (hasPrefs) {
          Navigator.push(
            context,
            MaterialPageRoute(
              builder: (_) => CourseLoadingScreen(
                prefs: userPrefs,
                preferredAnchor: _anchorHint,
              ),
            ),
          );
        } else {
          Navigator.push(
            context,
            MaterialPageRoute(
              builder: (_) => const TravelSetupScreen(),
            ),
          );
        }
      },
      child: Container(
        margin: const EdgeInsets.fromLTRB(16, 12, 16, 0),
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [colorScheme.primary, colorScheme.tertiary],
          ),
          borderRadius: BorderRadius.circular(16),
        ),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 8,
                      vertical: 3,
                    ),
                    decoration: BoxDecoration(
                      color: Colors.white.withValues(alpha: 0.25),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Text(
                      hasPrefs ? '맞춤 코스 다시 추천' : '여행 취향 분석',
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 11,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    _ctaTitle,
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 18,
                      fontWeight: FontWeight.w800,
                      height: 1.3,
                      letterSpacing: -0.3,
                    ),
                  ),
                  const SizedBox(height: 12),
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 14,
                      vertical: 8,
                    ),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(20),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(
                          hasPrefs ? '내 취향 코스 추천받기' : '여행 시작하기',
                          style: TextStyle(
                            color: colorScheme.primary,
                            fontSize: 13,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                        const SizedBox(width: 4),
                        Icon(
                          Icons.arrow_forward,
                          size: 14,
                          color: colorScheme.primary,
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(width: 12),
            const Text('🗺️', style: TextStyle(fontSize: 56)),
          ],
        ),
      ),
    );
  }
}

// ─── 취향 칩 뱃지 ──────────────────────────────────────────
class _PrefsChip extends StatelessWidget {
  final String label;
  const _PrefsChip(this.label);

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: const Color(0xFFE8F5F1),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Text(
        label,
        style: const TextStyle(
          fontSize: 12,
          fontWeight: FontWeight.w600,
          color: Color(0xFF2E7D6B),
        ),
      ),
    );
  }
}

// ─── 혜택 카테고리 탭 버튼 ─────────────────────────────────
class _BenefitCategoryTab extends StatelessWidget {
  final String label;
  final IconData icon;
  final bool selected;
  final VoidCallback onTap;

  const _BenefitCategoryTab({
    required this.label,
    required this.icon,
    required this.selected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
        decoration: BoxDecoration(
          color: selected
              ? colorScheme.primaryContainer
              : colorScheme.surfaceContainerLow,
          borderRadius: BorderRadius.circular(20),
          border: Border.all(
            color: selected ? colorScheme.primary : Colors.transparent,
          ),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              icon,
              size: 15,
              color: selected
                  ? colorScheme.primary
                  : colorScheme.onSurface.withValues(alpha: 0.6),
            ),
            const SizedBox(width: 5),
            Text(
              label,
              style: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w600,
                color: selected
                    ? colorScheme.primary
                    : colorScheme.onSurface.withValues(alpha: 0.6),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ─── 홈 혜택 카드 ──────────────────────────────────────────
class _HomeBenefitCard extends StatelessWidget {
  final BenefitItem benefit;
  final VoidCallback onTap;

  const _HomeBenefitCard({required this.benefit, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final isUrgent =
        benefit.statusType == StatusType.deadline &&
        benefit.statusLabel.contains('D-');

    return GestureDetector(
      onTap: onTap,
      child: Container(
        margin: const EdgeInsets.fromLTRB(16, 0, 16, 8),
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(16),
          boxShadow: [
            const BoxShadow(color: Color(0x05000000), blurRadius: 0, spreadRadius: 1),
            const BoxShadow(color: Color(0x0A000000), blurRadius: 8, offset: Offset(0, 2)),
          ],
        ),
        child: Row(
          children: [
            // 아이콘 영역
            Container(
              width: 48,
              height: 48,
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [benefit.gradientStart, benefit.gradientEnd],
                ),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Icon(benefit.icon, color: Colors.white, size: 24),
            ),
            const SizedBox(width: 12),
            // 텍스트
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    benefit.title,
                    style: const TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.w700,
                      letterSpacing: -0.1,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    benefit.subtitle,
                    style: const TextStyle(
                      fontSize: 12,
                      color: Color(0xFF6B7280),
                      height: 1.4,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(width: 8),
            // 상태 칩 + 경고
            Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                BenefitChip(
                  label: benefit.statusLabel,
                  backgroundColor: isUrgent
                      ? Colors.red.withValues(alpha: 0.1)
                      : benefit.gradientStart.withValues(alpha: 0.12),
                  textColor: isUrgent ? Colors.red : benefit.gradientStart,
                  icon: isUrgent ? Icons.warning_amber_rounded : null,
                ),
                if (isUrgent) ...[
                  const SizedBox(height: 3),
                  const Text(
                    '조기마감 임박',
                    style: TextStyle(
                      fontSize: 10,
                      color: Colors.red,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                ],
              ],
            ),
            const SizedBox(width: 4),
            Icon(Icons.chevron_right, color: Colors.grey[400], size: 18),
          ],
        ),
      ),
    );
  }
}

/// 축제 리스트 아이템
class _FestivalListItem extends StatelessWidget {
  final Map<String, String> festival;
  const _FestivalListItem({required this.festival});

  String get _statusLabel {
    try {
      final now = DateTime.now();
      final startStr = festival['startDate'] ?? '';
      final endStr = festival['endDate'] ?? '';

      if (startStr.isEmpty || endStr.isEmpty) return '기간 미정';

      final start = DateTime.parse(startStr);
      final end = DateTime.parse(endStr).add(const Duration(days: 1));

      if (now.isAfter(start) && now.isBefore(end)) {
        return '진행 중 🔥';
      } else if (now.isBefore(start)) {
        final diff = start
            .difference(DateTime(now.year, now.month, now.day))
            .inDays;
        return 'D-$diff';
      } else {
        return '종료됨';
      }
    } catch (e) {
      return '확인 불가';
    }
  }

  Color get _statusColor {
    final label = _statusLabel;
    if (label.contains('진행')) return const Color(0xFF1B8C6E);
    if (label.contains('D-')) return const Color(0xFF5C4AE3);
    return Colors.grey;
  }

  @override
  Widget build(BuildContext context) {
    final startDate = festival['startDate'] ?? '';
    final endDate = festival['endDate'] ?? '';

    // YYYY-MM-DD -> MM.DD 형식으로 변환 (날짜가 있을 때만)
    String format(String s) =>
        (s.length >= 10) ? s.substring(5).replaceAll('-', '.') : '';
    final formattedStart = format(startDate);
    final formattedEnd = format(endDate);
    final hasDate = formattedStart.isNotEmpty && formattedEnd.isNotEmpty;

    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 5),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          const BoxShadow(color: Color(0x05000000), blurRadius: 0, spreadRadius: 1),
          const BoxShadow(color: Color(0x0A000000), blurRadius: 8, offset: Offset(0, 2)),
        ],
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
                  festival['title'] ?? '행사 정보 없음',
                  style: const TextStyle(
                    fontWeight: FontWeight.w700,
                    fontSize: 14,
                    color: Color(0xFF1A1A1A),
                    letterSpacing: -0.1,
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 3),
                Row(
                  children: [
                    Flexible(
                      child: Text(
                        festival['area'] ?? '지역 정보 없음',
                        style: const TextStyle(
                          fontSize: 12,
                          color: Color(0xFF6B7280),
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                    if (hasDate) ...[
                      const SizedBox(width: 6),
                      Text(
                        '·  $formattedStart ~ $formattedEnd',
                        style: TextStyle(
                          fontSize: 12,
                          color: _statusColor,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ],
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(width: 8),
          BenefitChip(
            label: _statusLabel,
            backgroundColor: _statusColor.withValues(alpha: 0.08),
            textColor: _statusColor,
          ),
        ],
      ),
    );
  }
}
