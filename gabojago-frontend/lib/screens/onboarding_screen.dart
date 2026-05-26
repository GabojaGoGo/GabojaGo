// onboarding_screen.dart
// 첫 로그인 직후 닉네임 입력 + 여행 취향 선택 온보딩
// 완료 → AuthService에 저장 → MainShell로 이동

import 'package:flutter/material.dart';
import 'package:geolocator/geolocator.dart';
import '../models/user_prefs.dart';
import '../services/auth_service.dart';
import '../services/user_data_service.dart';

class OnboardingScreen extends StatefulWidget {
  const OnboardingScreen({super.key});

  @override
  State<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends State<OnboardingScreen> {
  final _pageCtrl = PageController();
  int _page = 0; // 0: 닉네임, 1: 취향, 2: 기간

  final _nickCtrl = TextEditingController();
  final _nickFocus = FocusNode();
  final Set<String> _selectedPurposes = {};
  String _selectedDuration = '';
  bool _saving = false;

  static const _primary = Color(0xFF2E7D6B);
  static const _totalPages = 3;

  @override
  void initState() {
    super.initState();
    // 자동 포커스
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _nickFocus.requestFocus();
    });
  }

  @override
  void dispose() {
    _pageCtrl.dispose();
    _nickCtrl.dispose();
    _nickFocus.dispose();
    super.dispose();
  }

  // ── 다음 페이지 ──────────────────────────────────────────
  void _next() {
    if (_page < _totalPages - 1) {
      _pageCtrl.nextPage(
        duration: const Duration(milliseconds: 350),
        curve: Curves.easeInOut,
      );
      setState(() => _page++);
      FocusScope.of(context).unfocus();
    } else {
      _finish();
    }
  }

  bool get _canProceed {
    if (_page == 0) return _nickCtrl.text.trim().length >= 2;
    if (_page == 1) return _selectedPurposes.isNotEmpty;
    return _selectedDuration.isNotEmpty;
  }

  // ── 온보딩 완료 → 저장 → MainShell ──────────────────────
  Future<void> _finish() async {
    setState(() => _saving = true);
    await AuthService.instance.saveProfile(
      nickname: _nickCtrl.text.trim(),
      purposes: _selectedPurposes.toList(),
      duration: _selectedDuration,
    );
    await UserDataService.instance.savePrefs(
      _selectedPurposes.toList(),
      _selectedDuration,
    );

    if (!mounted) return;

    // UserPrefsScope 업데이트
    final newPrefs = AuthService.instance.toUserPrefs().copyWith(
      loginProvider: AuthService.instance.provider,
    );
    UserPrefsScope.maybeOf(context)?.onUpdate(newPrefs);

    // 위치 권한 요청 멘트 다이얼로그 띄우기
    await showDialog(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => AlertDialog(
        contentPadding: const EdgeInsets.only(top: 32, left: 24, right: 24, bottom: 12),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TweenAnimationBuilder<double>(
              tween: Tween(begin: 0.0, end: 1.0),
              duration: const Duration(milliseconds: 800),
              curve: Curves.elasticOut,
              builder: (context, val, child) {
                return Transform.scale(
                  scale: val,
                  child: const Icon(Icons.location_on_rounded, size: 64, color: Color(0xFF2E7D6B)),
                );
              },
            ),
            const SizedBox(height: 20),
            const Text(
              '위치 권한이 필요해요! 📍',
              style: TextStyle(fontWeight: FontWeight.w800, fontSize: 20, color: Color(0xFF1A1A1A)),
            ),
            const SizedBox(height: 12),
            const Text(
              '트립메이트가 내 취향에 딱 맞는\n주변 관광지와 축제를 추천해드릴 수 있도록\n위치 권한을 허용해 주실래요?',
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 15, height: 1.5, color: Colors.black87),
            ),
            const SizedBox(height: 12),
          ],
        ),
        actionsAlignment: MainAxisAlignment.center,
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            style: TextButton.styleFrom(
              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
            ),
            child: const Text('나중에요', style: TextStyle(color: Colors.grey, fontSize: 16, fontWeight: FontWeight.w600)),
          ),
          ElevatedButton(
            onPressed: () async {
              Navigator.pop(ctx);
              await Geolocator.requestPermission();
            },
            style: ElevatedButton.styleFrom(
              backgroundColor: const Color(0xFF2E7D6B),
              foregroundColor: Colors.white,
              padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
              elevation: 0,
            ),
            child: const Text('좋아요! 🚀', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
          ),
        ],
      ),
    );

    if (!mounted) return;
    Navigator.of(context).pushReplacementNamed('/main');
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      body: SafeArea(
        child: Column(
          children: [
            // ── 진행바 ─────────────────────────────────────
            _ProgressBar(current: _page, total: _totalPages),

            // ── 페이지 콘텐츠 ──────────────────────────────
            Expanded(
              child: PageView(
                controller: _pageCtrl,
                physics: const NeverScrollableScrollPhysics(),
                children: [
                  _NicknamePage(
                    controller: _nickCtrl,
                    focusNode: _nickFocus,
                    onChanged: (_) => setState(() {}),
                  ),
                  _PurposePage(
                    selected: _selectedPurposes,
                    onToggle: (key) => setState(() {
                      if (_selectedPurposes.contains(key)) {
                        _selectedPurposes.remove(key);
                      } else if (_selectedPurposes.length < 3) {
                        _selectedPurposes.add(key);
                      }
                    }),
                  ),
                  _DurationPage(
                    selected: _selectedDuration,
                    onSelect: (key) => setState(() => _selectedDuration = key),
                  ),
                ],
              ),
            ),

            // ── 하단 버튼 ──────────────────────────────────
            Padding(
              padding: const EdgeInsets.fromLTRB(24, 0, 24, 32),
              child: Column(
                children: [
                  SizedBox(
                    width: double.infinity,
                    height: 54,
                    child: ElevatedButton(
                      onPressed: _canProceed && !_saving ? _next : null,
                      style: ElevatedButton.styleFrom(
                        backgroundColor: _primary,
                        disabledBackgroundColor: const Color(0xFFCCE5DE),
                        foregroundColor: Colors.white,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(16),
                        ),
                        elevation: 0,
                      ),
                      child: _saving
                          ? const SizedBox(
                              width: 22,
                              height: 22,
                              child: CircularProgressIndicator(
                                color: Colors.white,
                                strokeWidth: 2.5,
                              ),
                            )
                          : Text(
                              _page == _totalPages - 1
                                  ? '취향 저장하고 시작하기 🎉'
                                  : '다음',
                              style: const TextStyle(
                                fontSize: 16,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                    ),
                  ),
                  // 건너뛰기 (취향·기간 페이지에서만)
                  if (_page > 0)
                    TextButton(
                      onPressed: _saving ? null : _finish,
                      child: Text(
                        '나중에 설정할게요',
                        style: TextStyle(fontSize: 13, color: Colors.grey[400]),
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

// ── 진행바 ────────────────────────────────────────────────
class _ProgressBar extends StatelessWidget {
  final int current;
  final int total;
  const _ProgressBar({required this.current, required this.total});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 16, 24, 0),
      child: Row(
        children: List.generate(total, (i) {
          final active = i <= current;
          return Expanded(
            child: Container(
              margin: EdgeInsets.only(right: i < total - 1 ? 6 : 0),
              height: 4,
              decoration: BoxDecoration(
                color: active
                    ? const Color(0xFF2E7D6B)
                    : const Color(0xFFE5E5E5),
                borderRadius: BorderRadius.circular(2),
              ),
            ),
          );
        }),
      ),
    );
  }
}

// ── Page 1: 닉네임 입력 ───────────────────────────────────
class _NicknamePage extends StatelessWidget {
  final TextEditingController controller;
  final FocusNode focusNode;
  final ValueChanged<String> onChanged;
  const _NicknamePage({
    required this.controller,
    required this.focusNode,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 36, 24, 0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 이모지 아이콘
          Container(
            width: 56,
            height: 56,
            decoration: BoxDecoration(
              color: const Color(0xFFE6F4F1),
              borderRadius: BorderRadius.circular(16),
            ),
            child: const Center(
              child: Text('👋', style: TextStyle(fontSize: 28)),
            ),
          ),
          const SizedBox(height: 20),
          const Text(
            '어떻게 불러드릴까요?',
            style: TextStyle(
              fontSize: 24,
              fontWeight: FontWeight.w800,
              color: Color(0xFF1A1A1A),
              height: 1.2,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            '닉네임을 설정하면 맞춤 여행 추천을\n더 정확하게 받을 수 있어요.',
            style: TextStyle(
              fontSize: 14,
              color: Colors.grey[500],
              height: 1.55,
            ),
          ),
          const SizedBox(height: 36),

          // 닉네임 입력 필드
          TextField(
            controller: controller,
            focusNode: focusNode,
            onChanged: onChanged,
            maxLength: 10,
            textInputAction: TextInputAction.done,
            style: const TextStyle(
              fontSize: 18,
              fontWeight: FontWeight.w700,
              color: Color(0xFF1A1A1A),
            ),
            decoration: InputDecoration(
              hintText: '닉네임 입력 (2~10자)',
              hintStyle: TextStyle(
                fontSize: 16,
                color: Colors.grey[400],
                fontWeight: FontWeight.w400,
              ),
              counterText: '',
              filled: true,
              fillColor: const Color(0xFFF5F7F7),
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(14),
                borderSide: BorderSide.none,
              ),
              focusedBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(14),
                borderSide: const BorderSide(
                  color: Color(0xFF2E7D6B),
                  width: 2,
                ),
              ),
              contentPadding: const EdgeInsets.symmetric(
                horizontal: 18,
                vertical: 16,
              ),
              suffixIcon: controller.text.isNotEmpty
                  ? IconButton(
                      icon: Icon(Icons.clear, color: Colors.grey[400]),
                      onPressed: () {
                        controller.clear();
                        onChanged('');
                      },
                    )
                  : null,
            ),
          ),

          const SizedBox(height: 12),

          // 글자 수 표시
          Align(
            alignment: Alignment.centerRight,
            child: Text(
              '${controller.text.length}/10',
              style: TextStyle(fontSize: 12, color: Colors.grey[400]),
            ),
          ),
        ],
      ),
    );
  }
}

// ── Page 2: 여행 취향 선택 ────────────────────────────────
class _PurposePage extends StatelessWidget {
  final Set<String> selected;
  final ValueChanged<String> onToggle;
  const _PurposePage({required this.selected, required this.onToggle});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 36, 24, 0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 56,
            height: 56,
            decoration: BoxDecoration(
              color: const Color(0xFFE6F4F1),
              borderRadius: BorderRadius.circular(16),
            ),
            child: const Center(
              child: Text('🗺️', style: TextStyle(fontSize: 28)),
            ),
          ),
          const SizedBox(height: 20),
          const Text(
            '어떤 여행을 좋아하세요?',
            style: TextStyle(
              fontSize: 24,
              fontWeight: FontWeight.w800,
              color: Color(0xFF1A1A1A),
            ),
          ),
          const SizedBox(height: 8),
          Text(
            '최대 3가지를 선택하면 딱 맞는 코스를 추천해드려요.',
            style: TextStyle(
              fontSize: 14,
              color: Colors.grey[500],
              height: 1.5,
            ),
          ),
          const SizedBox(height: 28),

          // 2열 그리드
          Expanded(
            child: GridView.count(
              crossAxisCount: 2,
              crossAxisSpacing: 10,
              mainAxisSpacing: 10,
              childAspectRatio: 2.0,
              physics: const NeverScrollableScrollPhysics(),
              children: kPurposeOptions.map((opt) {
                final key = opt['key']!;
                final isSel = selected.contains(key);
                final isDisabled = !isSel && selected.length >= 3;
                return _PurposeChip(
                  emoji: opt['icon']!,
                  label: opt['label']!,
                  selected: isSel,
                  disabled: isDisabled,
                  onTap: () => onToggle(key),
                );
              }).toList(),
            ),
          ),

          // 선택 카운트 힌트
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: List.generate(3, (i) {
                final filled = i < selected.length;
                return Container(
                  margin: const EdgeInsets.symmetric(horizontal: 3),
                  width: 8,
                  height: 8,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: filled
                        ? const Color(0xFF2E7D6B)
                        : const Color(0xFFE0E0E0),
                  ),
                );
              }),
            ),
          ),
        ],
      ),
    );
  }
}

