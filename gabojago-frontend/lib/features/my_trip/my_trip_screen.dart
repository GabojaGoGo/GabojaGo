// my_trip_screen.dart
// 기록 탭 — 게스트/로그인 상태 분기
//   게스트  : 계정 연동 유도 배너 + 소셜 연결 버튼 + 잠금 UI
//   로그인  : 프로필 헤더, 실제 취향 프로필, 족적, 혜택 리포트, 버킷리스트, 설정

import 'package:flutter/material.dart';
import 'package:tripmate/core/models/user_prefs.dart';
import 'package:tripmate/core/services/auth_service.dart';
import 'package:tripmate/core/services/social_login_clients.dart';
import 'package:tripmate/core/services/user_data_service.dart';
import 'package:tripmate/features/auth/travel_setup_screen.dart';

// ── 색상 상수 ──────────────────────────────────────────────
const _kPrimary   = Color(0xFF2E7D6B);
const _kText1     = Color(0xFF1A1A1A);
const _kText2     = Color(0xFF707070);
const _kText3     = Color(0xFF9E9E9E);
const _kBorder    = Color(0xFFE8EAED);
const _kSurface   = Color(0xFFF5F7F7);

// purpose key → 색상 매핑
const _kPurposeColors = <String, Color>{
  'resort':   Color(0xFF1565C0),
  'food':     Color(0xFFF57C00),
  'budget':   Color(0xFF388E3C),
  'nature':   Color(0xFF2E7D6B),
  'history':  Color(0xFF5C4AE3),
  'activity': Color(0xFFD32F2F),
};

// 지역 카드 색상/아이콘 팔레트
const _kRegionColors = [
  Color(0xFF2E7D6B), Color(0xFF1565C0), Color(0xFF5C4AE3),
  Color(0xFFF57C00), Color(0xFFD32F2F), Color(0xFF388E3C),
];
const _kRegionIcons = [
  Icons.forest_outlined,            Icons.beach_access_outlined,
  Icons.account_balance_outlined,   Icons.restaurant_outlined,
  Icons.landscape_outlined,         Icons.park_outlined,
];

// ══════════════════════════════════════════════════════════
class MyTripScreen extends StatefulWidget {
  const MyTripScreen({super.key});

  @override
  State<MyTripScreen> createState() => _MyTripScreenState();
}

class _MyTripScreenState extends State<MyTripScreen> {

  // ── 숫자 포맷 ─────────────────────────────────────────
  String _fmt(int n) => n.toString().replaceAllMapped(
      RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (m) => '${m[1]},');

  void _refresh() => setState(() {});

