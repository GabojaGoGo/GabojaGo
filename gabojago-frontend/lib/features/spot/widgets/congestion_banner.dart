import 'package:flutter/material.dart';

class CongestionBanner extends StatelessWidget {
  final bool isRelaxed;
  const CongestionBanner({super.key, required this.isRelaxed});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 11),
      decoration: BoxDecoration(
        color: isRelaxed ? const Color(0xFFE8F5E9) : const Color(0xFFFBE9E7),
        borderRadius: BorderRadius.circular(14),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.10),
            blurRadius: 10,
            offset: const Offset(0, 3),
          ),
        ],
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            isRelaxed
                ? Icons.sentiment_satisfied_alt_rounded
                : Icons.sentiment_dissatisfied_rounded,
            color: isRelaxed
                ? const Color(0xFF1B8C6E)
                : const Color(0xFFD84315),
            size: 20,
          ),
          const SizedBox(width: 10),
          Text(
            isRelaxed ? '지금 주변은 대체로 여유로워요' : '지금 주변은 다소 혼잡한 편이에요',
            style: TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.w700,
              color: isRelaxed
                  ? const Color(0xFF1B6B51)
                  : const Color(0xFFBF360C),
            ),
          ),
        ],
      ),
    );
  }
}
