import 'package:flutter/material.dart';
import '../services/api_service.dart';
import '../services/user_data_service.dart';

class CourseDetailScreen extends StatefulWidget {
  final Map<String, dynamic> course;
  final List<String> purposes;

  const CourseDetailScreen({
    super.key,
    required this.course,
    this.purposes = const [],
  });

  @override
  State<CourseDetailScreen> createState() => _CourseDetailScreenState();
}

class _CourseDetailScreenState extends State<CourseDetailScreen> {
  Map<String, dynamic>? _detail;
  bool _loading = true;
  bool _saved = false;

  @override
  void initState() {
    super.initState();
    final contentId = widget.course['contentId'] as String? ?? '';
    _saved = UserDataService.instance.isCourseSaved(contentId);

    // course에 이미 places가 있으면 바로 사용, 없으면 API 호출
    final existingPlaces = widget.course['places'] as List?;
    if (existingPlaces != null && existingPlaces.isNotEmpty) {
      _detail = {
        'places':   widget.course['places'],
        'distance': widget.course['distance'] ?? '',
        'taketime': widget.course['taketime'] ?? '',
        'theme':    widget.course['theme']    ?? '',
        'nearbyRestaurants':    widget.course['nearbyRestaurants']    ?? [],
        'nearbyAccommodations': widget.course['nearbyAccommodations'] ?? [],
      };
      _loading = false;
    } else {
      _loadDetail(contentId);
    }
  }

  Future<void> _loadDetail(String contentId) async {
    try {
      final detail = await ApiService.getCourseDetail(
        contentId,
        purposes: widget.purposes,
      );
      if (mounted) setState(() { _detail = detail; _loading = false; });
    } catch (e) {
      if (mounted) setState(() { _loading = false; });
    }
  }

