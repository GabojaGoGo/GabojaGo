import 'package:flutter/material.dart';
import '../models/user_prefs.dart';
import '../services/api_service.dart';
import '../services/user_data_service.dart';
import '../widgets/shimmer_box.dart';
import 'course_detail_screen.dart';
import 'receipt_screen.dart';
import 'travel_setup_screen.dart';

// ══════════════════════════════════════════════════════════
// 플래너 화면
// ─ Tab 1: AI 추천 코스 (취향 기반, API 호출)
// ─ Tab 2: 저장한 코스 (로컬 SharedPrefs)
// ══════════════════════════════════════════════════════════

class PlannerScreen extends StatefulWidget {
  const PlannerScreen({super.key});

  @override
  State<PlannerScreen> createState() => _PlannerScreenState();
}

class _PlannerScreenState extends State<PlannerScreen>
    with SingleTickerProviderStateMixin {
  late final TabController _tabCtrl;

  // ── Tab1 상태 ────────────────────────────────────────
  List<Map<String, dynamic>> _recommended = [];
  bool _loading = false;
  bool _hasError = false;
  UserPrefs? _loadedPrefs;

  @override
  void initState() {
    super.initState();
    _tabCtrl = TabController(length: 2, vsync: this);
    // 탭 전환 시 저장 코스 배지 카운트 갱신
    _tabCtrl.addListener(() => setState(() {}));
  }

  @override
  void dispose() {
    _tabCtrl.dispose();
    super.dispose();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final prefs = UserPrefsScope.of(context).prefs;
    // 취향이 바뀌었을 때만 리로드
    if (prefs.hasPrefs &&
        (prefs.purposes.toString() != _loadedPrefs?.purposes.toString() ||
            prefs.duration != _loadedPrefs?.duration)) {
      _loadCourses(prefs);
    }
  }

  Future<void> _loadCourses(UserPrefs prefs) async {
    setState(() {
      _loading = true;
      _hasError = false;
      _loadedPrefs = prefs;
    });
    try {
      final courses = await ApiService.getCoursesWithDetail(
        purposes: prefs.purposes,
        duration: prefs.duration,
      );
      if (mounted) {
        setState(() {
          _recommended = courses;
          _loading = false;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _recommended = [];
          _loading = false;
          _hasError = true;
        });
      }
    }
  }

  void _goToSetup() {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => const TravelSetupScreen(showCourseResult: false),
      ),
    );
  }

  // 저장/삭제 이벤트 발생 시 전체 setState → 탭 배지 즉시 갱신
  void _onSaveChanged() => setState(() {});

  @override
  Widget build(BuildContext context) {
    final prefs = UserPrefsScope.of(context).prefs;
    final savedCount = UserDataService.instance.getSavedCourses().length;
    final colorScheme = Theme.of(context).colorScheme;

    return Scaffold(
      appBar: AppBar(
        title: const Text('AI 여행 플래너'),
        actions: [
          IconButton(
            icon: const Icon(Icons.receipt_long_outlined),
            onPressed: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const ReceiptScreen()),
            ),
            tooltip: '영수증 스캔',
          ),
        ],
        bottom: TabBar(
          controller: _tabCtrl,
          labelColor: colorScheme.primary,
          unselectedLabelColor: const Color(0xFF9CA3AF),
          indicatorColor: colorScheme.primary,
          indicatorWeight: 2,
          labelStyle: const TextStyle(
              fontWeight: FontWeight.w700, fontSize: 14),
          unselectedLabelStyle: const TextStyle(
              fontWeight: FontWeight.w500, fontSize: 14),
          tabs: [
            const Tab(text: 'AI 추천'),
            Tab(
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Text('저장한 코스'),
                  if (savedCount > 0) ...[
                    const SizedBox(width: 6),
                    Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 6, vertical: 1),
                      decoration: BoxDecoration(
                        color: colorScheme.primary,
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Text(
                        '$savedCount',
                        style: const TextStyle(
                            color: Colors.white,
                            fontSize: 11,
                            fontWeight: FontWeight.w700),
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tabCtrl,
        children: [
          // ─ Tab 1: AI 추천 ──────────────────────────
          _RecommendedTab(
            prefs: prefs,
            courses: _recommended,
            loading: _loading,
            hasError: _hasError,
            onGoToSetup: _goToSetup,
            onSaveChanged: _onSaveChanged,
            onRefresh: () => _loadCourses(prefs),
          ),
          // ─ Tab 2: 저장한 코스 ──────────────────────
          _SavedCoursesTab(
            onSaveChanged: _onSaveChanged,
            onGoToRecommended: () => _tabCtrl.animateTo(0),
          ),
        ],
      ),
    );
  }
}

// ══════════════════════════════════════════════════════════
// Tab 1 — AI 추천 코스
// ══════════════════════════════════════════════════════════

class _RecommendedTab extends StatelessWidget {
  final UserPrefs prefs;
  final List<Map<String, dynamic>> courses;
  final bool loading;
  final bool hasError;
  final VoidCallback onGoToSetup;
  final VoidCallback onSaveChanged;
  final Future<void> Function() onRefresh;

  const _RecommendedTab({
    required this.prefs,
    required this.courses,
    required this.loading,
    required this.hasError,
    required this.onGoToSetup,
    required this.onSaveChanged,
    required this.onRefresh,
  });

  @override
  Widget build(BuildContext context) {
    // 취향 없는 경우
    if (!prefs.hasPrefs) {
      return _EmptySetup(onGoToSetup: onGoToSetup);
    }

    return RefreshIndicator(
      onRefresh: onRefresh,
      color: const Color(0xFF2E7D6B),
      child: CustomScrollView(
        physics: const AlwaysScrollableScrollPhysics(),
        slivers: [
          // 취향 요약 헤더
          SliverToBoxAdapter(
            child: _PrefsHeader(prefs: prefs, onGoToSetup: onGoToSetup),
          ),

          // 결과 카운트 또는 오류
          if (!loading)
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
              sliver: SliverToBoxAdapter(
                child: hasError
                    ? _ErrorBanner(onRetry: onRefresh)
                    : Text(
                        courses.isEmpty
                            ? '조건에 맞는 코스를 찾는 중이에요'
                            : '취향 맞춤 코스 ${courses.length}개',
                        style: const TextStyle(
                          fontSize: 13,
                          color: Color(0xFF6B7280),
                          fontWeight: FontWeight.w500,
                        ),
                      ),
              ),
            ),

          // 로딩 shimmer
          if (loading)
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 32),
              sliver: SliverList(
                delegate: SliverChildBuilderDelegate(
                  (_, _) => const CourseCardShimmer(),
                  childCount: 3,
                ),
              ),
            ),

          // 코스 카드 목록
          if (!loading && courses.isNotEmpty)
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 32),
              sliver: SliverList(
                delegate: SliverChildBuilderDelegate(
                  (context, i) => Padding(
                    padding: const EdgeInsets.only(bottom: 16),
                    child: _CourseCard(
                      course: courses[i],
                      purposes: prefs.purposes,
                      onSaveChanged: onSaveChanged,
                    ),
                  ),
                  childCount: courses.length,
                ),
              ),
            ),

          // 코스 없음
          if (!loading && !hasError && courses.isEmpty)
            SliverFillRemaining(
              hasScrollBody: false,
              child: Center(
                child: Padding(
                  padding: const EdgeInsets.all(32),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Text('😔', style: TextStyle(fontSize: 40)),
                      const SizedBox(height: 12),
                      const Text('조건에 맞는 코스가 없어요',
                          style: TextStyle(
                              fontSize: 15, fontWeight: FontWeight.w600)),
                      const SizedBox(height: 8),
                      TextButton(
                        onPressed: onGoToSetup,
                        child: const Text('다른 취향으로 다시 추천받기'),
                      ),
                    ],
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

// ══════════════════════════════════════════════════════════
// Tab 2 — 저장한 코스
// ══════════════════════════════════════════════════════════

class _SavedCoursesTab extends StatelessWidget {
  final VoidCallback onSaveChanged;
  final VoidCallback onGoToRecommended;

  const _SavedCoursesTab({
    required this.onSaveChanged,
    required this.onGoToRecommended,
  });

  @override
  Widget build(BuildContext context) {
    final saved = UserDataService.instance.getSavedCourses();

    if (saved.isEmpty) {
      return _EmptySaved(onGoToRecommended: onGoToRecommended);
    }

    return CustomScrollView(
      slivers: [
        SliverPadding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 4),
          sliver: SliverToBoxAdapter(
            child: Text(
              '${saved.length}개의 코스를 저장했어요',
              style: const TextStyle(
                fontSize: 13,
                color: Color(0xFF6B7280),
                fontWeight: FontWeight.w500,
              ),
            ),
          ),
        ),
        SliverPadding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
          sliver: SliverList(
            delegate: SliverChildBuilderDelegate(
              (context, i) {
                final course = saved[i];
                return Padding(
                  padding: const EdgeInsets.only(bottom: 16),
                  child: _SavedCourseCard(
                    course: course,
                    onRemove: () async {
                      final contentId =
                          course['contentId'] as String? ?? '';
                      await UserDataService.instance
                          .removeSavedCourse(contentId);
                      onSaveChanged();
                    },
                  ),
                );
              },
              childCount: saved.length,
            ),
          ),
        ),
      ],
    );
  }
}

// ══════════════════════════════════════════════════════════
// 취향 요약 헤더
// ══════════════════════════════════════════════════════════

class _PrefsHeader extends StatelessWidget {
  final UserPrefs prefs;
  final VoidCallback onGoToSetup;

  const _PrefsHeader({required this.prefs, required this.onGoToSetup});

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final purposeLabels = prefs.purposes
        .map((k) => kPurposeOptions
            .firstWhere((o) => o['key'] == k, orElse: () => {'label': k})['label']!)
        .toList();
    final durationLabel = prefs.duration.isNotEmpty
        ? kDurationOptions
            .firstWhere((o) => o['key'] == prefs.duration,
                orElse: () => {'label': prefs.duration})['label']!
        : '';

    return Container(
      margin: const EdgeInsets.fromLTRB(16, 16, 16, 0),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: colorScheme.primary.withValues(alpha: 0.06),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: colorScheme.primary.withValues(alpha: 0.15)),
      ),
      child: Row(
        children: [
          Expanded(
            child: Wrap(
              spacing: 6,
              runSpacing: 4,
              children: [
                ...purposeLabels.map((l) => _PrefChip(label: l)),
                if (durationLabel.isNotEmpty)
                  _PrefChip(label: durationLabel, isDuration: true),
              ],
            ),
          ),
          TextButton(
            onPressed: onGoToSetup,
            style: TextButton.styleFrom(
              foregroundColor: colorScheme.primary,
              padding:
                  const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
              minimumSize: Size.zero,
            ),
            child: const Text('다른\n취향',
                textAlign: TextAlign.center,
                style:
                    TextStyle(fontSize: 11, fontWeight: FontWeight.w600)),
          ),
        ],
      ),
    );
  }
}