  // ── 로그아웃 ──────────────────────────────────────────
  Future<void> _logout() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: const Text('로그아웃', style: TextStyle(fontWeight: FontWeight.w800)),
        content: const Text('로그아웃하면 저장된 취향 정보가\n초기화됩니다. 계속할까요?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('취소', style: TextStyle(color: _kText2)),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('로그아웃', style: TextStyle(color: Colors.red, fontWeight: FontWeight.w700)),
          ),
        ],
      ),
    );
    if (ok == true && mounted) {
      await AuthService.instance.logout();
      if (!mounted) return;
      UserPrefsScope.maybeOf(context)?.onUpdate(const UserPrefs());
      Navigator.of(context).pushNamedAndRemoveUntil('/login', (_) => false);
    }
  }

  // ── 게스트 → 소셜 계정 연동 ──────────────────────────
  Future<void> _linkAccount(String provider) async {
    final socialProvider = switch (provider) {
      'kakao' => SocialLoginProvider.kakao,
      'naver' => SocialLoginProvider.naver,
      'google' => SocialLoginProvider.google,
      _ => null,
    };
    if (socialProvider == null) return;

    final result = await AuthService.instance.loginWithProvider(socialProvider);
    await UserDataService.instance.syncFromServer();
    final udPrefs = UserDataService.instance.getPrefs();
    final purposes = List<String>.from(udPrefs['purposes'] as List? ?? []);
    final duration = (udPrefs['duration'] as String?) ?? '';
    if (purposes.isNotEmpty || duration.isNotEmpty) {
      await AuthService.instance.updatePrefs(
        purposes: purposes,
        duration: duration,
      );
    }
    if (!mounted) return;
    if (result.isNewUser || !AuthService.instance.hasNickname) {
      Navigator.of(context).pushNamedAndRemoveUntil('/onboarding', (_) => false);
    } else {
      UserPrefsScope.maybeOf(context)?.onUpdate(
        AuthService.instance.toUserPrefs().copyWith(
          loginProvider: AuthService.instance.provider,
        ),
      );
    }
  }

  // ── 취향 재설정 ───────────────────────────────────────
  Future<void> _goToTravelSetup() async {
    final prefs = UserPrefsScope.maybeOf(context)?.prefs ?? const UserPrefs();
    await Navigator.push(context, MaterialPageRoute(
      builder: (_) => TravelSetupScreen(
        showCourseResult: false,
        initialPurposes: prefs.purposes,
        initialDuration: prefs.duration,
      ),
    ));
    if (!mounted) return;
    // pop 후 UserDataService에서 최신 취향을 읽어 UserPrefsScope 갱신
    final udPrefs  = UserDataService.instance.getPrefs();
    final purposes = List<String>.from(udPrefs['purposes'] as List? ?? []);
    final duration = (udPrefs['duration'] as String?) ?? '';
    UserPrefsScope.maybeOf(context)?.onUpdate(
      prefs.copyWith(purposes: purposes, duration: duration),
    );
  }

  // ── 버킷 아이템 추가 다이얼로그 ──────────────────────
  Future<void> _addBucketItem() async {
    final titleCtrl = TextEditingController();
    final areaCtrl  = TextEditingController();
    final noteCtrl  = TextEditingController();
    try {
      final ok = await showDialog<bool>(
        context: context,
        builder: (_) => AlertDialog(
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          title: const Text('버킷리스트 추가', style: TextStyle(fontWeight: FontWeight.w800)),
          content: Column(mainAxisSize: MainAxisSize.min, children: [
            TextField(
              controller: titleCtrl,
              decoration: const InputDecoration(labelText: '여행지 / 목표'),
              autofocus: true,
            ),
            TextField(
              controller: areaCtrl,
              decoration: const InputDecoration(labelText: '지역 (예: 강원도 속초)'),
            ),
            TextField(
              controller: noteCtrl,
              decoration: const InputDecoration(labelText: '메모 (선택)'),
            ),
          ]),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('취소')),
            TextButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('추가', style: TextStyle(fontWeight: FontWeight.w700, color: _kPrimary)),
            ),
          ],
        ),
      );
      if (ok == true && titleCtrl.text.trim().isNotEmpty) {
        await UserDataService.instance.addBucketItem(
          title: titleCtrl.text.trim(),
          area:  areaCtrl.text.trim(),
          note:  noteCtrl.text.trim(),
        );
        _refresh();
      }
    } finally {
      titleCtrl.dispose();
      areaCtrl.dispose();
      noteCtrl.dispose();
    }
  }

  // ── 설정 메뉴 BottomSheet ─────────────────────────────
  void _showSettings() {
    showModalBottomSheet(
      context: context,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (_) => _SettingsSheet(
        onLogout: () { Navigator.pop(context); _logout(); },
        onEditPrefs: () { Navigator.pop(context); _goToTravelSetup(); },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final prefs = UserPrefsScope.maybeOf(context)?.prefs ?? const UserPrefs();
    final isGuest = prefs.loginProvider == 'guest' || !prefs.isLoggedIn;

    final footprints   = UserDataService.instance.getFootprints();
    final bucketList   = UserDataService.instance.getBucketList();
    final benefitData  = UserDataService.instance.getBenefitReports();
    final totalSaved   = (benefitData['totalSaved'] as int?) ?? 0;
    final benefitItems = List<Map<String, dynamic>>.from(
        (benefitData['items'] as List? ?? []).map((e) => Map<String, dynamic>.from(e as Map)));
    final uniqueRegions = footprints.map((f) => f['regionName'] as String? ?? '').toSet();

    return Scaffold(
      backgroundColor: const Color(0xFFF8F9FA),
      appBar: AppBar(
        title: const Text('나의 여행'),
        actions: [
          IconButton(
            icon: const Icon(Icons.settings_outlined),
            onPressed: _showSettings,
            tooltip: '설정',
          ),
        ],
      ),
      body: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [

            // ── 게스트: 통합 카드 / 로그인: 프로필 헤더 ─────
            if (isGuest)
              _GuestProfileCard(onLink: _linkAccount)
            else
              _ProfileHeader(
                prefs: prefs,
                fmt: _fmt,
                tripCount: footprints.length,
                totalSaved: totalSaved,
              ),

            // ── 로그인 전용 콘텐츠 ───────────────────────
            if (!isGuest) ...[

              // 취향 프로필 (실제 UserPrefs 반영)
              _SectionTitle(title: '내 취향 프로필', trailing: TextButton(
                onPressed: _goToTravelSetup,
                child: const Text('재설정', style: TextStyle(fontSize: 12, color: _kPrimary)),
              )),
              _TasteProfileSection(prefs: prefs),

              // 나의 족적
              _SectionTitle(title: '👣  나의 족적'),
              _VisitedRegionGrid(footprints: footprints),

              // 역대 혜택 리포트
              _SectionTitle(title: '역대 혜택 리포트'),
              _BenefitReportCard(
                fmt: _fmt,
                totalSaved: totalSaved,
                benefitCount: benefitItems.length,
                regionCount: uniqueRegions.length,
              ),

              // 버킷리스트
              _SectionTitle(
                title: '버킷리스트',
                trailing: TextButton.icon(
                  onPressed: _addBucketItem,
                  icon: const Icon(Icons.add, size: 15, color: _kPrimary),
                  label: const Text('추가', style: TextStyle(fontSize: 12, color: _kPrimary)),
                ),
              ),
              if (bucketList.isEmpty)
                _EmptyBucket()
              else
                ...bucketList.map((b) => _BucketListItem(
                  data: b,
                  onDelete: () async {
                    await UserDataService.instance.deleteBucketItem(b['id']);
                    _refresh();
                  },
                  onToggleComplete: () async {
                    await UserDataService.instance.updateBucketItem(
                      b['id'],
                      completed: !(b['completed'] as bool? ?? false),
                    );
                    _refresh();
                  },
                )),

            ] else ...[
              // 게스트 잠금 미리보기
              _LockedPreview(),
            ],

            // ── 설정 메뉴 (공통) ─────────────────────────
            _SettingsMenuSection(
              isGuest: isGuest,
              onLogout: _logout,
              onEditPrefs: _goToTravelSetup,
            ),

            const SizedBox(height: 40),
            Center(
              child: Text(
                '가보자GO v1.0.0  ·  2026 관광데이터 활용 공모전',
                style: TextStyle(fontSize: 11, color: Colors.grey.shade400),
              ),
            ),
            const SizedBox(height: 24),
          ],
        ),
      ),
    );
  }
}