  Future<void> _toggleSave() async {
    final contentId = widget.course['contentId'] as String? ?? '';
    if (_saved) {
      await UserDataService.instance.removeSavedCourse(contentId);
    } else {
      await UserDataService.instance.saveCourse(widget.course);
    }
    if (mounted) {
      setState(() => _saved = !_saved);
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text(_saved ? '플래너에 저장됐어요!' : '플래너에서 삭제됐어요'),
      ));
    }
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final imageUrl = widget.course['imageUrl'] as String? ?? '';
    final hasImage =
        imageUrl.isNotEmpty && !imageUrl.contains('placeholder');

    final places = _detail != null
        ? List<Map<String, dynamic>>.from(
            (_detail!['places'] as List? ?? [])
                .map((e) => Map<String, dynamic>.from(e as Map)))
        : <Map<String, dynamic>>[];

    final nearbyRestaurants = _detail != null
        ? List<Map<String, dynamic>>.from(
            (_detail!['nearbyRestaurants'] as List? ?? [])
                .map((e) => Map<String, dynamic>.from(e as Map)))
        : <Map<String, dynamic>>[];

    final nearbyAccommodations = _detail != null
        ? List<Map<String, dynamic>>.from(
            (_detail!['nearbyAccommodations'] as List? ?? [])
                .map((e) => Map<String, dynamic>.from(e as Map)))
        : <Map<String, dynamic>>[];

    return Scaffold(
      backgroundColor: colorScheme.surface,
      body: CustomScrollView(
        slivers: [
          SliverAppBar(
            expandedHeight: hasImage ? 220 : 0,
            pinned: true,
            backgroundColor: colorScheme.primary,
            foregroundColor: Colors.white,
            actions: [
              IconButton(
                icon: Icon(_saved ? Icons.bookmark : Icons.bookmark_outline),
                onPressed: _toggleSave,
              ),
            ],
            flexibleSpace: hasImage
                ? FlexibleSpaceBar(
                    background: Image.network(
                      imageUrl,
                      fit: BoxFit.cover,
                      errorBuilder: (context, error, stack) =>
                          Container(color: colorScheme.primaryContainer),
                    ),
                  )
                : null,
          ),

          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.all(20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // 지역
                  if ((widget.course['region'] as String? ?? '').isNotEmpty)
                    Row(
                      children: [
                        Icon(Icons.location_on_outlined,
                            size: 14, color: colorScheme.primary),
                        const SizedBox(width: 4),
                        Text(
                          widget.course['region'] as String,
                          style: TextStyle(
                              fontSize: 13,
                              color: colorScheme.primary,
                              fontWeight: FontWeight.w600),
                        ),
                      ],
                    ),
                  const SizedBox(height: 8),

                  // 제목
                  Text(
                    widget.course['title'] as String? ?? '',
                    style: const TextStyle(
                      fontSize: 22,
                      fontWeight: FontWeight.w800,
                      letterSpacing: -0.4,
                      height: 1.2,
                    ),
                  ),
                  const SizedBox(height: 12),

                  // 거리/시간/테마
                  if (_detail != null)
                    Wrap(
                      spacing: 8,
                      runSpacing: 6,
                      children: [
                        if ((_detail!['distance'] as String? ?? '').isNotEmpty)
                          _InfoPill(
                              icon: Icons.straighten_outlined,
                              label: _detail!['distance'] as String),
                        if ((_detail!['taketime'] as String? ?? '').isNotEmpty)
                          _InfoPill(
                              icon: Icons.schedule_outlined,
                              label: _detail!['taketime'] as String),
                        if ((_detail!['theme'] as String? ?? '').isNotEmpty)
                          _InfoPill(
                              icon: Icons.tag_outlined,
                              label: _detail!['theme'] as String),
                      ],
                    ),
                  if (_detail != null) const SizedBox(height: 20),

                  // 로딩
                  if (_loading)
                    const Center(
                      child: Padding(
                        padding: EdgeInsets.symmetric(vertical: 32),
                        child: CircularProgressIndicator(),
                      ),
                    ),

                  // 코스 구성 장소 목록
                  if (!_loading && places.isNotEmpty) ...[
                    Text(
                      '코스 구성 (${places.length}곳)',
                      style: const TextStyle(
                          fontSize: 16, fontWeight: FontWeight.w700),
                    ),
                    const SizedBox(height: 14),
                    ...List.generate(places.length, (i) {
                      final place = places[i];
                      final travelMin =
                          place['travelMinutesToNext'] as int?;
                      final dayLabel = place['dayLabel'] as String?;
                      final prevDayLabel = i > 0
                          ? places[i - 1]['dayLabel'] as String?
                          : null;
                      final showDayHeader = dayLabel != null &&
                          (i == 0 || dayLabel != prevDayLabel);

                      return Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          // DAY 구분 헤더
                          if (showDayHeader)
                            _DayDivider(
                              label: dayLabel,
                              isFirst: i == 0,
                            ),
                          _PlaceDetailItem(
                            index: i,
                            name: place['subname'] as String? ?? '',
                            overview: place['overview'] as String? ?? '',
                            imageUrl: place['imageUrl'] as String? ?? '',
                            address: place['address'] as String? ?? '',
                            tel: place['tel'] as String? ?? '',
                            usetime: place['usetime'] as String? ?? '',
                            usefee: place['usefee'] as String? ?? '',
                            isLast: i == places.length - 1 && travelMin == null,
                            slotType: place['slotType'] as String?,
                            timeLabel: place['timeLabel'] as String?,
                          ),
                          // 이동 시간 divider
                          if (i < places.length - 1)
                            _TravelTimeDivider(minutes: travelMin),
                        ],
                      );
                    }),
                    const SizedBox(height: 24),
                  ],

                  // 장소 없음
                  if (!_loading && _detail != null && places.isEmpty)
                    Container(
                      padding: const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        color: colorScheme.surfaceContainerLow,
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Row(
                        children: [
                          Icon(Icons.info_outline,
                              size: 18, color: const Color(0xFF9CA3AF)),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              '이 코스의 상세 정보가 제공되지 않아요.',
                              style: TextStyle(
                                  fontSize: 13, color: const Color(0xFF6B7280)),
                            ),
                          ),
                        ],
                      ),
                    ),

                  // 주변 맛집
                  if (nearbyRestaurants.isNotEmpty) ...[
                    const Divider(height: 32),
                    const Text('코스 근처 맛집',
                        style: TextStyle(
                            fontSize: 16, fontWeight: FontWeight.w700)),
                    const SizedBox(height: 12),
                    ...nearbyRestaurants
                        .map((p) => _NearbyPlaceItem(place: p)),
                    const SizedBox(height: 16),
                  ],

                  // 주변 숙박
                  if (nearbyAccommodations.isNotEmpty) ...[
                    const Divider(height: 32),
                    const Text('코스 근처 숙박',
                        style: TextStyle(
                            fontSize: 16, fontWeight: FontWeight.w700)),
                    const SizedBox(height: 12),
                    ...nearbyAccommodations
                        .map((p) => _NearbyPlaceItem(place: p)),
                    const SizedBox(height: 16),
                  ],

                  const SizedBox(height: 32),
                ],
              ),
            ),
          ),
        ],
      ),

      bottomNavigationBar: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 8, 20, 16),
          child: FilledButton.icon(
            onPressed: _toggleSave,
            icon: Icon(_saved ? Icons.bookmark : Icons.bookmark_add_outlined),
            label: Text(_saved ? '플래너에서 삭제' : '플래너에 담기'),
            style: FilledButton.styleFrom(
              minimumSize: const Size(double.infinity, 52),
              shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12)),
              backgroundColor:
                  _saved ? Colors.grey.shade400 : colorScheme.primary,
            ),
          ),
        ),
      ),
    );
  }
}