// ══════════════════════════════════════════════════════════
// 추천 코스 카드
// ══════════════════════════════════════════════════════════

class _CourseCard extends StatefulWidget {
  final Map<String, dynamic> course;
  final List<String> purposes;
  final VoidCallback onSaveChanged;

  const _CourseCard({
    required this.course,
    required this.purposes,
    required this.onSaveChanged,
  });

  @override
  State<_CourseCard> createState() => _CourseCardState();
}

class _CourseCardState extends State<_CourseCard> {
  late bool _saved;

  @override
  void initState() {
    super.initState();
    _saved = UserDataService.instance
        .isCourseSaved(widget.course['contentId'] as String? ?? '');
  }

  Future<void> _toggleSave() async {
    final contentId = widget.course['contentId'] as String? ?? '';
    if (_saved) {
      await UserDataService.instance.removeSavedCourse(contentId);
    } else {
      await UserDataService.instance.saveCourse(widget.course);
    }
    if (mounted) setState(() => _saved = !_saved);
    widget.onSaveChanged();
    if (mounted) {
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(
          content: Text(_saved ? '플래너에 저장됐어요!' : '플래너에서 삭제됐어요'),
          duration: const Duration(seconds: 2),
          behavior: SnackBarBehavior.floating,
          shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(10)),
        ));
    }
  }

  void _goToDetail() {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => CourseDetailScreen(
          course: widget.course,
          purposes: widget.purposes,
        ),
      ),
    ).then((_) {
      final contentId = widget.course['contentId'] as String? ?? '';
      if (mounted) {
        setState(() => _saved =
            UserDataService.instance.isCourseSaved(contentId));
      }
      widget.onSaveChanged();
    });
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final places = List<Map<String, dynamic>>.from(
        (widget.course['places'] as List? ?? [])
            .map((e) => Map<String, dynamic>.from(e as Map)));
    final distance = widget.course['distance'] as String? ?? '';
    final taketime = widget.course['taketime'] as String? ?? '';
    final region = widget.course['region'] as String? ?? '';
    final imageUrl = widget.course['imageUrl'] as String? ?? '';
    final hasImage =
        imageUrl.isNotEmpty && !imageUrl.contains('placeholder');

    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          const BoxShadow(
              color: Color(0x05000000), blurRadius: 0, spreadRadius: 1),
          const BoxShadow(
              color: Color(0x0A000000),
              blurRadius: 8,
              offset: Offset(0, 2)),
          const BoxShadow(
              color: Color(0x14000000),
              blurRadius: 16,
              offset: Offset(0, 4)),
        ],
      ),
      clipBehavior: Clip.antiAlias,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 이미지 (있을 때만)
          if (hasImage)
            Image.network(
              imageUrl,
              height: 140,
              width: double.infinity,
              fit: BoxFit.cover,
              loadingBuilder: (_, child, p) =>
                  p == null ? child : const ShimmerBox(height: 140, radius: 0),
              errorBuilder: (_, _, _) => _CoursePlaceholder(course: widget.course),
            )
          else
            _CoursePlaceholder(course: widget.course),

          Padding(
            padding: const EdgeInsets.fromLTRB(16, 14, 16, 0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // 지역
                if (region.isNotEmpty)
                  Row(
                    children: [
                      Icon(Icons.location_on_outlined,
                          size: 13, color: colorScheme.primary),
                      const SizedBox(width: 2),
                      Text(region,
                          style: TextStyle(
                              fontSize: 12,
                              color: colorScheme.primary,
                              fontWeight: FontWeight.w600)),
                    ],
                  ),
                if (region.isNotEmpty) const SizedBox(height: 5),

                // 코스명
                Text(
                  widget.course['title'] as String? ?? '',
                  style: const TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w800,
                    letterSpacing: -0.3,
                  ),
                ),
                const SizedBox(height: 10),

                // 거리 / 소요 시간
                if (distance.isNotEmpty || taketime.isNotEmpty) ...[
                  Row(
                    children: [
                      if (distance.isNotEmpty) ...[
                        _InfoPill(
                            icon: Icons.straighten_outlined,
                            label: distance),
                        const SizedBox(width: 8),
                      ],
                      if (taketime.isNotEmpty)
                        _InfoPill(
                            icon: Icons.schedule_outlined, label: taketime),
                    ],
                  ),
                  const SizedBox(height: 12),
                ],

                // 장소 목록
                if (places.isNotEmpty) ...[
                  ...List.generate(
                    places.length.clamp(0, 4),
                    (i) => _PlaceRow(
                      index: i,
                      place: places[i],
                      isLast: i == places.length.clamp(0, 4) - 1,
                    ),
                  ),
                  if (places.length > 4)
                    Padding(
                      padding: const EdgeInsets.only(left: 30, top: 4),
                      child: Text(
                        '+ ${places.length - 4}곳 더 보기',
                        style: const TextStyle(
                            fontSize: 12, color: Color(0xFF9CA3AF)),
                      ),
                    ),
                  const SizedBox(height: 12),
                ] else if ((widget.course['overview'] as String? ?? '').isNotEmpty) ...[
                  Text(
                    widget.course['overview'] as String,
                    style: const TextStyle(
                        fontSize: 13,
                        color: Color(0xFF6B7280),
                        height: 1.6),
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                  ),
                  const SizedBox(height: 12),
                ],
              ],
            ),
          ),

          // 버튼 영역
          const Divider(height: 1),
          Padding(
            padding: const EdgeInsets.all(12),
            child: Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: _goToDetail,
                    style: OutlinedButton.styleFrom(
                      foregroundColor: colorScheme.primary,
                      side: BorderSide(color: colorScheme.primary),
                      padding: const EdgeInsets.symmetric(vertical: 10),
                      shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(10)),
                    ),
                    child: const Text('코스 상세 보기',
                        style: TextStyle(
                            fontWeight: FontWeight.w600, fontSize: 13)),
                  ),
                ),
                const SizedBox(width: 8),
                _BookmarkButton(
                  saved: _saved,
                  onTap: _toggleSave,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

// ══════════════════════════════════════════════════════════
// 저장된 코스 카드 (Tab2 전용 — 삭제 버튼 포함)
// ══════════════════════════════════════════════════════════

class _SavedCourseCard extends StatelessWidget {
  final Map<String, dynamic> course;
  final VoidCallback onRemove;

  const _SavedCourseCard({required this.course, required this.onRemove});

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final region = course['region'] as String? ?? '';
    final title = course['title'] as String? ?? '';
    final imageUrl = course['imageUrl'] as String? ?? '';
    final hasImage =
        imageUrl.isNotEmpty && !imageUrl.contains('placeholder');
    final places = List<Map<String, dynamic>>.from(
        (course['places'] as List? ?? [])
            .map((e) => Map<String, dynamic>.from(e as Map)));
    final distance = course['distance'] as String? ?? '';
    final taketime = course['taketime'] as String? ?? '';

    return Dismissible(
      key: ValueKey(course['contentId']),
      direction: DismissDirection.endToStart,
      background: Container(
        alignment: Alignment.centerRight,
        padding: const EdgeInsets.only(right: 20),
        decoration: BoxDecoration(
          color: const Color(0xFFEF4444),
          borderRadius: BorderRadius.circular(16),
        ),
        child: const Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.delete_outline, color: Colors.white, size: 24),
            SizedBox(height: 4),
            Text('삭제', style: TextStyle(color: Colors.white, fontSize: 11, fontWeight: FontWeight.w600)),
          ],
        ),
      ),
      confirmDismiss: (_) async {
        return await showDialog<bool>(
          context: context,
          builder: (_) => AlertDialog(
            shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(16)),
            title: const Text('코스 삭제',
                style: TextStyle(fontWeight: FontWeight.w800)),
            content: Text('"$title"을(를)\n플래너에서 삭제할까요?',
                style: const TextStyle(height: 1.5)),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context, false),
                child: const Text('취소',
                    style: TextStyle(color: Color(0xFF6B7280))),
              ),
              TextButton(
                onPressed: () => Navigator.pop(context, true),
                child: const Text('삭제',
                    style: TextStyle(
                        color: Color(0xFFEF4444),
                        fontWeight: FontWeight.w700)),
              ),
            ],
          ),
        ) ?? false;
      },
      onDismissed: (_) => onRemove(),
      child: Container(
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(16),
          boxShadow: [
            const BoxShadow(
                color: Color(0x05000000), blurRadius: 0, spreadRadius: 1),
            const BoxShadow(
                color: Color(0x0A000000),
                blurRadius: 8,
                offset: Offset(0, 2)),
            const BoxShadow(
                color: Color(0x14000000),
                blurRadius: 16,
                offset: Offset(0, 4)),
          ],
        ),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          borderRadius: BorderRadius.circular(16),
          onTap: () => Navigator.push(
            context,
            MaterialPageRoute(
              builder: (_) => CourseDetailScreen(course: course),
            ),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // 이미지
              if (hasImage)
                Image.network(
                  imageUrl,
                  height: 130,
                  width: double.infinity,
                  fit: BoxFit.cover,
                  loadingBuilder: (_, child, p) =>
                      p == null ? child : const ShimmerBox(height: 130, radius: 0),
                  errorBuilder: (_, _, _) =>
                      _CoursePlaceholder(course: course, height: 80),
                )
              else
                _CoursePlaceholder(course: course, height: 80),

              Padding(
                padding: const EdgeInsets.all(14),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // 지역
                    if (region.isNotEmpty)
                      Row(
                        children: [
                          Icon(Icons.location_on_outlined,
                              size: 12, color: colorScheme.primary),
                          const SizedBox(width: 2),
                          Text(region,
                              style: TextStyle(
                                  fontSize: 11,
                                  color: colorScheme.primary,
                                  fontWeight: FontWeight.w600)),
                        ],
                      ),
                    if (region.isNotEmpty) const SizedBox(height: 4),
                    Text(
                      title,
                      style: const TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.w800,
                        letterSpacing: -0.3,
                      ),
                    ),
                    // 거리/시간 정보
                    if (distance.isNotEmpty || taketime.isNotEmpty) ...[
                      const SizedBox(height: 8),
                      Row(
                        children: [
                          if (distance.isNotEmpty) ...[
                            _InfoPill(
                                icon: Icons.straighten_outlined,
                                label: distance),
                            const SizedBox(width: 6),
                          ],
                          if (taketime.isNotEmpty)
                            _InfoPill(
                                icon: Icons.schedule_outlined,
                                label: taketime),
                        ],
                      ),
                    ],
                    // 장소 미리보기
                    if (places.isNotEmpty) ...[
                      const SizedBox(height: 10),
                      _PlacesPreviewRow(places: places),
                    ],
                  ],
                ),
              ),

              // 하단 버튼 영역
              const Divider(height: 1),
              Padding(
                padding: const EdgeInsets.symmetric(
                    horizontal: 12, vertical: 10),
                child: Row(
                  children: [
                    const Icon(Icons.touch_app_outlined,
                        size: 14, color: Color(0xFF9CA3AF)),
                    const SizedBox(width: 4),
                    const Text('탭해서 코스 상세 보기',
                        style: TextStyle(
                            fontSize: 12, color: Color(0xFF9CA3AF))),
                    const Spacer(),
                    GestureDetector(
                      onTap: onRemove,
                      child: Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 10, vertical: 5),
                        decoration: BoxDecoration(
                          color: const Color(0xFFFEF2F2),
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: const Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(Icons.delete_outline,
                                size: 14, color: Color(0xFFEF4444)),
                            SizedBox(width: 3),
                            Text('삭제',
                                style: TextStyle(
                                    fontSize: 12,
                                    color: Color(0xFFEF4444),
                                    fontWeight: FontWeight.w600)),
                          ],
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

// ══════════════════════════════════════════════════════════
// 빈 상태 위젯들
// ══════════════════════════════════════════════════════════

class _EmptySetup extends StatelessWidget {
  final VoidCallback onGoToSetup;
  const _EmptySetup({required this.onGoToSetup});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('🗺️', style: TextStyle(fontSize: 56)),
            const SizedBox(height: 20),
            const Text('취향을 먼저 설정해보세요',
                style: TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.w700,
                    letterSpacing: -0.3)),
            const SizedBox(height: 8),
            const Text(
              '취향에 딱 맞는 맞춤 코스를\n추천해드릴게요',
              textAlign: TextAlign.center,
              style: TextStyle(
                  fontSize: 14,
                  color: Color(0xFF6B7280),
                  height: 1.6),
            ),
            const SizedBox(height: 28),
            FilledButton.icon(
              onPressed: onGoToSetup,
              icon: const Icon(Icons.tune_outlined, size: 18),
              label: const Text('취향 설정하기'),
              style: FilledButton.styleFrom(
                padding: const EdgeInsets.symmetric(
                    horizontal: 24, vertical: 14),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12)),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _EmptySaved extends StatelessWidget {
  final VoidCallback onGoToRecommended;
  const _EmptySaved({required this.onGoToRecommended});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('🔖', style: TextStyle(fontSize: 56)),
            const SizedBox(height: 20),
            const Text('저장한 코스가 없어요',
                style: TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.w700,
                    letterSpacing: -0.3)),
            const SizedBox(height: 8),
            const Text(
              'AI 추천 탭에서 마음에 드는 코스를\n저장해 모아두세요',
              textAlign: TextAlign.center,
              style: TextStyle(
                  fontSize: 14,
                  color: Color(0xFF6B7280),
                  height: 1.6),
            ),
            const SizedBox(height: 28),
            FilledButton.icon(
              onPressed: onGoToRecommended,
              icon: const Icon(Icons.auto_awesome_outlined, size: 18),
              label: const Text('AI 추천 코스 받기'),
              style: FilledButton.styleFrom(
                padding: const EdgeInsets.symmetric(
                    horizontal: 24, vertical: 14),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12)),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _ErrorBanner extends StatelessWidget {
  final Future<void> Function() onRetry;
  const _ErrorBanner({required this.onRetry});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: const Color(0xFFFFF3CD),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          const Icon(Icons.wifi_off_outlined,
              size: 18, color: Color(0xFFF59E0B)),
          const SizedBox(width: 10),
          const Expanded(
            child: Text('코스를 불러오지 못했어요',
                style: TextStyle(
                    fontSize: 13,
                    color: Color(0xFF92400E),
                    fontWeight: FontWeight.w500)),
          ),
          TextButton(
            onPressed: onRetry,
            style: TextButton.styleFrom(
              foregroundColor: const Color(0xFFF59E0B),
              padding: EdgeInsets.zero,
              minimumSize: Size.zero,
            ),
            child: const Text('재시도',
                style: TextStyle(fontWeight: FontWeight.w700)),
          ),
        ],
      ),
    );
  }
}

