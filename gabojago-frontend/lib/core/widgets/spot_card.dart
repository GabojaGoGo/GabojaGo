// spot_card.dart
import 'package:flutter/material.dart';
import 'package:tripmate/core/widgets/benefit_chip.dart';
import 'package:tripmate/core/widgets/shimmer_box.dart';

class SpotData {
  final int id;
  final String areaName;
  final String spotName;
  final String category;
  final String congestion;
  final String congestionSource;
  final String? congestionBaseYmd;
  final String? imageUrl; // 이미지 URL 추가
  final Color placeholderColor;
  final double latitude;
  final double longitude;

  const SpotData({
    required this.id,
    required this.areaName,
    required this.spotName,
    required this.category,
    required this.congestion,
    required this.congestionSource,
    this.congestionBaseYmd,
    this.imageUrl,
    required this.placeholderColor,
    required this.latitude,
    required this.longitude,
  });

  factory SpotData.fromJson(Map<String, dynamic> json) {
    final congestionValue = (json['congestion'] as String?)?.trim();
    return SpotData(
      id: (json['id'] as num?)?.toInt() ?? 0,
      areaName: (json['address'] as String?)?.split(' ').take(2).join(' ') ?? '지역 정보 없음',
      spotName: json['name'] ?? '이름 없음',
      category: '관광지',
      congestion: (congestionValue == null || congestionValue.isEmpty) ? '예측중' : congestionValue,
      congestionSource: (json['congestionSource'] as String?) ?? 'pending',
      congestionBaseYmd: json['congestionBaseYmd'] as String?,
      imageUrl: json['imageUrl'],
      placeholderColor: const Color(0xFF2E7D6B),
      latitude: (json['latitude'] as num?)?.toDouble() ?? 0.0,
      longitude: (json['longitude'] as num?)?.toDouble() ?? 0.0,
    );
  }

  SpotData copyWith({
    String? congestion,
    String? congestionSource,
    String? congestionBaseYmd,
  }) {
    return SpotData(
      id: id,
      areaName: areaName,
      spotName: spotName,
      category: category,
      congestion: congestion ?? this.congestion,
      congestionSource: congestionSource ?? this.congestionSource,
      congestionBaseYmd: congestionBaseYmd ?? this.congestionBaseYmd,
      imageUrl: imageUrl,
      placeholderColor: placeholderColor,
      latitude: latitude,
      longitude: longitude,
    );
  }
}

class SpotCard extends StatelessWidget {
  final SpotData spot;

  const SpotCard({super.key, required this.spot});

  Color get _congestionColor {
    switch (spot.congestion) {
      case '예측중': return const Color(0xFF607D8B);
      case '낮음': return const Color(0xFF1B8C6E);
      case '보통': return const Color(0xFFF57C00);
      case '높음': return const Color(0xFFD84315);
      case '정보 없음': return const Color(0xFF616161);
      default: return const Color(0xFFD84315);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 160,
      margin: const EdgeInsets.only(right: 12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          BoxShadow(color: Color(0x05000000), blurRadius: 0, spreadRadius: 1),
          BoxShadow(color: Color(0x0A000000), blurRadius: 8, offset: Offset(0, 2)),
          BoxShadow(color: Color(0x14000000), blurRadius: 16, offset: Offset(0, 4)),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 🖼️ 이미지 영역 (null 처리)
          ClipRRect(
            borderRadius: const BorderRadius.vertical(top: Radius.circular(16)),
            child: SizedBox(
              height: 120,
              width: double.infinity,
              child: (spot.imageUrl != null && spot.imageUrl!.isNotEmpty)
                  ? Image.network(
                      spot.imageUrl!,
                      fit: BoxFit.cover,
                      loadingBuilder: (context, child, loadingProgress) {
                        if (loadingProgress == null) return child;
                        return const ShimmerBox(height: 120, radius: 0);
                      },
                      errorBuilder: (context, error, stackTrace) => _buildPlaceholder(),
                    )
                  : _buildPlaceholder(),
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(10),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  spot.spotName,
                  style: const TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w700,
                    color: Color(0xFF1A1A1A),
                    letterSpacing: -0.2,
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 2),
                Text(
                  spot.areaName,
                  style: const TextStyle(fontSize: 11, color: Color(0xFF6B7280)),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 6),
                BenefitChip(
                  label: '혼잡도 추정 ${spot.congestion}',
                  backgroundColor: _congestionColor.withValues(alpha: 0.1),
                  textColor: _congestionColor,
                  icon: Icons.people_outline,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildPlaceholder() {
    return Container(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [
            spot.placeholderColor.withValues(alpha: 0.25),
            spot.placeholderColor.withValues(alpha: 0.12),
          ],
        ),
      ),
      child: Center(
        child: Icon(
          Icons.landscape_outlined,
          color: spot.placeholderColor.withValues(alpha: 0.45),
          size: 30,
        ),
      ),
    );
  }
}