// ══════════════════════════════════════════════════════════
// 게스트 통합 카드 — 프로필 + 계정 연동을 하나의 카드에
// ══════════════════════════════════════════════════════════
class _GuestProfileCard extends StatelessWidget {
  final Future<void> Function(String) onLink;
  const _GuestProfileCard({required this.onLink});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.fromLTRB(16, 16, 16, 4),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(24),
        border: Border.all(color: _kBorder),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.05),
            blurRadius: 16,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // ── 상단: 게스트 프로필 정보 ─────────────────────
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 20, 20, 18),
            child: Row(
              children: [
                // 아바타
                Container(
                  width: 56, height: 56,
                  decoration: BoxDecoration(
                    color: const Color(0xFFF0F0F0),
                    shape: BoxShape.circle,
                    border: Border.all(color: _kBorder, width: 1.5),
                  ),
                  child: const Icon(Icons.person, size: 30, color: Color(0xFFAAAAAA)),
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        '게스트 모드',
                        style: TextStyle(
                          fontSize: 17,
                          fontWeight: FontWeight.w800,
                          color: _kText1,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        '로그인하면 취향·코스·혜택이 나를 기억해요',
                        style: TextStyle(
                          fontSize: 12.5,
                          color: _kText3,
                          height: 1.4,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),

          // ── 구분선 ───────────────────────────────────────
          const Divider(height: 1, color: _kBorder),

          // ── 하단: 계정 연동 섹션 ─────────────────────────
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 16, 20, 6),
            child: Row(
              children: [
                Container(
                  width: 26, height: 26,
                  decoration: BoxDecoration(
                    color: _kPrimary.withValues(alpha: 0.1),
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(Icons.link_rounded, size: 15, color: _kPrimary),
                ),
                const SizedBox(width: 9),
                const Text(
                  '계정을 연결하고 시작하세요',
                  style: TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w700,
                    color: _kText1,
                  ),
                ),
              ],
            ),
          ),

          // ── 소셜 로그인 버튼 리스트 ──────────────────────
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 4, 16, 16),
            child: Column(
              children: [
                _SocialLoginRow(
                  bgColor: const Color(0xFFFEE500),
                  iconWidget: const Text('💬', style: TextStyle(fontSize: 18)),
                  label: '카카오로 시작하기',
                  labelColor: const Color(0xFF191919),
                  onTap: () => onLink('kakao'),
                ),
                const SizedBox(height: 8),
                _SocialLoginRow(
                  bgColor: const Color(0xFF03C75A),
                  iconWidget: const Text(
                    'N',
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w900,
                      color: Colors.white,
                    ),
                  ),
                  label: '네이버로 시작하기',
                  labelColor: const Color(0xFF191919),
                  onTap: () => onLink('naver'),
                ),
                const SizedBox(height: 8),
                _SocialLoginRow(
                  bgColor: Colors.white,
                  borderColor: _kBorder,
                  iconWidget: _GoogleIcon(),
                  label: 'Google로 시작하기',
                  labelColor: const Color(0xFF191919),
                  onTap: () => onLink('google'),
                ),
                const SizedBox(height: 8),
                _SocialLoginRow(
                  bgColor: _kSurface,
                  iconWidget: const Icon(Icons.mail_outline_rounded, size: 18, color: _kText2),
                  label: '이메일로 시작하기',
                  labelColor: _kText1,
                  onTap: () => onLink('email'),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// 소셜 로그인 한 행 (아이콘 + 텍스트 + 화살표)
class _SocialLoginRow extends StatelessWidget {
  final Color bgColor;
  final Color? borderColor;
  final Widget iconWidget;
  final String label;
  final Color labelColor;
  final VoidCallback onTap;

  const _SocialLoginRow({
    required this.bgColor,
    this.borderColor,
    required this.iconWidget,
    required this.label,
    required this.labelColor,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(14),
        child: Container(
          height: 50,
          padding: const EdgeInsets.symmetric(horizontal: 14),
          decoration: BoxDecoration(
            color: bgColor,
            borderRadius: BorderRadius.circular(14),
            border: borderColor != null
                ? Border.all(color: borderColor!, width: 1.5)
                : null,
          ),
          child: Row(
            children: [
              SizedBox(width: 24, child: Center(child: iconWidget)),
              const SizedBox(width: 12),
              Expanded(
                child: Text(
                  label,
                  style: TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                    color: labelColor,
                  ),
                ),
              ),
              Icon(
                Icons.arrow_forward_ios_rounded,
                size: 13,
                color: labelColor.withValues(alpha: 0.4),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Google 컬러 G 아이콘
class _GoogleIcon extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return const SizedBox(
      width: 18,
      height: 18,
      child: _GText(),
    );
  }
}

class _GText extends StatelessWidget {
  const _GText();
  @override
  Widget build(BuildContext context) {
    return Text(
      'G',
      style: const TextStyle(
        fontSize: 17,
        fontWeight: FontWeight.w700,
        color: Color(0xFF4285F4),
      ),
    );
  }
}

// ══════════════════════════════════════════════════════════
// 프로필 헤더
// ══════════════════════════════════════════════════════════
class _ProfileHeader extends StatelessWidget {
  final UserPrefs prefs;
  final String Function(int) fmt;
  final int tripCount;
  final int totalSaved;
  const _ProfileHeader({
    required this.prefs,
    required this.fmt,
    required this.tripCount,
    required this.totalSaved,
  });

  // tripCount 기준 레벨 계산
  static const _kLevels = [
    {'label': 'BRONZE', 'icon': '🥉', 'min': 0,  'max': 4,  'color': Color(0xFFAD7456)},
    {'label': 'SILVER', 'icon': '🥈', 'min': 5,  'max': 14, 'color': Color(0xFF9E9E9E)},
    {'label': 'GOLD',   'icon': '🥇', 'min': 15, 'max': 29, 'color': Color(0xFFFFB800)},
    {'label': 'PLAT',   'icon': '💎', 'min': 30, 'max': 999,'color': Color(0xFF29B6F6)},
  ];

  Map<String, dynamic> get _level {
    for (final lv in _kLevels) {
      if (tripCount >= (lv['min'] as int) && tripCount <= (lv['max'] as int)) return lv;
    }
    return _kLevels.last;
  }

  void _showLevelInfo(BuildContext context) {
    final current = _level;
    showModalBottomSheet(
      context: context,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (_) => Padding(
        padding: const EdgeInsets.fromLTRB(20, 16, 20, 32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Center(
              child: Container(
                width: 36, height: 4,
                decoration: BoxDecoration(color: _kBorder, borderRadius: BorderRadius.circular(2)),
              ),
            ),
            const SizedBox(height: 16),
            const Text('여행자 레벨 기준', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w800, color: _kText1)),
            const SizedBox(height: 4),
            Text('방문한 관광지 수(족적)를 기준으로 산정돼요', style: TextStyle(fontSize: 12, color: _kText3)),
            const SizedBox(height: 16),
            ..._kLevels.map((lv) {
              final isCurrent = lv['label'] == current['label'];
              final color = lv['color'] as Color;
              final max = lv['max'] as int;
              final rangeText = max >= 999 ? '${lv['min']}곳 이상' : '${lv['min']}~$max곳';
              return Container(
                margin: const EdgeInsets.only(bottom: 10),
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                decoration: BoxDecoration(
                  color: isCurrent ? color.withValues(alpha: 0.1) : _kSurface,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: isCurrent ? color.withValues(alpha: 0.4) : _kBorder),
                ),
                child: Row(children: [
                  Text(lv['icon'] as String, style: const TextStyle(fontSize: 22)),
                  const SizedBox(width: 12),
                  Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Row(children: [
                      Text(lv['label'] as String, style: TextStyle(fontSize: 14, fontWeight: FontWeight.w800, color: color)),
                      if (isCurrent) ...[
                        const SizedBox(width: 6),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                          decoration: BoxDecoration(color: color, borderRadius: BorderRadius.circular(4)),
                          child: const Text('현재', style: TextStyle(fontSize: 9, fontWeight: FontWeight.w700, color: Colors.white)),
                        ),
                      ],
                    ]),
                    Text(rangeText, style: const TextStyle(fontSize: 11, color: _kText3)),
                  ])),
                  Text('$tripCount / ${max >= 999 ? '∞' : (max + 1)}', style: TextStyle(fontSize: 11, color: isCurrent ? color : _kText3, fontWeight: isCurrent ? FontWeight.w700 : FontWeight.normal)),
                ]),
              );
            }),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final isGuest = prefs.loginProvider == 'guest' || !prefs.isLoggedIn;
    final name    = isGuest ? '게스트' : (prefs.nickname.isNotEmpty ? prefs.nickname : '여행자');
    final providerLabel = _providerLabel(prefs.loginProvider);
    final lv = _level;

    return Container(
      margin: const EdgeInsets.fromLTRB(16, 16, 16, 0),
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: isGuest ? const Color(0xFFF5F7F7) : Colors.white,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: isGuest ? _kBorder : Colors.transparent),
        boxShadow: isGuest ? [] : [
          BoxShadow(color: Colors.black.withValues(alpha: 0.06), blurRadius: 16, offset: const Offset(0, 4)),
        ],
      ),
      child: Row(
        children: [
          // 아바타
          Container(
            width: 62, height: 62,
            decoration: BoxDecoration(
              color: isGuest ? const Color(0xFFE0E0E0) : _kPrimary.withValues(alpha: 0.12),
              shape: BoxShape.circle,
            ),
            child: Center(
              child: Text(
                isGuest ? '👤' : '✈️',
                style: const TextStyle(fontSize: 28),
              ),
            ),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Text(
                      isGuest ? '게스트 모드' : '$name님',
                      style: TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.w800,
                        color: isGuest ? _kText2 : _kText1,
                      ),
                    ),
                    if (!isGuest) ...[
                      const SizedBox(width: 6),
                      GestureDetector(
                        onTap: () => _showLevelInfo(context),
                        child: Container(
                          padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
                          decoration: BoxDecoration(
                            color: (lv['color'] as Color).withValues(alpha: 0.15),
                            borderRadius: BorderRadius.circular(6),
                          ),
                          child: Row(mainAxisSize: MainAxisSize.min, children: [
                            Text(lv['icon'] as String, style: const TextStyle(fontSize: 9)),
                            const SizedBox(width: 3),
                            Text(lv['label'] as String, style: TextStyle(fontSize: 10, fontWeight: FontWeight.w700, color: lv['color'] as Color)),
                          ]),
                        ),
                      ),
                    ],
                  ],
                ),
                const SizedBox(height: 4),
                Text(
                  isGuest
                      ? '로그인하면 취향 저장·기록 기능이 활성화돼요'
                      : '총 $tripCount번 여행  ·  누적 절약 ${fmt(totalSaved)}원',
                  style: TextStyle(
                    fontSize: 12,
                    color: isGuest ? _kText3 : _kText2,
                  ),
                ),
                if (!isGuest && providerLabel.isNotEmpty) ...[
                  const SizedBox(height: 4),
                  Row(
                    children: [
                      const Icon(Icons.link, size: 12, color: _kText3),
                      const SizedBox(width: 3),
                      Text(providerLabel, style: const TextStyle(fontSize: 11, color: _kText3)),
                    ],
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }

  String _providerLabel(String provider) {
    switch (provider) {
      case 'kakao':  return '카카오 연결됨';
      case 'naver':  return '네이버 연결됨';
      case 'google': return 'Google 연결됨';
      case 'email':  return '이메일 로그인';
      default:       return '';
    }
  }
}

// ══════════════════════════════════════════════════════════
// 취향 프로필 섹션 (실제 UserPrefs 반영)
// ══════════════════════════════════════════════════════════
class _TasteProfileSection extends StatelessWidget {
  final UserPrefs prefs;
  const _TasteProfileSection({required this.prefs});

  @override
  Widget build(BuildContext context) {
    final purposes = prefs.purposes;
    final duration = prefs.duration;

    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16),
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: _kBorder),
      ),
      child: purposes.isEmpty
          ? _EmptyTaste()
          : Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: purposes.map((key) {
                    final opt = kPurposeOptions.firstWhere(
                      (o) => o['key'] == key,
                      orElse: () => {'key': key, 'label': key, 'icon': '🏷️'},
                    );
                    final color = _kPurposeColors[key] ?? _kPrimary;
                    return Container(
                      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
                      decoration: BoxDecoration(
                        color: color.withValues(alpha: 0.1),
                        borderRadius: BorderRadius.circular(20),
                        border: Border.all(color: color.withValues(alpha: 0.3)),
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Text(opt['icon']!, style: const TextStyle(fontSize: 14)),
                          const SizedBox(width: 5),
                          Text(
                            opt['label']!,
                            style: TextStyle(
                              fontSize: 13,
                              fontWeight: FontWeight.w600,
                              color: color,
                            ),
                          ),
                        ],
                      ),
                    );
                  }).toList(),
                ),
                if (duration.isNotEmpty) ...[
                  const SizedBox(height: 12),
                  const Divider(height: 1, color: _kBorder),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      const Icon(Icons.schedule, size: 14, color: _kText3),
                      const SizedBox(width: 5),
                      const Text(
                        '선호 기간  ',
                        style: TextStyle(fontSize: 12, color: _kText3),
                      ),
                      Text(
                        _durationLabel(duration),
                        style: const TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w700,
                          color: _kPrimary,
                        ),
                      ),
                    ],
                  ),
                ],
              ],
            ),
    );
  }

  String _durationLabel(String key) {
    const m = {
      'day': '당일치기',
      '1n2d': '1박 2일',
      '2n3d': '2박 3일',
      '3nplus': '3박 이상',
    };
    return m[key] ?? key;
  }
}

