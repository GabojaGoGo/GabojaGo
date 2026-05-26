// subsidy_banner.dart
// 보조금 배너 카드 위젯 (홈 화면, 보조금 화면에서 재사용)
// 제목, 부제, 상태 칩(마감 D-day / 잔여 예산), CTA 버튼 포함

import 'package:flutter/material.dart';
import 'benefit_chip.dart';

class SubsidyBanner extends StatelessWidget {
  final String title;
  final String subtitle;
  final String deadline;     // 예: "D-23 마감"
  final String budgetLeft;   // 예: "잔여 예산 68%"
  final VoidCallback? onTap;

  const SubsidyBanner({
    super.key,
    required this.title,
    required this.subtitle,
    required this.deadline,
    required this.budgetLeft,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: [Color(0xFF5C4AE3), Color(0xFF7B6CF0)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // 상태 칩 행
            Row(
              children: [
                BenefitChip(
                  label: deadline,
                  backgroundColor: const Color(0xFFD84315).withValues(alpha: 0.9),
                  textColor: Colors.white,
                  icon: Icons.timer_outlined,
                ),
                const SizedBox(width: 8),
                BenefitChip(
                  label: budgetLeft,
                  backgroundColor: const Color(0xFF1B8C6E).withValues(alpha: 0.85),
                  textColor: Colors.white,
                  icon: Icons.account_balance_wallet_outlined,
                ),
              ],
            ),
            const SizedBox(height: 12),
            // 제목
            Text(
              title,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 18,
                fontWeight: FontWeight.w800,
                height: 1.3,
              ),
            ),
            const SizedBox(height: 6),
            // 부제
            Text(
              subtitle,
              style: TextStyle(
                color: Colors.white.withValues(alpha: 0.85),
                fontSize: 13,
              ),
            ),
            const SizedBox(height: 16),
            // CTA 버튼
            SizedBox(
              width: double.infinity,
              child: ElevatedButton(
                onPressed: onTap,
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.white,
                  foregroundColor: const Color(0xFF5C4AE3),
                  elevation: 0,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(10),
                  ),
                  padding: const EdgeInsets.symmetric(vertical: 12),
                ),
                child: const Text(
                  '신청 가이드 보기',
                  style: TextStyle(fontWeight: FontWeight.w700, fontSize: 14),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
