import 'package:flutter/material.dart';
import '../models/user_prefs.dart';
import '../services/auth_service.dart';
import '../services/user_data_service.dart';
import 'travel_course_result_screen.dart';

class TravelSetupScreen extends StatefulWidget {
  /// true(기본값): 완료 후 코스 추천 화면으로 이동
  /// false: 저장 후 이전 화면으로 복귀 (기록탭 재설정 등)
  final bool showCourseResult;
  final List<String> initialPurposes;
  final String initialDuration;
  const TravelSetupScreen({
    super.key,
    this.showCourseResult = true,
    this.initialPurposes = const [],
    this.initialDuration = '',
  });

  @override
  State<TravelSetupScreen> createState() => _TravelSetupScreenState();
}

class _TravelSetupScreenState extends State<TravelSetupScreen> {
  final _pageController = PageController();
  int _currentPage = 0;

  late final Set<String> _selectedPurposes;
  late String _selectedDuration;

  @override
  void initState() {
    super.initState();
    _selectedPurposes = Set.from(widget.initialPurposes);
    _selectedDuration = widget.initialDuration;
  }

  void _goNext() {
    if (_currentPage < 1) {
      _pageController.nextPage(
        duration: const Duration(milliseconds: 300),
        curve: Curves.easeInOut,
      );
      setState(() => _currentPage++);
    } else {
      _goToResult();
    }
  }

  void _goBack() {
    if (_currentPage > 0) {
      _pageController.previousPage(
        duration: const Duration(milliseconds: 300),
        curve: Curves.easeInOut,
      );
      setState(() => _currentPage--);
    } else {
      Navigator.pop(context);
    }
  }

  void _goToResult() {
    final prefs = UserPrefs(
      purposes: _selectedPurposes.toList(),
      duration: _selectedDuration,
    );
    // UserPrefsScope 업데이트 + 서버 sync
    final scope = UserPrefsScope.maybeOf(context);
    if (scope != null) {
      scope.onUpdate(prefs);
    }
    // auth_purposes / auth_duration도 함께 저장 (앱 재시작 시 복원용)
    AuthService.instance.updatePrefs(
      purposes: _selectedPurposes.toList(),
      duration: _selectedDuration,
    );
    UserDataService.instance.savePrefs(_selectedPurposes.toList(), _selectedDuration);

    if (widget.showCourseResult) {
      Navigator.push(
        context,
        MaterialPageRoute(
          builder: (_) => TravelCourseResultScreen(prefs: prefs),
        ),
      );
    } else {
      Navigator.pop(context);
    }
  }

  bool get _canProceed {
    if (_currentPage == 0) return _selectedPurposes.isNotEmpty;
    return _selectedDuration.isNotEmpty;
  }

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return Scaffold(
      backgroundColor: colorScheme.surface,
      appBar: AppBar(
        backgroundColor: colorScheme.surface,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: _goBack,
        ),
        title: Text(
          _currentPage == 0 ? '여행 목적 선택' : '여행 기간 선택',
          style: const TextStyle(fontWeight: FontWeight.w700),
        ),
      ),
      body: Column(
        children: [
          // 진행 바
          _StepProgressBar(currentStep: _currentPage, totalSteps: 2),
          const SizedBox(height: 8),
          // 페이지 컨텐츠
          Expanded(
            child: PageView(
              controller: _pageController,
              physics: const NeverScrollableScrollPhysics(),
              children: [
                _PurposeSelectionPage(
                  selected: _selectedPurposes,
                  onToggle: (key) => setState(() {
                    if (_selectedPurposes.contains(key)) {
                      _selectedPurposes.remove(key);
                    } else if (_selectedPurposes.length < 3) {
                      _selectedPurposes.add(key);
                    }
                  }),
                ),
                _DurationSelectionPage(
                  selected: _selectedDuration,
                  onSelect: (key) => setState(() => _selectedDuration = key),
                ),
              ],
            ),
          ),
          // 하단 버튼
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(20, 12, 20, 16),
              child: SizedBox(
                width: double.infinity,
                height: 52,
                child: FilledButton(
                  onPressed: _canProceed ? _goNext : null,
                  child: Text(
                    _currentPage == 1
                        ? (widget.showCourseResult ? '추천 코스 보기' : '저장')
                        : '다음',
                    style: const TextStyle(
                        fontSize: 16, fontWeight: FontWeight.w700),
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

// ─── 진행 바 ───────────────────────────────────────────────
class _StepProgressBar extends StatelessWidget {
  final int currentStep;
  final int totalSteps;

  const _StepProgressBar(
      {required this.currentStep, required this.totalSteps});

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 20),
      child: Row(
        children: List.generate(totalSteps, (i) {
          final active = i <= currentStep;
          return Expanded(
            child: Container(
              margin: EdgeInsets.only(right: i < totalSteps - 1 ? 6 : 0),
              height: 4,
              decoration: BoxDecoration(
                color: active
                    ? colorScheme.primary
                    : colorScheme.surfaceContainerHighest,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
          );
        }),
      ),
    );
  }
}

// ─── Step 1: 여행 목적 선택 ────────────────────────────────
class _PurposeSelectionPage extends StatelessWidget {
  final Set<String> selected;
  final void Function(String) onToggle;

  const _PurposeSelectionPage(
      {required this.selected, required this.onToggle});

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            '어떤 여행을 원하세요?',
            style: TextStyle(fontSize: 22, fontWeight: FontWeight.w800),
          ),
          const SizedBox(height: 4),
          Text(
            '최대 3개까지 선택할 수 있어요',
            style: TextStyle(
                fontSize: 14, color: Colors.grey[600]),
          ),
          const SizedBox(height: 24),
          GridView.count(
            crossAxisCount: 2,
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            crossAxisSpacing: 12,
            mainAxisSpacing: 12,
            childAspectRatio: 1.6,
            children: kPurposeOptions.map((opt) {
              final key = opt['key']!;
              final isSelected = selected.contains(key);
              final disabled = !isSelected && selected.length >= 3;
              return _PurposeChip(
                icon: opt['icon']!,
                label: opt['label']!,
                isSelected: isSelected,
                disabled: disabled,
                onTap: () => onToggle(key),
              );
            }).toList(),
          ),
        ],
      ),
    );
  }
}