class _EmptyTaste extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        const Text('🗺️', style: TextStyle(fontSize: 32)),
        const SizedBox(height: 8),
        const Text('아직 취향을 설정하지 않았어요', style: TextStyle(fontSize: 14, color: _kText2)),
        const SizedBox(height: 4),
        TextButton(
          onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const TravelSetupScreen())),
          child: const Text('취향 설정하러 가기 →', style: TextStyle(color: _kPrimary, fontWeight: FontWeight.w600)),
        ),
      ],
    );
  }
}

// ══════════════════════════════════════════════════════════
// 나의 족적 (방문 지역 그리드)
// ══════════════════════════════════════════════════════════
class _VisitedRegionGrid extends StatelessWidget {
  final List<Map<String, dynamic>> footprints;
  const _VisitedRegionGrid({required this.footprints});

  @override
  Widget build(BuildContext context) {
    if (footprints.isEmpty) {
      return _EmptyFootprints();
    }

    // regionName 기준으로 그루핑
    final Map<String, List<Map<String, dynamic>>> grouped = {};
    for (final f in footprints) {
      final region = (f['regionName'] as String?)?.trim() ?? '기타';
      grouped.putIfAbsent(region, () => []).add(f);
    }
    final regions = grouped.entries.toList();

    return GridView.builder(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      padding: const EdgeInsets.symmetric(horizontal: 16),
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 3,
        crossAxisSpacing: 10,
        mainAxisSpacing: 10,
        childAspectRatio: 0.85,
      ),
      itemCount: regions.length,
      itemBuilder: (_, i) {
        final entry = regions[i];
        final allTags = entry.value
            .expand((f) => (f['tags'] as List? ?? []).cast<String>())
            .toSet()
            .take(2)
            .toList();
        // 가장 최근 visitedAt
        final sortedDates = entry.value
            .map((f) => (f['visitedAt'] as String?) ?? '')
            .where((s) => s.isNotEmpty)
            .toList()
          ..sort((a, b) => b.compareTo(a));
        final lastVisitRaw = sortedDates.isNotEmpty ? sortedDates.first : '';
        final lastVisit = lastVisitRaw.length >= 7
            ? lastVisitRaw.substring(0, 7).replaceAll('-', '.')
            : lastVisitRaw;
        return _VisitedCard(data: {
          'name':       entry.key,
          'spotCount':  entry.value.length,
          'lastVisit':  lastVisit,
          'color':      _kRegionColors[i % _kRegionColors.length],
          'icon':       _kRegionIcons[i % _kRegionIcons.length],
          'tags':       allTags,
        });
      },
    );
  }
}

