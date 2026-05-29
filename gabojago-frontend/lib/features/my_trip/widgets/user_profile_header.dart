import 'package:flutter/material.dart';
import 'package:tripmate/core/models/user_prefs.dart';

const _kPrimary = Color(0xFF2E7D6B);
const _kText1   = Color(0xFF1A1A1A);
const _kText2   = Color(0xFF707070);
const _kText3   = Color(0xFF9E9E9E);
const _kBorder  = Color(0xFFE8EAED);
const _kSurface = Color(0xFFF5F7F7);

class UserProfileHeader extends StatelessWidget {
  final UserPrefs prefs;
  final String Function(int) fmt;
  final int tripCount;
  final int totalSaved;

  const UserProfileHeader({
    super.key,
    required this.prefs,
    required this.fmt,
    required this.tripCount,
    required this.totalSaved,
  });

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
            const Text('여행자 레벨 기준',
                style: TextStyle(fontSize: 16, fontWeight: FontWeight.w800, color: _kText1)),
            const SizedBox(height: 4),
            const Text('방문한 관광지 수(족적)를 기준으로 산정돼요',
                style: TextStyle(fontSize: 12, color: _kText3)),
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
                  border: Border.all(
                      color: isCurrent ? color.withValues(alpha: 0.4) : _kBorder),
                ),
                child: Row(children: [
                  Text(lv['icon'] as String, style: const TextStyle(fontSize: 22)),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Row(children: [
                        Text(lv['label'] as String,
                            style: TextStyle(
                                fontSize: 14, fontWeight: FontWeight.w800, color: color)),
                        if (isCurrent) ...[
                          const SizedBox(width: 6),
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                            decoration: BoxDecoration(
                                color: color, borderRadius: BorderRadius.circular(4)),
                            child: const Text('현재',
                                style: TextStyle(
                                    fontSize: 9,
                                    fontWeight: FontWeight.w700,
                                    color: Colors.white)),
                          ),
                        ],
                      ]),
                      Text(rangeText, style: const TextStyle(fontSize: 11, color: _kText3)),
                    ]),
                  ),
                  Text(
                    '$tripCount / ${max >= 999 ? '∞' : (max + 1)}',
                    style: TextStyle(
                      fontSize: 11,
                      color: isCurrent ? color : _kText3,
                      fontWeight: isCurrent ? FontWeight.w700 : FontWeight.normal,
                    ),
                  ),
                ]),
              );
            }),
          ],
        ),
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

  @override
  Widget build(BuildContext context) {
    final isGuest = prefs.loginProvider == 'guest' || !prefs.isLoggedIn;
    final name = isGuest ? '게스트' : (prefs.nickname.isNotEmpty ? prefs.nickname : '여행자');
    final providerLabel = _providerLabel(prefs.loginProvider);
    final lv = _level;

    return Container(
      margin: const EdgeInsets.fromLTRB(16, 16, 16, 0),
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: isGuest ? const Color(0xFFF5F7F7) : Colors.white,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: isGuest ? _kBorder : Colors.transparent),
        boxShadow: isGuest
            ? []
            : [
                BoxShadow(
                    color: Colors.black.withValues(alpha: 0.06),
                    blurRadius: 16,
                    offset: const Offset(0, 4)),
              ],
      ),
      child: Row(
        children: [
          Container(
            width: 62, height: 62,
            decoration: BoxDecoration(
              color: isGuest
                  ? const Color(0xFFE0E0E0)
                  : _kPrimary.withValues(alpha: 0.12),
              shape: BoxShape.circle,
            ),
            child: Center(
              child: Text(isGuest ? '👤' : '✈️', style: const TextStyle(fontSize: 28)),
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
                            Text(lv['icon'] as String,
                                style: const TextStyle(fontSize: 9)),
                            const SizedBox(width: 3),
                            Text(lv['label'] as String,
                                style: TextStyle(
                                    fontSize: 10,
                                    fontWeight: FontWeight.w700,
                                    color: lv['color'] as Color)),
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
                  style: TextStyle(fontSize: 12, color: isGuest ? _kText3 : _kText2),
                ),
                if (!isGuest && providerLabel.isNotEmpty) ...[
                  const SizedBox(height: 4),
                  Row(
                    children: [
                      const Icon(Icons.link, size: 12, color: _kText3),
                      const SizedBox(width: 3),
                      Text(providerLabel,
                          style: const TextStyle(fontSize: 11, color: _kText3)),
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
}