class _PurposeChip extends StatelessWidget {
  final String icon;
  final String label;
  final bool isSelected;
  final bool disabled;
  final VoidCallback onTap;

  const _PurposeChip({
    required this.icon,
    required this.label,
    required this.isSelected,
    required this.disabled,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return GestureDetector(
      onTap: disabled ? null : onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        decoration: BoxDecoration(
          color: isSelected
              ? colorScheme.primaryContainer
              : disabled
                  ? colorScheme.surfaceContainerLowest
                  : colorScheme.surfaceContainerLow,
          border: Border.all(
            color: isSelected
                ? colorScheme.primary
                : Colors.transparent,
            width: 2,
          ),
          borderRadius: BorderRadius.circular(16),
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Text(icon, style: TextStyle(fontSize: 28, color: disabled ? null : null)),
            const SizedBox(height: 6),
            Text(
              label,
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.w600,
                color: isSelected
                    ? colorScheme.primary
                    : disabled
                        ? colorScheme.onSurface.withValues(alpha: 0.38)
                        : colorScheme.onSurface,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ─── Step 2: 여행 기간 선택 ────────────────────────────────
class _DurationSelectionPage extends StatelessWidget {
  final String selected;
  final void Function(String) onSelect;

  const _DurationSelectionPage(
      {required this.selected, required this.onSelect});

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            '얼마나 여행할 예정인가요?',
            style: TextStyle(fontSize: 22, fontWeight: FontWeight.w800),
          ),
          const SizedBox(height: 4),
          Text(
            '기간에 맞는 최적 코스를 찾아드릴게요',
            style: TextStyle(fontSize: 14, color: Colors.grey[600]),
          ),
          const SizedBox(height: 24),
          ...kDurationOptions.map((opt) {
            final key = opt['key']!;
            final isSelected = selected == key;
            return Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: _DurationCard(
                label: opt['label']!,
                sub: opt['sub']!,
                isSelected: isSelected,
                onTap: () => onSelect(key),
              ),
            );
          }),
        ],
      ),
    );
  }
}

class _DurationCard extends StatelessWidget {
  final String label;
  final String sub;
  final bool isSelected;
  final VoidCallback onTap;

  const _DurationCard({
    required this.label,
    required this.sub,
    required this.isSelected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 180),
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 18),
        decoration: BoxDecoration(
          color: isSelected
              ? colorScheme.primaryContainer
              : colorScheme.surfaceContainerLow,
          border: Border.all(
            color: isSelected ? colorScheme.primary : Colors.transparent,
            width: 2,
          ),
          borderRadius: BorderRadius.circular(16),
        ),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    label,
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w700,
                      color: isSelected
                          ? colorScheme.primary
                          : colorScheme.onSurface,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    sub,
                    style: TextStyle(
                      fontSize: 13,
                      color: isSelected
                          ? colorScheme.primary.withValues(alpha: 0.7)
                          : Colors.grey[600],
                    ),
                  ),
                ],
              ),
            ),
            if (isSelected)
              Icon(Icons.check_circle_rounded,
                  color: colorScheme.primary, size: 22),
          ],
        ),
      ),
    );
  }
}