class _EmptyFootprints extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.symmetric(vertical: 28),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: _kBorder),
        ),
        child: const Column(children: [
          Text('👣', style: TextStyle(fontSize: 36)),
          SizedBox(height: 10),
          Text('아직 방문 기록이 없어요', style: TextStyle(fontSize: 14, color: _kText2)),
          SizedBox(height: 4),
          Text('관광지를 방문하면 자동으로 기록됩니다', style: TextStyle(fontSize: 12, color: _kText3)),
        ]),
      ),
    );
  }
}

class _VisitedCard extends StatelessWidget {
  final Map<String, dynamic> data;
  const _VisitedCard({required this.data});
  @override
  Widget build(BuildContext context) {
    final color = data['color'] as Color;
    final tags  = data['tags'] as List<String>;
    return Container(
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: _kBorder),
      ),
      child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
        Container(
          width: 44, height: 44,
          decoration: BoxDecoration(color: color.withValues(alpha: 0.12), shape: BoxShape.circle),
          child: Icon(data['icon'] as IconData, color: color, size: 22),
        ),
        const SizedBox(height: 7),
        Text(data['name'] as String, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: _kText1), textAlign: TextAlign.center),
        const SizedBox(height: 2),
        Text('${data['spotCount']}곳', style: TextStyle(fontSize: 10, color: Colors.grey.shade500)),
        const SizedBox(height: 5),
        Wrap(
          spacing: 3, runSpacing: 3, alignment: WrapAlignment.center,
          children: tags.map((t) => Container(
            padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1),
            decoration: BoxDecoration(color: color.withValues(alpha: 0.1), borderRadius: BorderRadius.circular(4)),
            child: Text(t, style: TextStyle(fontSize: 8, color: color, fontWeight: FontWeight.w600)),
          )).toList(),
        ),
      ]),
    );
  }
}

