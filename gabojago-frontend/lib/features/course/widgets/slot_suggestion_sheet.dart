import 'package:flutter/material.dart';

class SlotSuggestionSheet extends StatelessWidget {
  final List<Map<String, dynamic>> suggestions;

  const SlotSuggestionSheet({super.key, required this.suggestions});

  @override
  Widget build(BuildContext context) {
    if (suggestions.isEmpty) {
      return const SafeArea(
        child: Padding(
          padding: EdgeInsets.all(28),
          child: Text('이 시간대에 바꿀 수 있는 장소가 없어요.'),
        ),
      );
    }
    return SafeArea(
      child: ConstrainedBox(
        constraints: BoxConstraints(
          maxHeight: MediaQuery.sizeOf(context).height * 0.72,
        ),
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(20, 0, 20, 24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                '동선이 좋은 다른 장소',
                style: TextStyle(fontSize: 19, fontWeight: FontWeight.w800),
              ),
              const SizedBox(height: 4),
              const Text(
                '현재 코스의 앞뒤 장소와 실제 도로 거리를 비교했어요.',
                style: TextStyle(fontSize: 13, color: Color(0xFF6B7280)),
              ),
              const SizedBox(height: 12),
              ...suggestions.map(
                (suggestion) => ListTile(
                  contentPadding: EdgeInsets.zero,
                  leading: const CircleAvatar(
                    child: Icon(Icons.place_outlined),
                  ),
                  title: Text(
                    suggestion['placeName'] as String? ?? '',
                    style: const TextStyle(fontWeight: FontWeight.w700),
                  ),
                  subtitle: Text(
                    '우회 ${suggestion['detourMeters'] ?? 0}m · 동선 점수 ${suggestion['score'] ?? 0}점',
                  ),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: () => Navigator.pop(context, suggestion),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
