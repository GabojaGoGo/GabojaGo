import 'package:flutter/material.dart';

const _kPrimary = Color(0xFF2E7D6B);
const _kText1   = Color(0xFF1A1A1A);
const _kText3   = Color(0xFF9E9E9E);
const _kBorder  = Color(0xFFE8EAED);

class BenefitReportCard extends StatelessWidget {
  final String Function(int) fmt;
  final int totalSaved;
  final int benefitCount;
  final int regionCount;

  const BenefitReportCard({
    super.key,
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
        Container(
          width: double.infinity,
          padding: const EdgeInsets.symmetric(vertical: 14),
          decoration: BoxDecoration(
            color: _kPrimary.withValues(alpha: 0.07),
            borderRadius: BorderRadius.circular(12),
          ),
          child: Column(children: [
            Text('누적 절약 금액',
                style: TextStyle(
                    fontSize: 12,
                    color: _kPrimary.withValues(alpha: 0.8))),
            const SizedBox(height: 4),
            Text('${fmt(totalSaved)}원',
                style: const TextStyle(
                    fontSize: 28,
                    fontWeight: FontWeight.w800,
                    color: _kPrimary)),
          ]),
        ),
        const SizedBox(height: 14),
        Row(children: [
          _Stat(
              value: '$benefitCount건',
              label: '이용한 보조금',
              icon: Icons.card_giftcard_outlined),
          _VDiv(),
          _Stat(
              value: '$regionCount곳',
              label: '방문 지역',
              icon: Icons.place_outlined),
          _VDiv(),
          _Stat(
              value: '${fmt(avg)}원',
              label: '여행당 평균',
              icon: Icons.trending_up_outlined),
        ]),
      ]),
    );
  }
}

class _Stat extends StatelessWidget {
  final String value;
  final String label;
  final IconData icon;
  const _Stat({required this.value, required this.label, required this.icon});

  @override
  Widget build(BuildContext context) => Expanded(
        child: Column(children: [
          Icon(icon, size: 18, color: const Color(0xFF5C4AE3)),
          const SizedBox(height: 4),
          Text(value,
              style: const TextStyle(
                  fontWeight: FontWeight.w800,
                  fontSize: 14,
                  color: _kText1)),
          Text(label,
              style: const TextStyle(fontSize: 9, color: _kText3)),
        ]),
      );
}

class _VDiv extends StatelessWidget {
  @override
  Widget build(BuildContext context) =>
      Container(width: 1, height: 36, color: _kBorder);
}