// ══════════════════════════════════════════════════════════
// 역대 혜택 리포트
// ══════════════════════════════════════════════════════════
class _BenefitReportCard extends StatelessWidget {
  final String Function(int) fmt;
  final int totalSaved;
  final int benefitCount;
  final int regionCount;
  const _BenefitReportCard({
    required this.fmt,
    required this.totalSaved,
    required this.benefitCount,
    required this.regionCount,
  });

  @override
  Widget build(BuildContext context) {
    final avg = benefitCount > 0 ? totalSaved ~/ benefitCount : 0;
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16),
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: _kBorder),
      ),
      child: Column(children: [
        // 총 환급액
        Container(
          width: double.infinity,
          padding: const EdgeInsets.symmetric(vertical: 14),
          decoration: BoxDecoration(
            color: _kPrimary.withValues(alpha: 0.07),
            borderRadius: BorderRadius.circular(12),
          ),
          child: Column(children: [
            Text('누적 절약 금액', style: TextStyle(fontSize: 12, color: _kPrimary.withValues(alpha: 0.8))),
            const SizedBox(height: 4),
            Text('${fmt(totalSaved)}원', style: const TextStyle(fontSize: 28, fontWeight: FontWeight.w800, color: _kPrimary)),
          ]),
        ),
        const SizedBox(height: 14),
        Row(children: [
          _Stat(value: '$benefitCount건', label: '이용한 보조금', icon: Icons.card_giftcard_outlined),
          _VDiv(),
          _Stat(value: '$regionCount곳', label: '방문 지역',      icon: Icons.place_outlined),
          _VDiv(),
          _Stat(value: '${fmt(avg)}원',    label: '여행당 평균',     icon: Icons.trending_up_outlined),
        ]),
      ]),
    );
  }
}