// ══════════════════════════════════════════════════════════
// 공통 소형 위젯
// ══════════════════════════════════════════════════════════

/// 코스 이미지 그라디언트 플레이스홀더
class _CoursePlaceholder extends StatelessWidget {
  final Map<String, dynamic> course;
  final double height;

  const _CoursePlaceholder({required this.course, this.height = 120});

  static const _colors = [
    [Color(0xFF2E7D6B), Color(0xFF1B5E4A)],
    [Color(0xFF1565C0), Color(0xFF0D47A1)],
    [Color(0xFF5C4AE3), Color(0xFF3D2FC4)],
    [Color(0xFFF57C00), Color(0xFFE65100)],
    [Color(0xFF388E3C), Color(0xFF1B5E20)],
  ];

  @override
  Widget build(BuildContext context) {
    final title = course['title'] as String? ?? '';
    final idx = title.isEmpty ? 0 : title.codeUnitAt(0) % _colors.length;
    final region = course['region'] as String? ?? '';

    return Container(
      height: height,
      width: double.infinity,
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: _colors[idx],
        ),
      ),
      padding: const EdgeInsets.fromLTRB(14, 10, 14, 10),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisAlignment: MainAxisAlignment.end,
        children: [
          if (region.isNotEmpty)
            Text(region,
                style: const TextStyle(
                    color: Colors.white70,
                    fontSize: 11,
                    fontWeight: FontWeight.w500)),
          if (title.isNotEmpty) ...[
            if (region.isNotEmpty) const SizedBox(height: 2),
            Text(title,
                style: const TextStyle(
                    color: Colors.white,
                    fontSize: 14,
                    fontWeight: FontWeight.w700,
                    letterSpacing: -0.2),
                maxLines: 1,
                overflow: TextOverflow.ellipsis),
          ],
        ],
      ),
    );
  }
}