class _PurposeChip extends StatelessWidget {
  final String emoji;
  final String label;
  final bool selected;
  final bool disabled;
  final VoidCallback onTap;
  const _PurposeChip({
    required this.emoji,
    required this.label,
    required this.selected,
    required this.disabled,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: disabled ? null : onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 180),
        decoration: BoxDecoration(
          color: selected
              ? const Color(0xFFE6F4F1)
              : disabled
              ? const Color(0xFFF9F9F9)
              : const Color(0xFFF5F7F7),
          borderRadius: BorderRadius.circular(14),
          border: Border.all(
            color: selected ? const Color(0xFF2E7D6B) : Colors.transparent,
            width: 2,
          ),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Text(emoji, style: const TextStyle(fontSize: 22)),
            const SizedBox(width: 8),
            Text(
              label,
              style: TextStyle(
                fontSize: 14,
                fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
                color: selected
                    ? const Color(0xFF2E7D6B)
                    : disabled
                    ? Colors.grey[400]
                    : const Color(0xFF1A1A1A),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ── Page 3: 기간 선택 ─────────────────────────────────────
class _DurationPage extends StatelessWidget {
  final String selected;
  final ValueChanged<String> onSelect;
  const _DurationPage({required this.selected, required this.onSelect});

  static const _durations = [
    {'key': 'day', 'emoji': '☀️', 'label': '당일치기', 'desc': '부담 없이 가볍게'},
    {'key': '1n2d', 'emoji': '🌙', 'label': '1박 2일', 'desc': '가장 인기 있는 일정'},
    {'key': '2n3d', 'emoji': '✨', 'label': '2박 3일', 'desc': '여유롭게 힐링'},
    {'key': '3nplus', 'emoji': '🏝️', 'label': '3박 이상', 'desc': '완전한 여행 경험'},
  ];

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 36, 24, 0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 56,
            height: 56,
            decoration: BoxDecoration(
              color: const Color(0xFFE6F4F1),
              borderRadius: BorderRadius.circular(16),
            ),
            child: const Center(
              child: Text('📅', style: TextStyle(fontSize: 28)),
            ),
          ),
          const SizedBox(height: 20),
          const Text(
            '보통 얼마나 여행하세요?',
            style: TextStyle(
              fontSize: 24,
              fontWeight: FontWeight.w800,
              color: Color(0xFF1A1A1A),
            ),
          ),
          const SizedBox(height: 8),
          Text(
            '선호 기간에 맞는 코스를 우선 추천해드려요.',
            style: TextStyle(fontSize: 14, color: Colors.grey[500]),
          ),
          const SizedBox(height: 28),

          Expanded(
            child: ListView.separated(
              physics: const NeverScrollableScrollPhysics(),
              itemCount: _durations.length,
              separatorBuilder: (_, __) => const SizedBox(height: 10),
              itemBuilder: (_, i) {
                final d = _durations[i];
                final isSel = selected == d['key'];
                return GestureDetector(
                  onTap: () => onSelect(d['key']!),
                  child: AnimatedContainer(
                    duration: const Duration(milliseconds: 180),
                    padding: const EdgeInsets.symmetric(
                      horizontal: 20,
                      vertical: 18,
                    ),
                    decoration: BoxDecoration(
                      color: isSel
                          ? const Color(0xFFE6F4F1)
                          : const Color(0xFFF5F7F7),
                      borderRadius: BorderRadius.circular(16),
                      border: Border.all(
                        color: isSel
                            ? const Color(0xFF2E7D6B)
                            : Colors.transparent,
                        width: 2,
                      ),
                    ),
                    child: Row(
                      children: [
                        Text(d['emoji']!, style: const TextStyle(fontSize: 26)),
                        const SizedBox(width: 14),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                d['label']!,
                                style: TextStyle(
                                  fontSize: 16,
                                  fontWeight: isSel
                                      ? FontWeight.w700
                                      : FontWeight.w600,
                                  color: isSel
                                      ? const Color(0xFF2E7D6B)
                                      : const Color(0xFF1A1A1A),
                                ),
                              ),
                              const SizedBox(height: 2),
                              Text(
                                d['desc']!,
                                style: TextStyle(
                                  fontSize: 12,
                                  color: isSel
                                      ? const Color(
                                          0xFF2E7D6B,
                                        ).withValues(alpha: 0.7)
                                      : Colors.grey[400],
                                ),
                              ),
                            ],
                          ),
                        ),
                        if (isSel)
                          Container(
                            width: 24,
                            height: 24,
                            decoration: const BoxDecoration(
                              color: Color(0xFF2E7D6B),
                              shape: BoxShape.circle,
                            ),
                            child: const Icon(
                              Icons.check,
                              color: Colors.white,
                              size: 15,
                            ),
                          ),
                      ],
                    ),
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}