class _Stat extends StatelessWidget {
  final String value; final String label; final IconData icon;
  const _Stat({required this.value, required this.label, required this.icon});
  @override
  Widget build(BuildContext context) => Expanded(
    child: Column(children: [
      Icon(icon, size: 18, color: const Color(0xFF5C4AE3)),
      const SizedBox(height: 4),
      Text(value, style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 14, color: _kText1)),
      Text(label, style: const TextStyle(fontSize: 9, color: _kText3)),
    ]),
  );
}

class _VDiv extends StatelessWidget {
  @override
  Widget build(BuildContext context) => Container(width: 1, height: 36, color: _kBorder);
}

// ══════════════════════════════════════════════════════════
// 버킷리스트
// ══════════════════════════════════════════════════════════
class _BucketListItem extends StatelessWidget {
  final Map<String, dynamic> data;
  final VoidCallback onDelete;
  final VoidCallback onToggleComplete;
  const _BucketListItem({
    required this.data,
    required this.onDelete,
    required this.onToggleComplete,
  });

  @override
  Widget build(BuildContext context) {
    final completed = data['completed'] as bool? ?? false;
    return Container(
      margin: const EdgeInsets.fromLTRB(16, 0, 16, 10),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: completed ? _kSurface : Colors.white,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: completed ? _kBorder.withValues(alpha: 0.5) : _kBorder),
      ),
      child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        GestureDetector(
          onTap: onToggleComplete,
          child: Padding(
            padding: const EdgeInsets.only(top: 1),
            child: Icon(
              completed ? Icons.check_circle : Icons.radio_button_unchecked,
              size: 22,
              color: completed ? _kPrimary : _kText3,
            ),
          ),
        ),
        const SizedBox(width: 12),
        Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(
            data['title'] as String? ?? '',
            style: TextStyle(
              fontWeight: FontWeight.w700,
              fontSize: 14,
              color: completed ? _kText3 : _kText1,
              decoration: completed ? TextDecoration.lineThrough : null,
            ),
          ),
          const SizedBox(height: 3),
          if ((data['area'] as String? ?? '').isNotEmpty) Row(children: [
            const Icon(Icons.place_outlined, size: 12, color: _kText3),
            const SizedBox(width: 3),
            Text(data['area'] as String? ?? '', style: const TextStyle(fontSize: 11, color: _kText2)),
          ]),
          if ((data['note'] as String? ?? '').isNotEmpty) ...[
            const SizedBox(height: 3),
            Text(data['note'] as String? ?? '', style: TextStyle(fontSize: 11, color: Colors.grey.shade500, fontStyle: FontStyle.italic)),
          ],
        ])),
        GestureDetector(
          onTap: () => showModalBottomSheet(
            context: context,
            shape: const RoundedRectangleBorder(
              borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
            ),
            builder: (_) => SafeArea(
              child: Column(mainAxisSize: MainAxisSize.min, children: [
                const SizedBox(height: 8),
                Container(width: 36, height: 4, decoration: BoxDecoration(color: _kBorder, borderRadius: BorderRadius.circular(2))),
                const SizedBox(height: 16),
                ListTile(
                  leading: Icon(Icons.delete_outline, color: Colors.red.shade400),
                  title: Text('삭제', style: TextStyle(color: Colors.red.shade400)),
                  onTap: () { Navigator.pop(context); onDelete(); },
                ),
                const SizedBox(height: 8),
              ]),
            ),
          ),
          child: const Icon(Icons.more_horiz, color: _kText3, size: 20),
        ),
      ]),
    );
  }
}

