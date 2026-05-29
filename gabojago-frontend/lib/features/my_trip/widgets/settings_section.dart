import 'package:flutter/material.dart';

const _kPrimary = Color(0xFF2E7D6B);
const _kText1   = Color(0xFF1A1A1A);
const _kText2   = Color(0xFF707070);
const _kText3   = Color(0xFF9E9E9E);
const _kBorder  = Color(0xFFE8EAED);

class SettingsMenuSection extends StatelessWidget {
  final bool isGuest;
  final VoidCallback onLogout;
  final VoidCallback onEditPrefs;

  const SettingsMenuSection({
    super.key,
    required this.isGuest,
    required this.onLogout,
    required this.onEditPrefs,
  });

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
        if (!isGuest)
          _MenuItem(
              icon: Icons.tune_outlined,
              label: '취향 재설정',
              onTap: onEditPrefs),
        const _MenuItem(
            icon: Icons.notifications_outlined, label: '알림 설정', onTap: _noop),
        const _MenuItem(
            icon: Icons.info_outline,
            label: '앱 정보 / 공지사항',
            onTap: _noop),
        const _MenuItem(
            icon: Icons.shield_outlined,
            label: '개인정보 처리방침',
            onTap: _noop),
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

void _noop() {}

class _MenuItem extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final Color? textColor;
  final Color? iconColor;
  final bool showChevron;

  const _MenuItem({
    required this.icon,
    required this.label,
    required this.onTap,
    this.textColor,
    this.iconColor,
    this.showChevron = true,
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
            Expanded(
              child: Text(label,
                  style: TextStyle(fontSize: 14, color: textColor ?? _kText1)),
            ),
            if (showChevron)
              const Icon(Icons.chevron_right, size: 18, color: _kText3),
          ]),
        ),
      );
}

class SettingsSheet extends StatelessWidget {
  final VoidCallback onLogout;
  final VoidCallback onEditPrefs;

  const SettingsSheet({
    super.key,
    required this.onLogout,
    required this.onEditPrefs,
  });

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        const SizedBox(height: 8),
        Container(
          width: 36, height: 4,
          decoration: BoxDecoration(
              color: _kBorder, borderRadius: BorderRadius.circular(2)),
        ),
        const SizedBox(height: 16),
        ListTile(
          leading: const Icon(Icons.tune_outlined, color: _kPrimary),
          title: const Text('취향 재설정'),
          onTap: onEditPrefs,
        ),
        ListTile(
          leading: const Icon(Icons.notifications_outlined),
          title: const Text('알림 설정'),
          onTap: () => Navigator.pop(context),
        ),
        ListTile(
          leading: Icon(Icons.logout, color: Colors.red.shade400),
          title: Text('로그아웃',
              style: TextStyle(color: Colors.red.shade400)),
          onTap: onLogout,
        ),
        const SizedBox(height: 8),
      ]),
    );
  }
}
