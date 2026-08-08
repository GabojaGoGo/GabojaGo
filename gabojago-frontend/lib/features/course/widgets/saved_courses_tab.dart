import 'package:flutter/material.dart';

import 'package:tripmate/core/services/user_data_service.dart';
import 'package:tripmate/features/course/widgets/saved_course_card.dart';

class SavedCoursesTab extends StatelessWidget {
  final VoidCallback onSaveChanged;
  final VoidCallback onGoToRecommended;

  const SavedCoursesTab({
    super.key,
    required this.onSaveChanged,
    required this.onGoToRecommended,
  });

  @override
  Widget build(BuildContext context) {
    final saved = UserDataService.instance.getSavedCourses();

    if (saved.isEmpty) return _EmptySaved(onGoToRecommended: onGoToRecommended);

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
            delegate: SliverChildBuilderDelegate((context, i) {
              final course = saved[i];
              return Padding(
                padding: const EdgeInsets.only(bottom: 16),
                child: SavedCourseCard(
                  course: course,
                  onRemove: () async {
                    final contentId = course['contentId'] as String? ?? '';
                    await UserDataService.instance.removeSavedCourse(contentId);
                    onSaveChanged();
                  },
                ),
              );
            }, childCount: saved.length),
          ),
        ),
      ],
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
            const Text(
              '저장한 코스가 없어요',
              style: TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.w700,
                letterSpacing: -0.3,
              ),
            ),
            const SizedBox(height: 8),
            const Text(
              '코스 추천에서 마음에 드는 코스를\n저장해 모아두세요',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 14,
                color: Color(0xFF6B7280),
                height: 1.6,
              ),
            ),
            const SizedBox(height: 28),
            FilledButton.icon(
              onPressed: onGoToRecommended,
              icon: const Icon(Icons.auto_awesome_outlined, size: 18),
              label: const Text('코스 추천 보기'),
              style: FilledButton.styleFrom(
                padding: const EdgeInsets.symmetric(
                  horizontal: 24,
                  vertical: 14,
                ),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
