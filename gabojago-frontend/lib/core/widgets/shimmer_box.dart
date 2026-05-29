// shimmer_box.dart
// 패키지 없이 구현한 shimmer 스켈레톤 위젯
// 로딩 상태의 CircularProgressIndicator 대체용

import 'package:flutter/material.dart';

class ShimmerBox extends StatefulWidget {
  final double? width;
  final double height;
  final double radius;

  const ShimmerBox({
    super.key,
    this.width,
    required this.height,
    this.radius = 8,
  });

  @override
  State<ShimmerBox> createState() => _ShimmerBoxState();
}

class _ShimmerBoxState extends State<ShimmerBox>
    with SingleTickerProviderStateMixin {
  late final AnimationController _ctrl;

  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1400),
    )..repeat();
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _ctrl,
      builder: (context, _) {
        final slide = -1.5 + _ctrl.value * 3.0;
        return Container(
          width: widget.width,
          height: widget.height,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(widget.radius),
            gradient: LinearGradient(
              begin: Alignment(slide - 1, 0),
              end: Alignment(slide + 1, 0),
              colors: const [
                Color(0xFFEAECEF),
                Color(0xFFF5F6F8),
                Color(0xFFEAECEF),
              ],
            ),
          ),
        );
      },
    );
  }
}

/// 스팟 카드 shimmer 스켈레톤 (홈화면 가로 스크롤용)
class SpotCardShimmer extends StatelessWidget {
  const SpotCardShimmer({super.key});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 160,
      margin: const EdgeInsets.only(right: 12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          const BoxShadow(color: Color(0x05000000), blurRadius: 0, spreadRadius: 1),
          const BoxShadow(color: Color(0x0A000000), blurRadius: 8, offset: Offset(0, 2)),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          ShimmerBox(height: 120, radius: 0),
          const ClipRRect(), // 상단 radius 처리는 parent에서
          Padding(
            padding: const EdgeInsets.all(10),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                ShimmerBox(height: 13, radius: 4),
                const SizedBox(height: 6),
                ShimmerBox(width: 80, height: 11, radius: 4),
                const SizedBox(height: 8),
                ShimmerBox(width: 100, height: 20, radius: 10),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// 축제 리스트 아이템 shimmer
class FestivalItemShimmer extends StatelessWidget {
  const FestivalItemShimmer({super.key});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 5),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          const BoxShadow(color: Color(0x05000000), blurRadius: 0, spreadRadius: 1),
          const BoxShadow(color: Color(0x0A000000), blurRadius: 8, offset: Offset(0, 2)),
        ],
      ),
      child: Row(
        children: [
          ShimmerBox(width: 48, height: 48, radius: 10),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                ShimmerBox(height: 14, radius: 4),
                const SizedBox(height: 6),
                ShimmerBox(width: 120, height: 12, radius: 4),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// 코스 카드 shimmer (플래너/코스 결과 로딩용)
class CourseCardShimmer extends StatelessWidget {
  const CourseCardShimmer({super.key});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          const BoxShadow(color: Color(0x05000000), blurRadius: 0, spreadRadius: 1),
          const BoxShadow(color: Color(0x0A000000), blurRadius: 8, offset: Offset(0, 2)),
          const BoxShadow(color: Color(0x14000000), blurRadius: 16, offset: Offset(0, 4)),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                ShimmerBox(width: 60, height: 12, radius: 4),
                const SizedBox(height: 8),
                ShimmerBox(height: 18, radius: 4),
                const SizedBox(height: 4),
                ShimmerBox(width: 160, height: 18, radius: 4),
                const SizedBox(height: 12),
                // 장소 행 3개
                for (int i = 0; i < 3; i++) ...[
                  Row(
                    children: [
                      ShimmerBox(width: 22, height: 22, radius: 11),
                      const SizedBox(width: 8),
                      Expanded(child: ShimmerBox(height: 13, radius: 4)),
                    ],
                  ),
                  if (i < 2) const SizedBox(height: 8),
                ],
                const SizedBox(height: 12),
              ],
            ),
          ),
          const Divider(height: 1),
          Padding(
            padding: const EdgeInsets.all(12),
            child: ShimmerBox(height: 38, radius: 10),
          ),
        ],
      ),
    );
  }
}

/// 혜택 카드 shimmer (홈화면용)
class BenefitCardShimmer extends StatelessWidget {
  const BenefitCardShimmer({super.key});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.fromLTRB(16, 0, 16, 8),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          const BoxShadow(color: Color(0x05000000), blurRadius: 0, spreadRadius: 1),
          const BoxShadow(color: Color(0x0A000000), blurRadius: 8, offset: Offset(0, 2)),
        ],
      ),
      child: Row(
        children: [
          ShimmerBox(width: 48, height: 48, radius: 12),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                ShimmerBox(height: 14, radius: 4),
                const SizedBox(height: 6),
                ShimmerBox(width: 140, height: 12, radius: 4),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
