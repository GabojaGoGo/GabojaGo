import 'package:flutter/material.dart';

import 'package:tripmate/core/services/user_data_service.dart';
import 'package:tripmate/core/widgets/shimmer_box.dart';
import 'package:tripmate/features/course/course_detail_screen.dart';

class CourseCard extends StatefulWidget {
  final Map<String, dynamic> course;
  final List<String> purposes;
  final VoidCallback onSaveChanged;

  const CourseCard({
    super.key,
    required this.course,
    required this.purposes,
    required this.onSaveChanged,
  });

  @override
  State<CourseCard> createState() => _CourseCardState();
}

class _CourseCardState extends State<CourseCard> {
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
    } else {
      await UserDataService.instance.saveCourse(widget.course);
    }
    if (mounted) setState(() => _saved = !_saved);
    widget.onSaveChanged();
    if (_saved && mounted) {
      Navigator.pushNamedAndRemoveUntil(
        context,
        '/main',
        (_) => false,
        arguments: 2,
      );
      return;
    }
    if (!mounted) return;
    if (mounted) {
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(
          SnackBar(
            content: Text(_saved ? '플래너에 저장됐어요!' : '플래너에서 삭제됐어요'),
            duration: const Duration(seconds: 2),
            behavior: SnackBarBehavior.floating,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(10),
            ),
          ),
        );
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
        setState(
          () => _saved = UserDataService.instance.isCourseSaved(contentId),
        );
      }
      widget.onSaveChanged();
    });
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final places = List<Map<String, dynamic>>.from(
      (widget.course['places'] as List? ?? []).map(
        (e) => Map<String, dynamic>.from(e as Map),
      ),
    );
    final distance = widget.course['distance'] as String? ?? '';
    final taketime = widget.course['taketime'] as String? ?? '';
    final region = widget.course['region'] as String? ?? '';
    final imageUrl = widget.course['imageUrl'] as String? ?? '';
    final hasImage = imageUrl.isNotEmpty && !imageUrl.contains('placeholder');

    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        boxShadow: const [
          BoxShadow(color: Color(0x05000000), blurRadius: 0, spreadRadius: 1),
          BoxShadow(
            color: Color(0x0A000000),
            blurRadius: 8,
            offset: Offset(0, 2),
          ),
          BoxShadow(
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
              height: 140,
              width: double.infinity,
              fit: BoxFit.cover,
              loadingBuilder: (_, child, p) =>
                  p == null ? child : const ShimmerBox(height: 140, radius: 0),
              errorBuilder: (_, __, ___) =>
                  CoursePlaceholder(course: widget.course),
            )
          else
            CoursePlaceholder(course: widget.course),

          Padding(
            padding: const EdgeInsets.fromLTRB(16, 14, 16, 0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (region.isNotEmpty) ...[
                  Row(
                    children: [
                      Icon(
                        Icons.location_on_outlined,
                        size: 13,
                        color: colorScheme.primary,
                      ),
                      const SizedBox(width: 2),
                      Text(
                        region,
                        style: TextStyle(
                          fontSize: 12,
                          color: colorScheme.primary,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 5),
                ],
                Text(
                  widget.course['title'] as String? ?? '',
                  style: const TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w800,
                    letterSpacing: -0.3,
                  ),
                ),
                const SizedBox(height: 10),
                if (distance.isNotEmpty || taketime.isNotEmpty) ...[
                  Row(
                    children: [
                      if (distance.isNotEmpty) ...[
                        CourseInfoPill(
                          icon: Icons.straighten_outlined,
                          label: distance,
                        ),
                        const SizedBox(width: 8),
                      ],
                      if (taketime.isNotEmpty)
                        CourseInfoPill(
                          icon: Icons.schedule_outlined,
                          label: taketime,
                        ),
                    ],
                  ),
                  const SizedBox(height: 12),
                ],
                if (places.isNotEmpty) ...[
                  ...List.generate(
                    places.length.clamp(0, 4),
                    (i) => CoursePlace(
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
                          fontSize: 12,
                          color: Color(0xFF9CA3AF),
                        ),
                      ),
                    ),
                  const SizedBox(height: 12),
                ] else if ((widget.course['overview'] as String? ?? '')
                    .isNotEmpty) ...[
                  Text(
                    widget.course['overview'] as String,
                    style: const TextStyle(
                      fontSize: 13,
                      color: Color(0xFF6B7280),
                      height: 1.6,
                    ),
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                  ),
                  const SizedBox(height: 12),
                ],
              ],
            ),
          ),

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
                        borderRadius: BorderRadius.circular(10),
                      ),
                    ),
                    child: const Text(
                      '코스 상세 보기',
                      style: TextStyle(
                        fontWeight: FontWeight.w600,
                        fontSize: 13,
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                _BookmarkButton(saved: _saved, onTap: _toggleSave),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

// ── 코스 이미지 플레이스홀더 ─────────────────────────────────

class CoursePlaceholder extends StatelessWidget {
  final Map<String, dynamic> course;
  final double height;
  const CoursePlaceholder({super.key, required this.course, this.height = 120});

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
    final region = course['region'] as String? ?? '';
    final idx = title.isEmpty ? 0 : title.codeUnitAt(0) % _colors.length;
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
            Text(
              region,
              style: const TextStyle(
                color: Colors.white70,
                fontSize: 11,
                fontWeight: FontWeight.w500,
              ),
            ),
          if (title.isNotEmpty) ...[
            if (region.isNotEmpty) const SizedBox(height: 2),
            Text(
              title,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 14,
                fontWeight: FontWeight.w700,
                letterSpacing: -0.2,
              ),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ],
        ],
      ),
    );
  }
}

// ── 장소 행 ──────────────────────────────────────────────────

class CoursePlace extends StatelessWidget {
  final int index;
  final Map<String, dynamic> place;
  final bool isLast;
  const CoursePlace({
    super.key,
    required this.index,
    required this.place,
    required this.isLast,
  });

  static const _slotColors = {
    'meal': Color(0xFFF97316),
    'cafe': Color(0xFF8D6E63),
    'lodging': Color(0xFF8B5CF6),
  };

  @override
  Widget build(BuildContext context) {
    final slotColor = _slotColors[place['slotType']] ?? const Color(0xFF2E7D6B);
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
                      fontWeight: FontWeight.w700,
                    ),
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
                        fontSize: 10,
                        color: Color(0xFF9CA3AF),
                      ),
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

// ── 정보 필 ──────────────────────────────────────────────────

class CourseInfoPill extends StatelessWidget {
  final IconData icon;
  final String label;
  const CourseInfoPill({super.key, required this.icon, required this.label});

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
          Text(
            label,
            style: const TextStyle(
              fontSize: 12,
              color: Color(0xFF374151),
              fontWeight: FontWeight.w500,
            ),
          ),
        ],
      ),
    );
  }
}

// ── 북마크 버튼 ──────────────────────────────────────────────

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
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      ),
      tooltip: saved ? '플래너에서 삭제' : '플래너에 담기',
    );
  }
}
