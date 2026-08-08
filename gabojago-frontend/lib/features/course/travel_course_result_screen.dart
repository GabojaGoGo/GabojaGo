import 'package:flutter/material.dart';
import 'package:tripmate/core/models/user_prefs.dart';
import 'package:tripmate/infrastructure/api_service.dart';
import 'package:tripmate/core/services/user_data_service.dart';
import 'package:tripmate/features/course/course_detail_screen.dart';

class TravelCourseResultScreen extends StatefulWidget {
  final UserPrefs prefs;
  final List<Map<String, dynamic>>? preloadedCourses;
  final String travelConcept;

  const TravelCourseResultScreen({
    super.key,
    required this.prefs,
    this.preloadedCourses,
    this.travelConcept = '',
  });

  @override
  State<TravelCourseResultScreen> createState() =>
      _TravelCourseResultScreenState();
}

class _TravelCourseResultScreenState extends State<TravelCourseResultScreen> {
  List<Map<String, dynamic>> _courses = [];
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    if (widget.preloadedCourses != null) {
      _courses = widget.preloadedCourses!;
      _loading = false;
    } else {
      _loadCourses();
    }
  }

  Future<void> _loadCourses() async {
    try {
      final courses = await ApiService.getCourses(
        purposes: widget.prefs.purposes,
        duration: widget.prefs.duration,
        travelConcept: widget.travelConcept,
      );
      if (mounted) {
        setState(() {
          _courses = courses;
          _loading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = e.toString();
          _loading = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final purposeLabels = widget.prefs.purposes
        .map(
          (k) => kPurposeOptions.firstWhere(
            (o) => o['key'] == k,
            orElse: () => {'label': k},
          )['label']!,
        )
        .toList();
    final durationLabel = kDurationOptions.firstWhere(
      (o) => o['key'] == widget.prefs.duration,
      orElse: () => {'label': widget.prefs.duration},
    )['label']!;

    return Scaffold(
      backgroundColor: colorScheme.surface,
      body: CustomScrollView(
        slivers: [
          // 히어로 헤더
          SliverAppBar(
            expandedHeight: 160,
            pinned: true,
            backgroundColor: colorScheme.primary,
            foregroundColor: Colors.white,
            flexibleSpace: FlexibleSpaceBar(
              background: Container(
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                    colors: [colorScheme.primary, colorScheme.tertiary],
                  ),
                ),
                child: SafeArea(
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(20, 48, 20, 16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisAlignment: MainAxisAlignment.end,
                      children: [
                        const Text(
                          '맞춤 추천 코스',
                          style: TextStyle(
                            color: Colors.white,
                            fontSize: 22,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                        const SizedBox(height: 8),
                        SingleChildScrollView(
                          scrollDirection: Axis.horizontal,
                          child: Row(
                            children: [
                              ...purposeLabels.map(
                                (label) => Padding(
                                  padding: const EdgeInsets.only(right: 6),
                                  child: _SummaryChip(label: label),
                                ),
                              ),
                              if (widget.prefs.duration.isNotEmpty)
                                _SummaryChip(label: durationLabel),
                              if (widget.travelConcept.isNotEmpty)
                                _SummaryChip(label: widget.travelConcept),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ),

          // 로딩
          if (_loading)
            const SliverFillRemaining(
              child: Center(child: CircularProgressIndicator()),
            ),

          // 에러
          if (!_loading && _error != null)
            SliverFillRemaining(
              child: Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Text('🗺️', style: TextStyle(fontSize: 48)),
                    const SizedBox(height: 12),
                    const Text(
                      '코스를 불러오지 못했어요',
                      style: TextStyle(fontSize: 16, color: Colors.grey),
                    ),
                    const SizedBox(height: 8),
                    TextButton(
                      onPressed: () {
                        setState(() {
                          _loading = true;
                          _error = null;
                        });
                        _loadCourses();
                      },
                      child: const Text('다시 시도'),
                    ),
                  ],
                ),
              ),
            ),

          // 결과 없음
          if (!_loading && _error == null && _courses.isEmpty)
            const SliverFillRemaining(
              child: Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text('🗺️', style: TextStyle(fontSize: 48)),
                    SizedBox(height: 12),
                    Text(
                      '조건에 맞는 코스를 찾고 있어요',
                      style: TextStyle(fontSize: 16, color: Colors.grey),
                    ),
                  ],
                ),
              ),
            ),

          // 코스 리스트
          if (!_loading && _courses.isNotEmpty) ...[
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(20, 20, 20, 8),
              sliver: SliverToBoxAdapter(
                child: Text(
                  '자동차와 도보, ${_courses.length}개 균형 코스를 찾았어요',
                  style: TextStyle(
                    fontSize: 14,
                    color: colorScheme.onSurface.withValues(alpha: 0.6),
                  ),
                ),
              ),
            ),
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(20, 0, 20, 32),
              sliver: SliverList(
                delegate: SliverChildBuilderDelegate(
                  (context, i) => Padding(
                    padding: const EdgeInsets.only(bottom: 16),
                    child: _CourseCard(
                      course: _courses[i],
                      purposes: widget.prefs.purposes,
                      travelConcept: widget.travelConcept,
                    ),
                  ),
                  childCount: _courses.length,
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

// ─── 취향 요약 칩 ──────────────────────────────────────────
class _SummaryChip extends StatelessWidget {
  final String label;
  const _SummaryChip({required this.label});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.25),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Text(
        label,
        style: const TextStyle(
          color: Colors.white,
          fontSize: 12,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }
}

// ─── 코스 카드 ─────────────────────────────────────────────
class _CourseCard extends StatefulWidget {
  final Map<String, dynamic> course;
  final List<String> purposes;
  final String travelConcept;
  const _CourseCard({
    required this.course,
    this.purposes = const [],
    this.travelConcept = '',
  });

  @override
  State<_CourseCard> createState() => _CourseCardState();
}

class _CourseCardState extends State<_CourseCard> {
  late bool _saved;

  @override
  void initState() {
    super.initState();
    _saved = UserDataService.instance.isCourseSaved(
      widget.course['contentId'] as String? ?? '',
    );
  }

  Future<void> _toggleSave() async {
    final contentId = widget.course['contentId'] as String? ?? '';
    if (_saved) {
      await UserDataService.instance.removeSavedCourse(contentId);
      if (mounted) setState(() => _saved = false);
    } else {
      await UserDataService.instance.saveCourse(widget.course);
      if (mounted) setState(() => _saved = true);
    }
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(_saved ? '플래너에 저장됐어요!' : '플래너에서 삭제됐어요')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final imageUrl = widget.course['imageUrl'] as String? ?? '';
    final hasImage = imageUrl.isNotEmpty && !imageUrl.contains('placeholder');
    final overview = widget.course['overview'] as String? ?? '';

    return GestureDetector(
      onTap: () =>
          Navigator.push(
            context,
            MaterialPageRoute(
              builder: (_) => CourseDetailScreen(
                course: widget.course,
                purposes: widget.purposes,
                travelConcept: widget.travelConcept,
              ),
            ),
          ).then((_) {
            // 상세 화면에서 저장 토글 시 카드 상태 갱신
            final contentId = widget.course['contentId'] as String? ?? '';
            if (mounted) {
              setState(
                () =>
                    _saved = UserDataService.instance.isCourseSaved(contentId),
              );
            }
          }),
      child: Container(
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(16),
          boxShadow: [
            const BoxShadow(
              color: Color(0x05000000),
              blurRadius: 0,
              spreadRadius: 1,
            ),
            const BoxShadow(
              color: Color(0x0A000000),
              blurRadius: 8,
              offset: Offset(0, 2),
            ),
            const BoxShadow(
              color: Color(0x14000000),
              blurRadius: 16,
              offset: Offset(0, 4),
            ),
          ],
        ),
        clipBehavior: Clip.antiAlias,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (hasImage)
              Image.network(
                imageUrl,
                height: 160,
                width: double.infinity,
                fit: BoxFit.cover,
                loadingBuilder: (context, child, progress) {
                  if (progress == null) return child;
                  return _CourseImagePlaceholder(
                    course: widget.course,
                    height: 160,
                  );
                },
                errorBuilder: (context, error, stack) =>
                    _CourseImagePlaceholder(course: widget.course, height: 160),
              )
            else
              _CourseImagePlaceholder(course: widget.course, height: 100),

            Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if ((widget.course['relevanceScore'] as int? ?? 0) >= 70)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 6),
                      child: Chip(
                        label: const Text('복합 취향 최적'),
                        labelStyle: TextStyle(
                          fontSize: 11,
                          color: colorScheme.onPrimary,
                          fontWeight: FontWeight.w600,
                        ),
                        backgroundColor: colorScheme.primary,
                        padding: const EdgeInsets.symmetric(horizontal: 4),
                        materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
                        visualDensity: VisualDensity.compact,
                      ),
                    ),
                  _TravelModeChip(
                    travelMode: widget.course['travelMode'] as String? ?? 'CAR',
                  ),
                  const SizedBox(height: 6),
                  if ((widget.course['region'] as String? ?? '').isNotEmpty)
                    Row(
                      children: [
                        Icon(
                          Icons.location_on_outlined,
                          size: 14,
                          color: colorScheme.primary,
                        ),
                        const SizedBox(width: 2),
                        Text(
                          widget.course['region'] as String,
                          style: TextStyle(
                            fontSize: 13,
                            color: colorScheme.primary,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        const Spacer(),
                        Text(
                          '상세 보기',
                          style: TextStyle(
                            fontSize: 12,
                            color: colorScheme.primary.withValues(alpha: 0.7),
                          ),
                        ),
                        Icon(
                          Icons.chevron_right,
                          size: 16,
                          color: colorScheme.primary.withValues(alpha: 0.7),
                        ),
                      ],
                    ),
                  const SizedBox(height: 6),

                  Text(
                    widget.course['title'] as String? ?? '',
                    style: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w800,
                      letterSpacing: -0.3,
                    ),
                  ),

                  if (overview.isNotEmpty) ...[
                    const SizedBox(height: 6),
                    Text(
                      overview,
                      style: const TextStyle(
                        fontSize: 13,
                        color: Color(0xFF6B7280),
                        height: 1.6,
                      ),
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ],

                  // 장소 미리보기 (DAY별)
                  if ((widget.course['places'] as List?)?.isNotEmpty == true)
                    _PlacesPreview(
                      places: List<Map<String, dynamic>>.from(
                        (widget.course['places'] as List).map(
                          (e) => Map<String, dynamic>.from(e as Map),
                        ),
                      ),
                    ),

                  const SizedBox(height: 12),

                  SizedBox(
                    width: double.infinity,
                    child: FilledButton.icon(
                      onPressed: _toggleSave,
                      icon: Icon(
                        _saved ? Icons.bookmark : Icons.bookmark_add_outlined,
                        size: 18,
                      ),
                      label: Text(_saved ? '저장됨' : '플래너에 저장'),
                      style: FilledButton.styleFrom(
                        padding: const EdgeInsets.symmetric(vertical: 12),
                        backgroundColor: _saved
                            ? Colors.grey[400]
                            : colorScheme.primary,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12),
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _TravelModeChip extends StatelessWidget {
  final String travelMode;
  const _TravelModeChip({required this.travelMode});

  @override
  Widget build(BuildContext context) {
    final isWalk = travelMode == 'WALK';
    final color = isWalk ? const Color(0xFF0F766E) : const Color(0xFF1D4ED8);
    return Chip(
      avatar: Icon(
        isWalk ? Icons.directions_walk : Icons.directions_car,
        size: 16,
        color: color,
      ),
      label: Text(isWalk ? '도보 기준' : '자동차 기준'),
      labelStyle: TextStyle(
        fontSize: 11,
        color: color,
        fontWeight: FontWeight.w700,
      ),
      backgroundColor: color.withValues(alpha: 0.10),
      side: BorderSide.none,
      padding: const EdgeInsets.symmetric(horizontal: 4),
      materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
      visualDensity: VisualDensity.compact,
    );
  }
}

// ─── 장소 미리보기 (DAY별 컴팩트) ────────────────────────
class _PlacesPreview extends StatelessWidget {
  final List<Map<String, dynamic>> places;
  const _PlacesPreview({required this.places});

  static const _slotIcons = {
    'meal': Icons.restaurant_outlined,
    'cafe': Icons.local_cafe_outlined,
    'lodging': Icons.hotel_outlined,
    'sight': Icons.place_outlined,
  };

  static const _slotColors = {
    'meal': Color(0xFFE65100),
    'cafe': Color(0xFF8D6E63),
    'lodging': Color(0xFF6A1B9A),
  };

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    // DAY별 그룹핑
    final Map<String, List<Map<String, dynamic>>> byDay = {};
    for (final p in places) {
      final day = p['dayLabel'] as String? ?? '';
      byDay.putIfAbsent(day, () => []).add(p);
    }

    return Padding(
      padding: const EdgeInsets.only(top: 10),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: byDay.entries.map((entry) {
          final dayLabel = entry.key;
          final dayPlaces = entry.value;

          return Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (dayLabel.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.only(bottom: 4, top: 4),
                  child: Text(
                    dayLabel,
                    style: TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.w700,
                      color: colorScheme.primary,
                    ),
                  ),
                ),
              Wrap(
                spacing: 4,
                runSpacing: 2,
                crossAxisAlignment: WrapCrossAlignment.center,
                children: List.generate(dayPlaces.length * 2 - 1, (i) {
                  if (i.isOdd) {
                    return Icon(
                      Icons.arrow_forward_ios,
                      size: 8,
                      color: Colors.grey[400],
                    );
                  }
                  final place = dayPlaces[i ~/ 2];
                  final slot = place['slotType'] as String? ?? 'sight';
                  final icon = _slotIcons[slot] ?? Icons.place_outlined;
                  final color = _slotColors[slot] ?? colorScheme.primary;
                  return Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(icon, size: 10, color: color),
                      const SizedBox(width: 2),
                      ConstrainedBox(
                        constraints: const BoxConstraints(maxWidth: 80),
                        child: Text(
                          place['subname'] as String? ?? '',
                          style: TextStyle(
                            fontSize: 11,
                            color: Colors.grey[700],
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    ],
                  );
                }),
              ),
            ],
          );
        }).toList(),
      ),
    );
  }
}

// ─── 코스 이미지 플레이스홀더 (이미지 없거나 로딩 실패 시) ──
class _CourseImagePlaceholder extends StatelessWidget {
  final Map<String, dynamic> course;
  final double height;

  const _CourseImagePlaceholder({required this.course, required this.height});

  static const _gradients = [
    [Color(0xFF2E7D6B), Color(0xFF1B5E4A)],
    [Color(0xFF1565C0), Color(0xFF0D47A1)],
    [Color(0xFF5C4AE3), Color(0xFF3D2FC4)],
    [Color(0xFFF57C00), Color(0xFFE65100)],
    [Color(0xFF388E3C), Color(0xFF1B5E20)],
  ];

  @override
  Widget build(BuildContext context) {
    final title = course['title'] as String? ?? '';
    final idx = title.isEmpty ? 0 : title.codeUnitAt(0) % _gradients.length;
    final colors = _gradients[idx];
    final region = course['region'] as String? ?? '';

    return Container(
      height: height,
      width: double.infinity,
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: colors,
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisAlignment: MainAxisAlignment.end,
          children: [
            if (region.isNotEmpty)
              Row(
                children: [
                  const Icon(
                    Icons.location_on,
                    color: Colors.white70,
                    size: 12,
                  ),
                  const SizedBox(width: 3),
                  Text(
                    region,
                    style: const TextStyle(
                      color: Colors.white70,
                      fontSize: 11,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                ],
              ),
            if (region.isNotEmpty) const SizedBox(height: 4),
            Text(
              title,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 15,
                fontWeight: FontWeight.w700,
                letterSpacing: -0.2,
              ),
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
          ],
        ),
      ),
    );
  }
}
