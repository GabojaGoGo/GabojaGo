import 'package:flutter/material.dart';
import 'package:tripmate/core/models/user_prefs.dart';
import 'package:tripmate/features/auth/travel_setup_screen.dart';

const _kPrimary = Color(0xFF2E7D6B);
const _kText2 = Color(0xFF707070);
const _kText3 = Color(0xFF9E9E9E);
const _kBorder = Color(0xFFE8EAED);

const _kPurposeColors = <String, Color>{
  'resort': Color(0xFF1565C0),
  'food': Color(0xFFF57C00),
  'budget': Color(0xFF388E3C),
  'nature': Color(0xFF2E7D6B),
  'history': Color(0xFF5C4AE3),
  'activity': Color(0xFFD32F2F),
};

class TasteProfileSection extends StatelessWidget {
  final UserPrefs prefs;
  const TasteProfileSection({super.key, required this.prefs});

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
                      padding: const EdgeInsets.symmetric(
                        horizontal: 12,
                        vertical: 7,
                      ),
                      decoration: BoxDecoration(
                        color: color.withValues(alpha: 0.1),
                        borderRadius: BorderRadius.circular(20),
                        border: Border.all(color: color.withValues(alpha: 0.3)),
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Text(
                            opt['icon']!,
                            style: const TextStyle(fontSize: 14),
                          ),
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
        const Text(
          '아직 취향을 설정하지 않았어요',
          style: TextStyle(fontSize: 14, color: _kText2),
        ),
        const SizedBox(height: 4),
        TextButton(
          onPressed: () => Navigator.push(
            context,
            MaterialPageRoute<void>(builder: (_) => const TravelSetupScreen()),
          ),
          child: const Text(
            '취향 설정하러 가기 →',
            style: TextStyle(color: _kPrimary, fontWeight: FontWeight.w600),
          ),
        ),
      ],
    );
  }
}