// ─── 이동 시간 divider ────────────────────────────────────
class _TravelTimeDivider extends StatelessWidget {
  final int? minutes;
  const _TravelTimeDivider({this.minutes});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(left: 10),
      child: Row(
        children: [
          Container(
            width: 2,
            height: 24,
            color: const Color(0xFF2E7D6B).withValues(alpha: 0.15),
          ),
          const SizedBox(width: 14),
          const Icon(Icons.directions_car_outlined,
              size: 14, color: Colors.grey),
          const SizedBox(width: 4),
          Text(
            minutes != null ? '자동차 약 $minutes분' : '이동',
            style: const TextStyle(fontSize: 12, color: Colors.grey),
          ),
        ],
      ),
    );
  }
}

// ─── DAY 구분 헤더 ───────────────────────────────────────
class _DayDivider extends StatelessWidget {
  final String label;
  final bool isFirst;
  const _DayDivider({required this.label, this.isFirst = false});

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return Padding(
      padding: EdgeInsets.only(top: isFirst ? 0 : 20, bottom: 12),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
            decoration: BoxDecoration(
              color: colorScheme.primary,
              borderRadius: BorderRadius.circular(20),
            ),
            child: Text(
              label,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 13,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Container(
              height: 1,
              color: colorScheme.primary.withValues(alpha: 0.2),
            ),
          ),
        ],
      ),
    );
  }
}

// ─── 장소 상세 아이템 ─────────────────────────────────────
class _PlaceDetailItem extends StatelessWidget {
  final int index;
  final String name;
  final String overview;
  final String imageUrl;
  final String address;
  final String tel;
  final String usetime;
  final String usefee;
  final bool isLast;
  final String? slotType;
  final String? timeLabel;

  const _PlaceDetailItem({
    required this.index,
    required this.name,
    required this.overview,
    required this.imageUrl,
    required this.address,
    required this.tel,
    required this.usetime,
    required this.usefee,
    required this.isLast,
    this.slotType,
    this.timeLabel,
  });

  IconData _slotIcon() {
    return switch (slotType) {
      'meal' => Icons.restaurant_outlined,
      'lodging' => Icons.hotel_outlined,
      _ => Icons.place_outlined,
    };
  }

  Color _slotColor(ColorScheme cs) {
    return switch (slotType) {
      'meal' => const Color(0xFFE65100),
      'lodging' => const Color(0xFF6A1B9A),
      _ => cs.primary,
    };
  }

  String _timeLabelKo() {
    return switch (timeLabel) {
      'morning' => '오전',
      'lunch' => '점심',
      'afternoon' => '오후',
      'dinner' => '저녁',
      'evening' => '숙박',
      _ => '',
    };
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final hasImg =
        imageUrl.isNotEmpty && !imageUrl.contains('placeholder');
    final slotColor = _slotColor(colorScheme);

    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // 번호 원 + 슬롯 아이콘
        Column(
          children: [
            Container(
              width: 26,
              height: 26,
              margin: const EdgeInsets.only(top: 2),
              decoration: BoxDecoration(
                color: slotColor,
                shape: BoxShape.circle,
              ),
              alignment: Alignment.center,
              child: Icon(
                _slotIcon(),
                color: Colors.white,
                size: 14,
              ),
            ),
          ],
        ),
        const SizedBox(width: 12),

        // 장소 내용
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // 시간대 라벨 + 장소명
              Row(
                crossAxisAlignment: CrossAxisAlignment.center,
                children: [
                  if (timeLabel != null && _timeLabelKo().isNotEmpty) ...[
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
                      decoration: BoxDecoration(
                        color: slotColor.withValues(alpha: 0.1),
                        borderRadius: BorderRadius.circular(4),
                      ),
                      child: Text(
                        _timeLabelKo(),
                        style: TextStyle(
                          fontSize: 10,
                          fontWeight: FontWeight.w600,
                          color: slotColor,
                        ),
                      ),
                    ),
                    const SizedBox(width: 6),
                  ],
                  Expanded(
                    child: Text(name,
                        style: const TextStyle(
                          fontSize: 15,
                          fontWeight: FontWeight.w700,
                          letterSpacing: -0.2,
                        )),
                  ),
                ],
              ),