/// 장소 이름 가로 나열 미리보기 (저장 코스 카드용)
class _PlacesPreviewRow extends StatelessWidget {
  final List<Map<String, dynamic>> places;
  const _PlacesPreviewRow({required this.places});

  static const _slotIcons = {
    'meal': Icons.restaurant_outlined,
    'lodging': Icons.hotel_outlined,
  };
  static const _slotColors = {
    'meal': Color(0xFFF97316),
    'lodging': Color(0xFF8B5CF6),
  };

  @override
  Widget build(BuildContext context) {
    final preview = places.take(4).toList();
    return Wrap(
      spacing: 4,
      runSpacing: 4,
      children: [
        for (int i = 0; i < preview.length; i++) ...[
          if (i > 0)
            const Icon(Icons.arrow_forward_ios,
                size: 8, color: Color(0xFFD1D5DB)),
          Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                _slotIcons[preview[i]['slotType']] ??
                    Icons.place_outlined,
                size: 11,
                color: _slotColors[preview[i]['slotType']] ??
                    const Color(0xFF2E7D6B),
              ),
              const SizedBox(width: 2),
              ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 72),
                child: Text(
                  preview[i]['subname'] as String? ?? '',
                  style: const TextStyle(
                      fontSize: 11, color: Color(0xFF6B7280)),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
          ),
        ],
        if (places.length > 4)
          Text('+${places.length - 4}',
              style: const TextStyle(
                  fontSize: 11, color: Color(0xFF9CA3AF))),
      ],
    );
  }
}

