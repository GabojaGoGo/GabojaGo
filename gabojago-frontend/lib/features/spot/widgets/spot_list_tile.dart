import 'package:flutter/material.dart';
import 'package:geolocator/geolocator.dart';

import 'package:tripmate/core/widgets/benefit_chip.dart';
import 'package:tripmate/core/widgets/spot_card.dart';

class SpotListTile extends StatelessWidget {
  final SpotData spot;
  final bool isSelected;
  final double lat;
  final double lng;
  final VoidCallback onTap;

  const SpotListTile({
    super.key,
    required this.spot,
    required this.isSelected,
    required this.lat,
    required this.lng,
    required this.onTap,
  });

  Color get _congestionColor {
    switch (spot.congestion) {
      case '낮음':
        return const Color(0xFF1B8C6E);
      case '보통':
        return const Color(0xFFF57C00);
      case '높음':
        return const Color(0xFFD84315);
      case '예측중':
        return const Color(0xFF607D8B);
      default:
        return const Color(0xFF616161);
    }
  }

  @override
  Widget build(BuildContext context) {
    final distM = Geolocator.distanceBetween(
      lat,
      lng,
      spot.latitude,
      spot.longitude,
    );
    final distLabel = distM < 1000
        ? '${distM.round()}m'
        : '${(distM / 1000).toStringAsFixed(distM >= 10000 ? 0 : 1)}km';

    return InkWell(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        color: isSelected
            ? const Color(0xFF1B8C6E).withValues(alpha: 0.07)
            : Colors.transparent,
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        child: Row(
          children: [
            Stack(
              children: [
                ClipRRect(
                  borderRadius: BorderRadius.circular(10),
                  child: Container(
                    width: 72,
                    height: 72,
                    color: spot.placeholderColor.withValues(alpha: 0.1),
                    child: (spot.imageUrl != null && spot.imageUrl!.isNotEmpty)
                        ? Image.network(
                            spot.imageUrl!,
                            fit: BoxFit.cover,
                            errorBuilder: (_, _, _) => _placeholder(),
                          )
                        : _placeholder(),
                  ),
                ),
                if (isSelected)
                  Positioned(
                    top: 4,
                    right: 4,
                    child: Container(
                      padding: const EdgeInsets.all(3),
                      decoration: const BoxDecoration(
                        color: Color(0xFF1B8C6E),
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(
                        Icons.check,
                        color: Colors.white,
                        size: 10,
                      ),
                    ),
                  ),
              ],
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    spot.spotName,
                    style: TextStyle(
                      fontSize: 15,
                      fontWeight: FontWeight.w700,
                      color: isSelected
                          ? const Color(0xFF1B8C6E)
                          : const Color(0xFF1A1A1A),
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  const SizedBox(height: 3),
                  Row(
                    children: [
                      Icon(
                        Icons.place_outlined,
                        size: 12,
                        color: Colors.grey.shade500,
                      ),
                      const SizedBox(width: 2),
                      Expanded(
                        child: Text(
                          spot.areaName,
                          style: TextStyle(
                            fontSize: 12,
                            color: Colors.grey.shade500,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      const SizedBox(width: 8),
                      Icon(
                        Icons.directions_walk,
                        size: 12,
                        color: Colors.grey.shade600,
                      ),
                      const SizedBox(width: 2),
                      Text(
                        distLabel,
                        style: TextStyle(
                          fontSize: 12,
                          color: Colors.grey.shade700,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 7),
                  BenefitChip(
                    label: '혼잡도 ${spot.congestion}',
                    backgroundColor: _congestionColor.withValues(alpha: 0.1),
                    textColor: _congestionColor,
                    icon: Icons.people_outline,
                  ),
                ],
              ),
            ),
            const SizedBox(width: 8),
            Icon(
              Icons.chevron_right_rounded,
              color: isSelected
                  ? const Color(0xFF1B8C6E)
                  : Colors.grey.shade400,
              size: 20,
            ),
          ],
        ),
      ),
    );
  }

  Widget _placeholder() {
    return Center(
      child: Icon(
        Icons.image_not_supported_outlined,
        color: spot.placeholderColor,
        size: 22,
      ),
    );
  }
}
