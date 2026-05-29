import 'package:flutter/material.dart';
import 'package:tripmate/core/models/user_prefs.dart';
import 'package:tripmate/core/services/auth_service.dart';
import 'package:tripmate/core/services/social_login_clients.dart';
import 'package:tripmate/core/services/user_data_service.dart';
import 'package:tripmate/features/auth/travel_setup_screen.dart';
import 'package:tripmate/features/my_trip/widgets/benefit_report_card.dart';
import 'package:tripmate/features/my_trip/widgets/bucket_section.dart';
import 'package:tripmate/features/my_trip/widgets/footprint_grid.dart';
import 'package:tripmate/features/my_trip/widgets/guest_panel.dart';
import 'package:tripmate/features/my_trip/widgets/settings_section.dart';
import 'package:tripmate/features/my_trip/widgets/taste_profile.dart';
import 'package:tripmate/features/my_trip/widgets/user_profile_header.dart';

const _kPrimary = Color(0xFF2E7D6B);
const _kText1   = Color(0xFF1A1A1A);
const _kText2   = Color(0xFF707070);
const _kText3   = Color(0xFF9E9E9E);
const _kBorder  = Color(0xFFE8EAED);

class MyTripScreen extends StatefulWidget {
  const MyTripScreen({super.key});

  @override
  State<MyTripScreen> createState() => _MyTripScreenState();
}

class _MyTripScreenState extends State<MyTripScreen> {

  String _fmt(int n) => n.toString().replaceAllMapped(
      RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (m) => '${m[1]},');

  void _refresh() => setState(() {});

  Future<void> _logout() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: const Text('로그아웃',
            style: TextStyle(fontWeight: FontWeight.w800)),
        content: const Text('로그아웃하면 저장된 취향 정보가\n초기화됩니다. 계속할까요?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('취소', style: TextStyle(color: _kText2)),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('로그아웃',
                style: TextStyle(
                    color: Colors.red, fontWeight: FontWeight.w700)),
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

  Future<void> _linkAccount(String provider) async {
    final socialProvider = switch (provider) {
      'kakao'  => SocialLoginProvider.kakao,
      'naver'  => SocialLoginProvider.naver,
      'google' => SocialLoginProvider.google,
      _        => null,
    };
    if (socialProvider == null) return;

    final result = await AuthService.instance.loginWithProvider(socialProvider);
    await UserDataService.instance.syncFromServer();
    final udPrefs   = UserDataService.instance.getPrefs();
    final purposes  = List<String>.from(udPrefs['purposes'] as List? ?? []);
    final duration  = (udPrefs['duration'] as String?) ?? '';
    if (purposes.isNotEmpty || duration.isNotEmpty) {
      await AuthService.instance.updatePrefs(purposes: purposes, duration: duration);
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

  Future<void> _goToTravelSetup() async {
    final prefs = UserPrefsScope.maybeOf(context)?.prefs ?? const UserPrefs();
    await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => TravelSetupScreen(
          showCourseResult: false,
          initialPurposes: prefs.purposes,
          initialDuration: prefs.duration,
        ),
      ),
    );
    if (!mounted) return;
    final udPrefs   = UserDataService.instance.getPrefs();
    final purposes  = List<String>.from(udPrefs['purposes'] as List? ?? []);
    final duration  = (udPrefs['duration'] as String?) ?? '';
    UserPrefsScope.maybeOf(context)?.onUpdate(
      prefs.copyWith(purposes: purposes, duration: duration),
    );
  }

  Future<void> _addBucketItem() async {
    final titleCtrl = TextEditingController();
    final areaCtrl  = TextEditingController();
    final noteCtrl  = TextEditingController();
    try {
      final ok = await showDialog<bool>(
        context: context,
        builder: (_) => AlertDialog(
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          title: const Text('버킷리스트 추가',
              style: TextStyle(fontWeight: FontWeight.w800)),
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
            TextButton(
                onPressed: () => Navigator.pop(context, false),
                child: const Text('취소')),
            TextButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('추가',
                  style: TextStyle(
                      fontWeight: FontWeight.w700, color: _kPrimary)),
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

  void _showSettings() {
    showModalBottomSheet(
      context: context,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (_) => SettingsSheet(
        onLogout: () { Navigator.pop(context); _logout(); },
        onEditPrefs: () { Navigator.pop(context); _goToTravelSetup(); },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final prefs   = UserPrefsScope.maybeOf(context)?.prefs ?? const UserPrefs();
    final isGuest = prefs.loginProvider == 'guest' || !prefs.isLoggedIn;

    final footprints    = UserDataService.instance.getFootprints();
    final bucketList    = UserDataService.instance.getBucketList();
    final benefitData   = UserDataService.instance.getBenefitReports();
    final totalSaved    = (benefitData['totalSaved'] as int?) ?? 0;
    final benefitItems  = List<Map<String, dynamic>>.from(
        (benefitData['items'] as List? ?? [])
            .map((e) => Map<String, dynamic>.from(e as Map)));
    final uniqueRegions = footprints
        .map((f) => f['regionName'] as String? ?? '')
        .toSet();

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
            if (isGuest)
              GuestPanel(onLink: _linkAccount)
            else
              UserProfileHeader(
                prefs: prefs,
                fmt: _fmt,
                tripCount: footprints.length,
                totalSaved: totalSaved,
              ),

            if (!isGuest) ...[
              _SectionTitle(
                title: '내 취향 프로필',
                trailing: TextButton(
                  onPressed: _goToTravelSetup,
                  child: const Text('재설정',
                      style: TextStyle(fontSize: 12, color: _kPrimary)),
                ),
              ),
              TasteProfileSection(prefs: prefs),

              const _SectionTitle(title: '👣  나의 족적'),
              FootprintGrid(footprints: footprints),

              const _SectionTitle(title: '역대 혜택 리포트'),
              BenefitReportCard(
                fmt: _fmt,
                totalSaved: totalSaved,
                benefitCount: benefitItems.length,
                regionCount: uniqueRegions.length,
              ),

              _SectionTitle(
                title: '버킷리스트',
                trailing: TextButton.icon(
                  onPressed: _addBucketItem,
                  icon: const Icon(Icons.add, size: 15, color: _kPrimary),
                  label: const Text('추가',
                      style: TextStyle(fontSize: 12, color: _kPrimary)),
                ),
              ),
              if (bucketList.isEmpty)
                const EmptyBucket()
              else
                ...bucketList.map((b) => BucketListItem(
                      data: b,
                      onDelete: () async {
                        await UserDataService.instance
                            .deleteBucketItem(b['id']);
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
              _LockedPreview(),
            ],

            SettingsMenuSection(
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

// ── 섹션 타이틀 ─────────────────────────────────────────────

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
            Text(title,
                style: const TextStyle(
                    fontSize: 17,
                    fontWeight: FontWeight.w700,
                    color: _kText1)),
            if (trailing != null) trailing!,
          ],
        ),
      );
}

// ── 게스트 잠금 미리보기 ─────────────────────────────────────

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
        const Text('로그인 후 이용 가능한 기능',
            style: TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.w800,
                color: _kText1)),
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
