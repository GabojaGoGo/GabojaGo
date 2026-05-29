import 'package:flutter/material.dart';

import 'package:tripmate/core/widgets/shimmer_box.dart';
import 'package:tripmate/features/course/course_detail_screen.dart';
import 'package:tripmate/features/course/widgets/course_card.dart';

class SavedCourseCard extends StatelessWidget {
  final Map<String, dynamic> course;
  final VoidCallback onRemove;
  const SavedCourseCard(
      {super.key, required this.course, required this.onRemove});

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final region   = course['region']   as String? ?? '';
    final title    = course['title']    as String? ?? '';
    final imageUrl = course['imageUrl'] as String? ?? '';
    final hasImage = imageUrl.isNotEmpty && !imageUrl.contains('placeholder');
    final places   = List<Map<String, dynamic>>.from(
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
            Text('삭제',
                style: TextStyle(
                    color: Colors.white,
                    fontSize: 11,
                    fontWeight: FontWeight.w600)),
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
        ) ??
            false;
      },
      onDismissed: (_) => onRemove(),
      child: Container(
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(16),
          boxShadow: const [
            BoxShadow(color: Color(0x05000000), blurRadius: 0, spreadRadius: 1),
            BoxShadow(
                color: Color(0x0A000000), blurRadius: 8, offset: Offset(0, 2)),
            BoxShadow(
                color: Color(0x14000000), blurRadius: 16, offset: Offset(0, 4)),
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
              if (hasImage)
                Image.network(
                  imageUrl,
                  height: 130,
                  width: double.infinity,
                  fit: BoxFit.cover,
                  loadingBuilder: (_, child, p) => p == null
                      ? child
                      : const ShimmerBox(height: 130, radius: 0),
                  errorBuilder: (_, __, ___) =>
                      CoursePlaceholder(course: course, height: 80),
                )
              else
                CoursePlaceholder(course: course, height: 80),

              Padding(
                padding: const EdgeInsets.all(14),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (region.isNotEmpty) ...[
                      Row(children: [
                        Icon(Icons.location_on_outlined,
                            size: 12, color: colorScheme.primary),
                        const SizedBox(width: 2),
                        Text(region,
                            style: TextStyle(
                                fontSize: 11,
                                color: colorScheme.primary,
                                fontWeight: FontWeight.w600)),
                      ]),
                      const SizedBox(height: 4),
                    ],
                    Text(title,
                        style: const TextStyle(
                            fontSize: 15,
                            fontWeight: FontWeight.w800,
                            letterSpacing: -0.3)),
                    if (distance.isNotEmpty || taketime.isNotEmpty) ...[
                      const SizedBox(height: 8),
                      Row(children: [
                        if (distance.isNotEmpty) ...[
                          CourseInfoPill(
                              icon: Icons.straighten_outlined,
                              label: distance),
                          const SizedBox(width: 6),
                        ],
                        if (taketime.isNotEmpty)
                          CourseInfoPill(
                              icon: Icons.schedule_outlined, label: taketime),
                      ]),
                    ],
                    if (places.isNotEmpty) ...[
                      const SizedBox(height: 10),
                      _PlacesPreviewRow(places: places),
                    ],
                  ],
                ),
              ),

              const Divider(height: 1),
              Padding(
                padding:
                    const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
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

class _PlacesPreviewRow extends StatelessWidget {
  final List<Map<String, dynamic>> places;
  const _PlacesPreviewRow({required this.places});

  static const _slotIcons = {
    'meal':    Icons.restaurant_outlined,
    'lodging': Icons.hotel_outlined,
  };
  static const _slotColors = {
    'meal':    Color(0xFFF97316),
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
                _slotIcons[preview[i]['slotType']] ?? Icons.place_outlined,
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
