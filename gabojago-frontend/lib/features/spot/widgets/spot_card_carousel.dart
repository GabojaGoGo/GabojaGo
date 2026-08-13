import 'package:flutter/material.dart';
import 'package:geolocator/geolocator.dart';

import 'package:tripmate/core/widgets/spot_card.dart';
import 'package:tripmate/features/spot/spot_detail_screen.dart';

class SpotCardCarousel extends StatelessWidget {
  final List<SpotData> spots;
  final int currentIndex;
  final double lat;
  final double lng;
  final PageController pageController;
  final ValueChanged<int> onPageChanged;

  const SpotCardCarousel({
    super.key,
    required this.spots,
    required this.currentIndex,
    required this.lat,
    required this.lng,
    required this.pageController,
    required this.onPageChanged,
  });

  Color _congestionColor(String? congestion) {
    switch (congestion) {
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
    return PageView.builder(
      controller: pageController,
      onPageChanged: onPageChanged,
      itemCount: spots.length,
      itemBuilder: (context, idx) => _SpotCard(
        spot: spots[idx],
        isActive: idx == currentIndex,
        lat: lat,
        lng: lng,
        congestionColor: _congestionColor(spots[idx].congestion),
      ),
    );
  }
}

class _SpotCard extends StatelessWidget {
  final SpotData spot;
  final bool isActive;
  final double lat;
  final double lng;
  final Color congestionColor;

  const _SpotCard({
    required this.spot,
    required this.isActive,
    required this.lat,
    required this.lng,
    required this.congestionColor,
  });

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

    return GestureDetector(
      onTap: () => Navigator.push(
        context,
        MaterialPageRoute<void>(builder: (_) => SpotDetailScreen(spot: spot)),
      ),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 220),
        curve: Curves.easeOut,
        margin: EdgeInsets.only(
          left: 8,
          right: 8,
          top: isActive ? 8 : 20,
          bottom: isActive ? 8 : 4,
        ),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(16),
          border: isActive
              ? Border.all(color: const Color(0xFF1B8C6E), width: 1.5)
              : null,
          boxShadow: [
            const BoxShadow(
              color: Color(0x05000000),
              blurRadius: 0,
              spreadRadius: 1,
            ),
            BoxShadow(
              color: Color(isActive ? 0x24000000 : 0x0A000000),
              blurRadius: isActive ? 12 : 8,
              offset: Offset(0, isActive ? 4 : 2),
            ),
          ],
        ),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(15),
          child: Row(
            children: [
              SizedBox(
                width: 110,
                child: (spot.imageUrl != null && spot.imageUrl!.isNotEmpty)
                    ? Image.network(
                        spot.imageUrl!,
                        fit: BoxFit.cover,
                        height: double.infinity,
                        errorBuilder: (_, _, _) => _placeholder(),
                      )
                    : _placeholder(),
              ),
              Expanded(
                child: Padding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 14,
                    vertical: 12,
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Text(
                        spot.spotName,
                        style: TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.w700,
                          color: isActive
                              ? const Color(0xFF1B8C6E)
                              : const Color(0xFF1A1A1A),
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      const SizedBox(height: 4),
                      Row(
                        children: [
                          Icon(
                            Icons.place_outlined,
                            size: 11,
                            color: Colors.grey.shade500,
                          ),
                          const SizedBox(width: 2),
                          Expanded(
                            child: Text(
                              spot.areaName,
                              style: TextStyle(
                                fontSize: 11,
                                color: Colors.grey.shade500,
                              ),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 3),
                      Row(
                        children: [
                          Icon(
                            Icons.directions_walk,
                            size: 11,
                            color: Colors.grey.shade600,
                          ),
                          const SizedBox(width: 2),
                          Text(
                            distLabel,
                            style: TextStyle(
                              fontSize: 11,
                              fontWeight: FontWeight.w600,
                              color: Colors.grey.shade700,
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 8),
                      Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 8,
                          vertical: 3,
                        ),
                        decoration: BoxDecoration(
                          color: congestionColor.withValues(alpha: 0.1),
                          borderRadius: BorderRadius.circular(6),
                        ),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(
                              Icons.people_outline,
                              size: 11,
                              color: congestionColor,
                            ),
                            const SizedBox(width: 3),
                            Text(
                              '혼잡도 ${spot.congestion}',
                              style: TextStyle(
                                fontSize: 10,
                                fontWeight: FontWeight.w600,
                                color: congestionColor,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _placeholder() {
    return Container(
      color: spot.placeholderColor.withValues(alpha: 0.1),
      child: Center(
        child: Icon(
          Icons.image_not_supported_outlined,
          color: spot.placeholderColor,
          size: 24,
        ),
      ),
    );
  }
}