class _EmptyBucket extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.symmetric(vertical: 24),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: _kBorder),
        ),
        child: const Column(children: [
          Text('📋', style: TextStyle(fontSize: 30)),
          SizedBox(height: 8),
          Text('버킷리스트가 비어있어요', style: TextStyle(fontSize: 13, color: _kText2)),
          SizedBox(height: 4),
          Text('위의 + 추가 버튼을 눌러 추가해 보세요', style: TextStyle(fontSize: 11, color: _kText3)),
        ]),
      ),
    );
  }
}

// ══════════════════════════════════════════════════════════
// 게스트 잠금 미리보기
// ══════════════════════════════════════════════════════════
class _LockedPreview extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.fromLTRB(16, 20, 16, 0),
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: _kBorder),
      ),
      child: Column(children: [
        const Text('🔒', style: TextStyle(fontSize: 40)),
        const SizedBox(height: 12),
        const Text('로그인 후 이용 가능한 기능', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w800, color: _kText1)),
        const SizedBox(height: 16),
        ...[
          ('🗺️', '나의 족적 지도 — 방문 지역 기록'),
          ('💰', '역대 절약 금액 · 혜택 리포트'),
          ('❤️', '취향 프로필 저장 · 코스 즐겨찾기'),
          ('📋', '버킷리스트 · 여행 알림'),
        ].map((item) => Padding(
          padding: const EdgeInsets.only(bottom: 10),
          child: Row(children: [
            Text(item.$1, style: const TextStyle(fontSize: 18)),
            const SizedBox(width: 12),
            Text(item.$2, style: const TextStyle(fontSize: 13, color: _kText2)),
          ]),
        )),
      ]),
    );
  }
}

// ══════════════════════════════════════════════════════════
// 섹션 타이틀
// ══════════════════════════════════════════════════════════
class _SectionTitle extends StatelessWidget {
  final String title;
  final Widget? trailing;
  const _SectionTitle({required this.title, this.trailing});

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(16, 24, 16, 10),
    child: Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(title, style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w700, color: _kText1)),
        ?trailing,
      ],
    ),
  );
}

// ══════════════════════════════════════════════════════════
// 설정 섹션 (화면 하단 리스트)
// ══════════════════════════════════════════════════════════
class _SettingsMenuSection extends StatelessWidget {
  final bool isGuest;
  final VoidCallback onLogout;
  final VoidCallback onEditPrefs;
  const _SettingsMenuSection({required this.isGuest, required this.onLogout, required this.onEditPrefs});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.fromLTRB(16, 28, 16, 0),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: _kBorder),
      ),
      child: Column(children: [
        if (!isGuest) _MenuItem(icon: Icons.tune_outlined, label: '취향 재설정', onTap: onEditPrefs),
        _MenuItem(icon: Icons.notifications_outlined, label: '알림 설정', onTap: () {}),
        _MenuItem(icon: Icons.info_outline, label: '앱 정보 / 공지사항', onTap: () {}),
        _MenuItem(icon: Icons.shield_outlined, label: '개인정보 처리방침', onTap: () {}),
        const Divider(height: 1, indent: 16, endIndent: 16),
        _MenuItem(
          icon: Icons.logout,
          label: isGuest ? '게스트 종료' : '로그아웃',
          textColor: Colors.red.shade400,
          iconColor: Colors.red.shade400,
          onTap: onLogout,
          showChevron: false,
        ),
      ]),
    );
  }
}

class _MenuItem extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final Color? textColor;
  final Color? iconColor;
  final bool showChevron;
  const _MenuItem({
    required this.icon, required this.label, required this.onTap,
    this.textColor, this.iconColor, this.showChevron = true,
  });

  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onTap,
    borderRadius: BorderRadius.circular(16),
    child: Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      child: Row(children: [
        Icon(icon, size: 20, color: iconColor ?? _kText2),
        const SizedBox(width: 14),
        Expanded(child: Text(label, style: TextStyle(fontSize: 14, color: textColor ?? _kText1))),
        if (showChevron) Icon(Icons.chevron_right, size: 18, color: _kText3),
      ]),
    ),
  );
}

// ══════════════════════════════════════════════════════════
// 설정 BottomSheet
// ══════════════════════════════════════════════════════════
class _SettingsSheet extends StatelessWidget {
  final VoidCallback onLogout;
  final VoidCallback onEditPrefs;
  const _SettingsSheet({required this.onLogout, required this.onEditPrefs});

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        const SizedBox(height: 8),
        Container(width: 36, height: 4, decoration: BoxDecoration(color: _kBorder, borderRadius: BorderRadius.circular(2))),
        const SizedBox(height: 16),
        ListTile(leading: const Icon(Icons.tune_outlined, color: _kPrimary), title: const Text('취향 재설정'), onTap: onEditPrefs),
        ListTile(leading: const Icon(Icons.notifications_outlined), title: const Text('알림 설정'), onTap: () => Navigator.pop(context)),
        ListTile(leading: Icon(Icons.logout, color: Colors.red.shade400), title: Text('로그아웃', style: TextStyle(color: Colors.red.shade400)), onTap: onLogout),
        const SizedBox(height: 8),
      ]),
    );
  }
}