/// 북마크 버튼
class _BookmarkButton extends StatelessWidget {
  final bool saved;
  final VoidCallback onTap;
  const _BookmarkButton({required this.saved, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return IconButton(
      onPressed: onTap,
      icon: Icon(
        saved ? Icons.bookmark : Icons.bookmark_add_outlined,
        color: saved ? colorScheme.primary : const Color(0xFF9CA3AF),
      ),
      style: IconButton.styleFrom(
        backgroundColor: saved
            ? colorScheme.primary.withValues(alpha: 0.1)
            : const Color(0xFFF3F4F6),
        shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(10)),
      ),
      tooltip: saved ? '플래너에서 삭제' : '플래너에 담기',
    );
  }
}

/// 장소 행 (번호 원 + 연결선 + 이름 + 슬롯 아이콘)
class _PlaceRow extends StatelessWidget {
  final int index;
  final Map<String, dynamic> place;
  final bool isLast;

  const _PlaceRow({
    required this.index,
    required this.place,
    required this.isLast,
  });

  static const _slotColors = {
    'meal': Color(0xFFF97316),
    'lodging': Color(0xFF8B5CF6),
  };

  @override
  Widget build(BuildContext context) {
    final slotColor = _slotColors[place['slotType']] ??
        const Color(0xFF2E7D6B);
    return IntrinsicHeight(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 26,
            child: Column(
              children: [
                Container(
                  width: 22,
                  height: 22,
                  decoration: BoxDecoration(
                    color: slotColor,
                    shape: BoxShape.circle,
                  ),
                  alignment: Alignment.center,
                  child: Text(
                    '${index + 1}',
                    style: const TextStyle(
                        color: Colors.white,
                        fontSize: 11,
                        fontWeight: FontWeight.w700),
                  ),
                ),
                if (!isLast)
                  Expanded(
                    child: Container(
                      width: 2,
                      margin: const EdgeInsets.symmetric(vertical: 2),
                      color: slotColor.withValues(alpha: 0.2),
                    ),
                  ),
              ],
            ),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Padding(
              padding: EdgeInsets.only(bottom: isLast ? 0 : 10, top: 2),
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      place['subname'] as String? ?? '',
                      style: const TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w500,
                        color: Color(0xFF1A1A1A),
                      ),
                    ),
                  ),
                  if ((place['dayLabel'] as String? ?? '').isNotEmpty)
                    Text(
                      place['dayLabel'] as String,
                      style: const TextStyle(
                          fontSize: 10, color: Color(0xFF9CA3AF)),
                    ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// 정보 필 (거리, 소요시간)
class _InfoPill extends StatelessWidget {
  final IconData icon;
  final String label;
  const _InfoPill({required this.icon, required this.label});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: const Color(0xFFF3F4F6),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 13, color: const Color(0xFF6B7280)),
          const SizedBox(width: 3),
          Text(label,
              style: const TextStyle(
                  fontSize: 12,
                  color: Color(0xFF374151),
                  fontWeight: FontWeight.w500)),
        ],
      ),
    );
  }
}

/// 취향 칩
class _PrefChip extends StatelessWidget {
  final String label;
  final bool isDuration;
  const _PrefChip({required this.label, this.isDuration = false});

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: isDuration
            ? const Color(0xFF1565C0).withValues(alpha: 0.1)
            : colorScheme.primary.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Text(
        label,
        style: TextStyle(
          fontSize: 12,
          fontWeight: FontWeight.w600,
          color: isDuration
              ? const Color(0xFF1565C0)
              : colorScheme.primary,
        ),
      ),
    );
  }
}
