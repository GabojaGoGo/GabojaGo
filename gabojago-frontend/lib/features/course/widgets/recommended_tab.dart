import 'package:flutter/material.dart';

import 'package:tripmate/core/models/user_prefs.dart';
import 'package:tripmate/core/widgets/shimmer_box.dart';
import 'package:tripmate/features/course/widgets/course_card.dart';

class RecommendedTab extends StatelessWidget {
  final UserPrefs prefs;
  final List<Map<String, dynamic>> courses;
  final bool loading;
  final bool hasError;
  final VoidCallback onGoToSetup;
  final VoidCallback onSaveChanged;
  final Future<void> Function() onRefresh;

  const RecommendedTab({
    super.key,
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
    if (!prefs.hasPrefs) return _EmptySetup(onGoToSetup: onGoToSetup);

    return RefreshIndicator(
      onRefresh: onRefresh,
      color: const Color(0xFF2E7D6B),
      child: CustomScrollView(
        physics: const AlwaysScrollableScrollPhysics(),
        slivers: [
          SliverToBoxAdapter(
            child: _PrefsHeader(prefs: prefs, onGoToSetup: onGoToSetup),
          ),

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

          if (!loading && courses.isNotEmpty)
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 32),
              sliver: SliverList(
                delegate: SliverChildBuilderDelegate(
                  (context, i) => Padding(
                    padding: const EdgeInsets.only(bottom: 16),
                    child: CourseCard(
                      course: courses[i],
                      purposes: prefs.purposes,
                      onSaveChanged: onSaveChanged,
                    ),
                  ),
                  childCount: courses.length,
                ),
              ),
            ),

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

class _PrefsHeader extends StatelessWidget {
  final UserPrefs prefs;
  final VoidCallback onGoToSetup;
  const _PrefsHeader({required this.prefs, required this.onGoToSetup});

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final purposeLabels = prefs.purposes
        .map((k) => kPurposeOptions
            .firstWhere((o) => o['key'] == k,
                orElse: () => {'label': k})['label']!)
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
        border:
            Border.all(color: colorScheme.primary.withValues(alpha: 0.15)),
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
                style: TextStyle(fontSize: 11, fontWeight: FontWeight.w600)),
          ),
        ],
      ),
    );
  }
}

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
          color: isDuration ? const Color(0xFF1565C0) : colorScheme.primary,
        ),
      ),
    );
  }
}

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