              // 주소
              if (address.isNotEmpty) ...[
                const SizedBox(height: 3),
                Row(
                  children: [
                    Icon(Icons.place_outlined,
                        size: 13, color: const Color(0xFF9CA3AF)),
                    const SizedBox(width: 3),
                    Expanded(
                      child: Text(address,
                          style: TextStyle(
                              fontSize: 12, color: const Color(0xFF6B7280)),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis),
                    ),
                  ],
                ),
              ],

              // 이용시간 / 요금
              if (usetime.isNotEmpty || usefee.isNotEmpty) ...[
                const SizedBox(height: 4),
                Wrap(
                  spacing: 8,
                  children: [
                    if (usetime.isNotEmpty)
                      _SmallTag(
                          icon: Icons.access_time_outlined,
                          text: usetime),
                    if (usefee.isNotEmpty)
                      _SmallTag(
                          icon: Icons.payments_outlined, text: usefee),
                  ],
                ),
              ],

              // 설명
              if (overview.isNotEmpty) ...[
                const SizedBox(height: 6),
                Text(overview,
                    style: const TextStyle(
                        fontSize: 13,
                        color: Color(0xFF6B7280),
                        height: 1.6),
                    maxLines: 3,
                    overflow: TextOverflow.ellipsis),
              ],

              // 이미지
              if (hasImg) ...[
                const SizedBox(height: 8),
                ClipRRect(
                  borderRadius: BorderRadius.circular(8),
                  child: Image.network(
                    imageUrl,
                    height: 120,
                    width: double.infinity,
                    fit: BoxFit.cover,
                    errorBuilder: (context, error, stack) =>
                        const SizedBox.shrink(),
                  ),
                ),
              ],

              const SizedBox(height: 8),
            ],
          ),
        ),
      ],
    );
  }
}

// ─── 주변 장소 아이템 ─────────────────────────────────────
class _NearbyPlaceItem extends StatelessWidget {
  final Map<String, dynamic> place;
  const _NearbyPlaceItem({required this.place});

  @override
  Widget build(BuildContext context) {
    final name = place['name'] as String? ?? '';
    final category = place['categoryName'] as String? ?? '';
    final address = place['address'] as String? ?? '';
    final distance = place['distance'] as String? ?? '';

    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [
          const BoxShadow(color: Color(0x05000000), blurRadius: 0, spreadRadius: 1),
          const BoxShadow(color: Color(0x08000000), blurRadius: 6, offset: Offset(0, 2)),
        ],
      ),
      child: Row(
        children: [
          const Icon(Icons.restaurant_outlined,
              size: 18, color: Color(0xFF2E7D6B)),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(name,
                    style: const TextStyle(
                        fontSize: 13, fontWeight: FontWeight.w600)),
                if (category.isNotEmpty || address.isNotEmpty)
                  Text(
                    [
                      if (category.isNotEmpty) category,
                      if (address.isNotEmpty) address,
                    ].join(' · '),
                    style: TextStyle(
                        fontSize: 12, color: const Color(0xFF6B7280)),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
              ],
            ),
          ),
          if (distance.isNotEmpty) ...[
            const SizedBox(width: 8),
            Text('${distance}m',
                style:
                    TextStyle(fontSize: 12, color: const Color(0xFF9CA3AF))),
          ],
        ],
      ),
    );
  }
}

// ─── 공통 위젯 ────────────────────────────────────────────
class _InfoPill extends StatelessWidget {
  final IconData icon;
  final String label;
  const _InfoPill({required this.icon, required this.label});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(
        color: Colors.grey.shade100,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 14, color: const Color(0xFF6B7280)),
          const SizedBox(width: 4),
          Text(label,
              style: TextStyle(
                  fontSize: 12,
                  color: Colors.grey.shade700,
                  fontWeight: FontWeight.w500)),
        ],
      ),
    );
  }
}

class _SmallTag extends StatelessWidget {
  final IconData icon;
  final String text;
  const _SmallTag({required this.icon, required this.text});

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 12, color: const Color(0xFF9CA3AF)),
        const SizedBox(width: 3),
        Text(text,
            style: TextStyle(fontSize: 11, color: const Color(0xFF6B7280)),
            maxLines: 1,
            overflow: TextOverflow.ellipsis),
      ],
    );
  }
}
