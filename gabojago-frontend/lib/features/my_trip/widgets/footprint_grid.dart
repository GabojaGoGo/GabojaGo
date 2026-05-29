import 'package:flutter/material.dart';

const _kText2  = Color(0xFF707070);
const _kText3  = Color(0xFF9E9E9E);
const _kBorder = Color(0xFFE8EAED);
const _kText1  = Color(0xFF1A1A1A);

const _kRegionColors = [
  Color(0xFF2E7D6B), Color(0xFF1565C0), Color(0xFF5C4AE3),
  Color(0xFFF57C00), Color(0xFFD32F2F), Color(0xFF388E3C),
];
const _kRegionIcons = [
  Icons.forest_outlined,          Icons.beach_access_outlined,
  Icons.account_balance_outlined, Icons.restaurant_outlined,
  Icons.landscape_outlined,       Icons.park_outlined,
];

class FootprintGrid extends StatelessWidget {
  final List<Map<String, dynamic>> footprints;
  const FootprintGrid({super.key, required this.footprints});

  @override
  Widget build(BuildContext context) {
    if (footprints.isEmpty) return const _EmptyFootprints();

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
        final sortedDates = entry.value
            .map((f) => (f['visitedAt'] as String?) ?? '')
            .where((s) => s.isNotEmpty)
            .toList()
          ..sort((a, b) => b.compareTo(a));
        final lastVisitRaw = sortedDates.isNotEmpty ? sortedDates.first : '';
        final lastVisit = lastVisitRaw.length >= 7
            ? lastVisitRaw.substring(0, 7).replaceAll('-', '.')
            : lastVisitRaw;
        return _RegionCard(data: {
          'name':      entry.key,
          'spotCount': entry.value.length,
          'lastVisit': lastVisit,
          'color':     _kRegionColors[i % _kRegionColors.length],
          'icon':      _kRegionIcons[i % _kRegionIcons.length],
          'tags':      allTags,
        });
      },
    );
  }
}

class _RegionCard extends StatelessWidget {
  final Map<String, dynamic> data;
  const _RegionCard({required this.data});

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
          decoration: BoxDecoration(
              color: color.withValues(alpha: 0.12), shape: BoxShape.circle),
          child: Icon(data['icon'] as IconData, color: color, size: 22),
        ),
        const SizedBox(height: 7),
        Text(
          data['name'] as String,
          style: const TextStyle(
              fontSize: 12, fontWeight: FontWeight.w700, color: _kText1),
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 2),
        Text('${data['spotCount']}곳',
            style: TextStyle(fontSize: 10, color: Colors.grey.shade500)),
        const SizedBox(height: 5),
        Wrap(
          spacing: 3, runSpacing: 3, alignment: WrapAlignment.center,
          children: tags
              .map((t) => Container(
                    padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1),
                    decoration: BoxDecoration(
                        color: color.withValues(alpha: 0.1),
                        borderRadius: BorderRadius.circular(4)),
                    child: Text(t,
                        style: TextStyle(
                            fontSize: 8,
                            color: color,
                            fontWeight: FontWeight.w600)),
                  ))
              .toList(),
        ),
      ]),
    );
  }
}

class _EmptyFootprints extends StatelessWidget {
  const _EmptyFootprints();

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
          Text('아직 방문 기록이 없어요',
              style: TextStyle(fontSize: 14, color: _kText2)),
          SizedBox(height: 4),
          Text('관광지를 방문하면 자동으로 기록됩니다',
              style: TextStyle(fontSize: 12, color: _kText3)),
        ]),
      ),
    );
  }
}
