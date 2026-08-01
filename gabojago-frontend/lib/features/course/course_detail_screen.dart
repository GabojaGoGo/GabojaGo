import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart' as latlong;
import 'package:tripmate/infrastructure/api_service.dart';
import 'package:tripmate/core/services/user_data_service.dart';
import 'package:tripmate/features/course/widgets/slot_suggestion_sheet.dart';

class CourseDetailScreen extends StatefulWidget {
  final Map<String, dynamic> course;
  final List<String> purposes;
  final String travelConcept;

  const CourseDetailScreen({
    super.key,
    required this.course,
    this.purposes = const [],
    this.travelConcept = '',
  });

  @override
  State<CourseDetailScreen> createState() => _CourseDetailScreenState();
}

class _CourseDetailScreenState extends State<CourseDetailScreen> {
  Map<String, dynamic>? _detail;
  bool _loading = true;
  bool _saved = false;

  @override
  void initState() {
    super.initState();
    final contentId = widget.course['contentId'] as String? ?? '';
    _saved = UserDataService.instance.isCourseSaved(contentId);

    // course에 이미 places가 있으면 바로 사용, 없으면 API 호출
    final existingPlaces = widget.course['places'] as List?;
    if (existingPlaces != null && existingPlaces.isNotEmpty) {
      _detail = {
        'places': widget.course['places'],
        'distance': widget.course['distance'] ?? '',
        'taketime': widget.course['taketime'] ?? '',
        'theme': widget.course['theme'] ?? '',
        'routePaths': widget.course['routePaths'] ?? [],
        'nearbyRestaurants': widget.course['nearbyRestaurants'] ?? [],
        'nearbyAccommodations': widget.course['nearbyAccommodations'] ?? [],
      };
      _loading = false;
    } else {
      _loadDetail(contentId);
    }
  }

  Future<void> _loadDetail(String contentId) async {
    try {
      final detail = await ApiService.getCourseDetail(
        contentId,
        purposes: widget.purposes,
      );
      if (mounted) {
        setState(() {
          _detail = detail;
          _loading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _loading = false;
        });
      }
    }
  }

  Future<void> _toggleSave() async {
    final contentId = widget.course['contentId'] as String? ?? '';
    if (_saved) {
      await UserDataService.instance.removeSavedCourse(contentId);
    } else {
      await UserDataService.instance.saveCourse(widget.course);
    }
    if (mounted) {
      setState(() => _saved = !_saved);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(_saved ? '플래너에 저장됐어요!' : '플래너에서 삭제됐어요')),
      );
    }
  }

  Future<void> _showSlotSuggestions(
    int placeIndex,
    List<Map<String, dynamic>> places,
  ) async {
    final currentPlaceIds = places
        .map((place) => place['placeId'])
        .whereType<num>()
        .map((id) => id.toInt())
        .toList();
    if (currentPlaceIds.length != places.length) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('이 코스는 아직 장소 교체를 지원하지 않아요.')),
      );
      return;
    }

    try {
      final response = await ApiService.getSlotSuggestions(
        regionKey: widget.course['areaCode'] as String? ?? 'busan',
        duration: widget.course['duration'] as String? ?? '1n2d',
        travelConcept: widget.travelConcept,
        travelMode: widget.course['travelMode'] as String? ?? 'CAR',
        slotOrder: placeIndex + 1,
        day: placeIndex < places.length
            ? places[placeIndex]['day'] as int?
            : null,
        timeLabel: places[placeIndex]['timeLabel'] as String?,
        slotType: (places[placeIndex]['slotType'] as String?)?.toUpperCase(),
        subtypeCodes: List<String>.from(
          places[placeIndex]['subtypeCodes'] as List? ?? const [],
        ),
        currentPlaceIds: currentPlaceIds,
      );
      if (!mounted) return;
      final selected = await showModalBottomSheet<Map<String, dynamic>>(
        context: context,
        showDragHandle: true,
        isScrollControlled: true,
        builder: (_) => SlotSuggestionSheet(
          suggestions: List<Map<String, dynamic>>.from(
            (response['suggestions'] as List? ?? const []).whereType<Map>().map(
              (value) => Map<String, dynamic>.from(value),
            ),
          ),
        ),
      );
      if (selected == null || !mounted) return;

      final updated = List<Map<String, dynamic>>.from(places);
      final original = Map<String, dynamic>.from(updated[placeIndex]);
      updated[placeIndex] = {
        ...original,
        'placeId': selected['placeId'],
        'subname': selected['placeName'],
        'address': selected['address'] ?? '',
        'imageUrl': selected['imageUrl'] ?? '',
        'mapx': selected['lng'],
        'mapy': selected['lat'],
        'overview':
            '동선 우회 ${selected['detourMeters'] ?? 0}m · ${selected['score'] ?? 0}점',
      };
      if (placeIndex > 0) {
        updated[placeIndex - 1] = {
          ...updated[placeIndex - 1],
          'travelMinutesToNext': null,
        };
      }
      if (placeIndex < updated.length - 1) {
        updated[placeIndex]['travelMinutesToNext'] = null;
      }
      setState(() {
        _detail = {...?_detail, 'places': updated, 'routePaths': <dynamic>[]};
        widget.course['places'] = updated;
        widget.course['routePaths'] = <dynamic>[];
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('${selected['placeName']}(으)로 바꿨어요.')),
      );
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('교체할 장소를 찾지 못했어요.')));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final imageUrl = widget.course['imageUrl'] as String? ?? '';
    final hasImage = imageUrl.isNotEmpty && !imageUrl.contains('placeholder');

    final places = _detail != null
        ? List<Map<String, dynamic>>.from(
            (_detail!['places'] as List? ?? []).map(
              (e) => Map<String, dynamic>.from(e as Map),
            ),
          )
        : <Map<String, dynamic>>[];

    final nearbyRestaurants = _detail != null
        ? List<Map<String, dynamic>>.from(
            (_detail!['nearbyRestaurants'] as List? ?? []).map(
              (e) => Map<String, dynamic>.from(e as Map),
            ),
          )
        : <Map<String, dynamic>>[];

    final nearbyAccommodations = _detail != null
        ? List<Map<String, dynamic>>.from(
            (_detail!['nearbyAccommodations'] as List? ?? []).map(
              (e) => Map<String, dynamic>.from(e as Map),
            ),
          )
        : <Map<String, dynamic>>[];
    final routePoints = _RoutePoint.fromPlaces(places);
    final routePaths = _RoutePath.fromRaw(
      _detail?['routePaths'] as List? ?? const [],
    );

    return Scaffold(
      backgroundColor: colorScheme.surface,
      extendBodyBehindAppBar: true,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        foregroundColor: Colors.white,
        elevation: 0,
        actions: [
          Padding(
            padding: const EdgeInsets.only(right: 8),
            child: IconButton.filledTonal(
              icon: Icon(_saved ? Icons.bookmark : Icons.bookmark_outline),
              onPressed: _toggleSave,
              tooltip: _saved ? '저장 해제' : '플래너 저장',
            ),
          ),
        ],
      ),
      body: Stack(
        children: [
          Positioned.fill(
            child: _CourseMapBackdrop(
              points: routePoints,
              routePaths: routePaths,
              imageUrl: imageUrl,
              hasImage: hasImage,
            ),
          ),
          if (routePoints.isNotEmpty)
            Positioned(
              top: MediaQuery.paddingOf(context).top + kToolbarHeight + 8,
              left: 14,
              right: 14,
              child: _RouteLegend(points: routePoints),
            ),
          DraggableScrollableSheet(
            initialChildSize: 0.18,
            minChildSize: 0.12,
            maxChildSize: 0.94,
            snap: true,
            snapSizes: const [0.18, 0.94],
            builder: (context, scrollController) {
              return DecoratedBox(
                decoration: BoxDecoration(
                  color: colorScheme.surface,
                  borderRadius: const BorderRadius.vertical(
                    top: Radius.circular(22),
                  ),
                  boxShadow: const [
                    BoxShadow(
                      color: Color(0x26000000),
                      blurRadius: 18,
                      offset: Offset(0, -4),
                    ),
                  ],
                ),
                child: ListView(
                  controller: scrollController,
                  padding: const EdgeInsets.fromLTRB(20, 10, 20, 28),
                  children: [
                    Center(
                      child: Container(
                        width: 44,
                        height: 5,
                        margin: const EdgeInsets.only(bottom: 14),
                        decoration: BoxDecoration(
                          color: const Color(0xFFD1D5DB),
                          borderRadius: BorderRadius.circular(999),
                        ),
                      ),
                    ),
                    // 지역
                    if ((widget.course['region'] as String? ?? '').isNotEmpty)
                      Row(
                        children: [
                          Icon(
                            Icons.location_on_outlined,
                            size: 14,
                            color: colorScheme.primary,
                          ),
                          const SizedBox(width: 4),
                          Text(
                            widget.course['region'] as String,
                            style: TextStyle(
                              fontSize: 13,
                              color: colorScheme.primary,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ],
                      ),
                    const SizedBox(height: 8),

                    // 제목
                    Text(
                      widget.course['title'] as String? ?? '',
                      style: const TextStyle(
                        fontSize: 22,
                        fontWeight: FontWeight.w800,
                        letterSpacing: -0.4,
                        height: 1.2,
                      ),
                    ),
                    const SizedBox(height: 12),

                    // 거리/시간/테마
                    if (_detail != null)
                      Wrap(
                        spacing: 8,
                        runSpacing: 6,
                        children: [
                          if ((_detail!['distance'] as String? ?? '')
                              .isNotEmpty)
                            _InfoPill(
                              icon: Icons.straighten_outlined,
                              label: _detail!['distance'] as String,
                            ),
                          if ((_detail!['taketime'] as String? ?? '')
                              .isNotEmpty)
                            _InfoPill(
                              icon: Icons.schedule_outlined,
                              label: _detail!['taketime'] as String,
                            ),
                          if ((_detail!['theme'] as String? ?? '').isNotEmpty)
                            _InfoPill(
                              icon: Icons.tag_outlined,
                              label: _detail!['theme'] as String,
                            ),
                        ],
                      ),
                    if (_detail != null) const SizedBox(height: 20),

                    FilledButton.icon(
                      onPressed: _toggleSave,
                      icon: Icon(
                        _saved ? Icons.bookmark : Icons.bookmark_add_outlined,
                      ),
                      label: Text(_saved ? '플래너에서 삭제' : '플래너에 담기'),
                      style: FilledButton.styleFrom(
                        minimumSize: const Size(double.infinity, 48),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12),
                        ),
                        backgroundColor: _saved
                            ? Colors.grey.shade400
                            : colorScheme.primary,
                      ),
                    ),
                    const SizedBox(height: 20),

                    // 로딩
                    if (_loading)
                      const Center(
                        child: Padding(
                          padding: EdgeInsets.symmetric(vertical: 32),
                          child: CircularProgressIndicator(),
                        ),
                      ),

                    // 코스 구성 장소 목록
                    if (!_loading && places.isNotEmpty) ...[
                      Text(
                        '코스 구성 (${places.length}곳)',
                        style: const TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      const SizedBox(height: 14),
                      ...List.generate(places.length, (i) {
                        final place = places[i];
                        final travelMin = place['travelMinutesToNext'] as int?;
                        final dayLabel = place['dayLabel'] as String?;
                        final prevDayLabel = i > 0
                            ? places[i - 1]['dayLabel'] as String?
                            : null;
                        final showDayHeader =
                            dayLabel != null &&
                            (i == 0 || dayLabel != prevDayLabel);

                        return Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            // DAY 구분 헤더
                            if (showDayHeader)
                              _DayDivider(label: dayLabel, isFirst: i == 0),
                            _PlaceDetailItem(
                              index: i,
                              name: place['subname'] as String? ?? '',
                              overview: place['overview'] as String? ?? '',
                              imageUrl: place['imageUrl'] as String? ?? '',
                              address: place['address'] as String? ?? '',
                              tel: place['tel'] as String? ?? '',
                              usetime: place['usetime'] as String? ?? '',
                              usefee: place['usefee'] as String? ?? '',
                              isLast:
                                  i == places.length - 1 && travelMin == null,
                              slotType: place['slotType'] as String?,
                              timeLabel: place['timeLabel'] as String?,
                              onReplace: () => _showSlotSuggestions(i, places),
                            ),
                            // 이동 시간 divider
                            if (i < places.length - 1)
                              _TravelTimeDivider(minutes: travelMin),
                          ],
                        );
                      }),
                      const SizedBox(height: 24),
                    ],

                    // 장소 없음
                    if (!_loading && _detail != null && places.isEmpty)
                      Container(
                        padding: const EdgeInsets.all(16),
                        decoration: BoxDecoration(
                          color: colorScheme.surfaceContainerLow,
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: Row(
                          children: [
                            Icon(
                              Icons.info_outline,
                              size: 18,
                              color: const Color(0xFF9CA3AF),
                            ),
                            const SizedBox(width: 8),
                            Expanded(
                              child: Text(
                                '이 코스의 상세 정보가 제공되지 않아요.',
                                style: TextStyle(
                                  fontSize: 13,
                                  color: const Color(0xFF6B7280),
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),

                    // 주변 맛집
                    if (nearbyRestaurants.isNotEmpty) ...[
                      const Divider(height: 32),
                      const Text(
                        '코스 근처 맛집',
                        style: TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      const SizedBox(height: 12),
                      ...nearbyRestaurants.map(
                        (p) => _NearbyPlaceItem(place: p),
                      ),
                      const SizedBox(height: 16),
                    ],

                    // 주변 숙박
                    if (nearbyAccommodations.isNotEmpty) ...[
                      const Divider(height: 32),
                      const Text(
                        '코스 근처 숙박',
                        style: TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      const SizedBox(height: 12),
                      ...nearbyAccommodations.map(
                        (p) => _NearbyPlaceItem(place: p),
                      ),
                      const SizedBox(height: 16),
                    ],

                    const SizedBox(height: 32),
                  ],
                ),
              );
            },
          ),
        ],
      ),
    );
  }
}

class _RoutePoint {
  final int index;
  final String name;
  final String dayLabel;
  final String slotType;
  final double lat;
  final double lng;

  const _RoutePoint({
    required this.index,
    required this.name,
    required this.dayLabel,
    required this.slotType,
    required this.lat,
    required this.lng,
  });

  static List<_RoutePoint> fromPlaces(List<Map<String, dynamic>> places) {
    final points = <_RoutePoint>[];
    for (var i = 0; i < places.length; i++) {
      final place = places[i];
      final lat = _asDouble(place['mapy']);
      final lng = _asDouble(place['mapx']);
      if (lat == null || lng == null) continue;
      points.add(
        _RoutePoint(
          index: i + 1,
          name: place['subname'] as String? ?? '',
          dayLabel: place['dayLabel'] as String? ?? 'DAY 1',
          slotType: place['slotType'] as String? ?? 'sight',
          lat: lat,
          lng: lng,
        ),
      );
    }
    return points;
  }

  static double? _asDouble(Object? value) {
    if (value is num) return value.toDouble();
    if (value is String) return double.tryParse(value);
    return null;
  }
}

class _RoutePath {
  final String dayLabel;
  final List<latlong.LatLng> points;

  const _RoutePath({required this.dayLabel, required this.points});

  static List<_RoutePath> fromRaw(List<dynamic> rawPaths) {
    return rawPaths
        .map((rawPath) {
          if (rawPath is! Map) return null;
          final rawPoints = rawPath['points'] as List? ?? const [];
          final points = rawPoints
              .map((rawPoint) {
                if (rawPoint is! Map) return null;
                final lat = _RoutePoint._asDouble(rawPoint['lat']);
                final lng = _RoutePoint._asDouble(rawPoint['lng']);
                if (lat == null || lng == null) return null;
                return latlong.LatLng(lat, lng);
              })
              .whereType<latlong.LatLng>()
              .toList();
          if (points.length < 2) return null;
          return _RoutePath(
            dayLabel: rawPath['dayLabel'] as String? ?? 'DAY 1',
            points: points,
          );
        })
        .whereType<_RoutePath>()
        .toList();
  }
}

class _CourseMapBackdrop extends StatelessWidget {
  final List<_RoutePoint> points;
  final List<_RoutePath> routePaths;
  final String imageUrl;
  final bool hasImage;

  const _CourseMapBackdrop({
    required this.points,
    required this.routePaths,
    required this.imageUrl,
    required this.hasImage,
  });

  @override
  Widget build(BuildContext context) {
    if (points.isNotEmpty) {
      return _RouteMap(points: points, routePaths: routePaths);
    }
    if (hasImage) {
      return Image.network(
        imageUrl,
        fit: BoxFit.cover,
        errorBuilder: (context, error, stack) =>
            Container(color: Theme.of(context).colorScheme.primaryContainer),
      );
    }
    return Container(color: Theme.of(context).colorScheme.primaryContainer);
  }
}

class _RouteMap extends StatefulWidget {
  final List<_RoutePoint> points;
  final List<_RoutePath> routePaths;

  const _RouteMap({required this.points, required this.routePaths});

  @override
  State<_RouteMap> createState() => _RouteMapState();
}

class _RouteMapState extends State<_RouteMap> {
  late final MapController _mapController;
  late final latlong.LatLng _initialCenter;
  late final double _initialZoom;

  @override
  void initState() {
    super.initState();
    _mapController = MapController();
    final center = _center(widget.points);
    _initialCenter = latlong.LatLng(center.$1, center.$2);
    _initialZoom = _initialMapZoom(widget.points);
  }

  @override
  void didUpdateWidget(covariant _RouteMap oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.points != widget.points) {
      final center = _center(widget.points);
      final nextCenter = latlong.LatLng(center.$1, center.$2);
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) return;
        _mapController.move(nextCenter, _initialMapZoom(widget.points));
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final labels = widget.points.map((p) => p.dayLabel).toSet().toList();
    return Stack(
      fit: StackFit.expand,
      children: [
        FlutterMap(
          mapController: _mapController,
          options: MapOptions(
            initialCenter: _initialCenter,
            initialZoom: _initialZoom,
            minZoom: 7,
            maxZoom: 18,
          ),
          children: [
            TileLayer(
              urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
              userAgentPackageName: 'com.gabojago.tripmate',
            ),
            PolylineLayer(
              polylines: widget.routePaths.isNotEmpty
                  ? widget.routePaths.map((routePath) {
                      final dayIndex = labels.indexOf(routePath.dayLabel);
                      return Polyline(
                        points: routePath.points,
                        color: _RoutePainter.dayColor(
                          dayIndex < 0 ? 0 : dayIndex,
                        ),
                        strokeWidth: 4,
                        borderColor: Colors.white,
                        borderStrokeWidth: 2,
                      );
                    }).toList()
                  : labels.map((label) {
                      final dayPoints = widget.points
                          .where((p) => p.dayLabel == label)
                          .toList();
                      return Polyline(
                        points: dayPoints
                            .map((p) => latlong.LatLng(p.lat, p.lng))
                            .toList(),
                        color: _RoutePainter.dayColor(labels.indexOf(label)),
                        strokeWidth: 4,
                        borderColor: Colors.white,
                        borderStrokeWidth: 2,
                      );
                    }).toList(),
            ),
            MarkerLayer(
              markers: widget.points.map((point) {
                final color = _RoutePainter.dayColor(
                  labels.indexOf(point.dayLabel),
                );
                return Marker(
                  point: latlong.LatLng(point.lat, point.lng),
                  width: 44,
                  height: 44,
                  child: _RouteMarker(point: point, color: color),
                );
              }).toList(),
            ),
          ],
        ),
        IgnorePointer(
          child: DecoratedBox(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: [
                  Colors.black.withValues(alpha: 0.18),
                  Colors.transparent,
                  Colors.black.withValues(alpha: 0.08),
                ],
                stops: const [0, 0.22, 1],
              ),
            ),
          ),
        ),
        Positioned(
          right: 14,
          top: 116,
          child: _MapZoomControls(
            onZoomIn: () => _zoomBy(1),
            onZoomOut: () => _zoomBy(-1),
          ),
        ),
      ],
    );
  }

  void _zoomBy(double delta) {
    final camera = _mapController.camera;
    final nextZoom = (camera.zoom + delta).clamp(7.0, 18.0);
    _mapController.move(camera.center, nextZoom);
  }

  (double, double) _center(List<_RoutePoint> points) {
    final lat =
        points.map((p) => p.lat).reduce((a, b) => a + b) / points.length;
    final lng =
        points.map((p) => p.lng).reduce((a, b) => a + b) / points.length;
    return (lat, lng);
  }

  double _initialMapZoom(List<_RoutePoint> points) {
    if (points.length <= 1) return 15;
    final minLat = points.map((p) => p.lat).reduce(math.min);
    final maxLat = points.map((p) => p.lat).reduce(math.max);
    final minLng = points.map((p) => p.lng).reduce(math.min);
    final maxLng = points.map((p) => p.lng).reduce(math.max);
    final span = math.max(maxLat - minLat, maxLng - minLng);
    if (span < 0.01) return 15.5;
    if (span < 0.03) return 14;
    if (span < 0.08) return 12.5;
    if (span < 0.16) return 11;
    return 9.5;
  }
}

class _MapZoomControls extends StatelessWidget {
  final VoidCallback onZoomIn;
  final VoidCallback onZoomOut;

  const _MapZoomControls({required this.onZoomIn, required this.onZoomOut});

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(10),
        boxShadow: const [
          BoxShadow(
            color: Color(0x26000000),
            blurRadius: 12,
            offset: Offset(0, 3),
          ),
        ],
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          _ZoomButton(icon: Icons.add, onPressed: onZoomIn, tooltip: '확대'),
          Container(width: 36, height: 1, color: const Color(0xFFE5E7EB)),
          _ZoomButton(icon: Icons.remove, onPressed: onZoomOut, tooltip: '축소'),
        ],
      ),
    );
  }
}

class _ZoomButton extends StatelessWidget {
  final IconData icon;
  final VoidCallback onPressed;
  final String tooltip;

  const _ZoomButton({
    required this.icon,
    required this.onPressed,
    required this.tooltip,
  });

  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: tooltip,
      child: InkWell(
        onTap: onPressed,
        borderRadius: BorderRadius.circular(10),
        child: SizedBox(
          width: 40,
          height: 40,
          child: Icon(icon, size: 22, color: const Color(0xFF374151)),
        ),
      ),
    );
  }
}

class _RouteMarker extends StatelessWidget {
  final _RoutePoint point;
  final Color color;

  const _RouteMarker({required this.point, required this.color});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Container(
        width: 30,
        height: 30,
        decoration: BoxDecoration(
          color: color,
          shape: BoxShape.circle,
          border: Border.all(color: Colors.white, width: 3),
          boxShadow: const [
            BoxShadow(
              color: Color(0x33000000),
              blurRadius: 8,
              offset: Offset(0, 2),
            ),
          ],
        ),
        alignment: Alignment.center,
        child: Text(
          point.index.toString(),
          style: const TextStyle(
            color: Colors.white,
            fontSize: 11,
            fontWeight: FontWeight.w800,
          ),
        ),
      ),
    );
  }
}

class _RouteLegend extends StatelessWidget {
  final List<_RoutePoint> points;

  const _RouteLegend({required this.points});

  @override
  Widget build(BuildContext context) {
    final labels = points.map((p) => p.dayLabel).toSet().toList();
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: Row(
        children: [
          ...labels.map((label) {
            final idx = labels.indexOf(label);
            return Container(
              margin: const EdgeInsets.only(right: 8),
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
              decoration: BoxDecoration(
                color: Colors.white.withValues(alpha: 0.92),
                borderRadius: BorderRadius.circular(8),
                boxShadow: const [
                  BoxShadow(
                    color: Color(0x22000000),
                    blurRadius: 8,
                    offset: Offset(0, 2),
                  ),
                ],
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Container(
                    width: 10,
                    height: 10,
                    decoration: BoxDecoration(
                      color: _RoutePainter.dayColor(idx),
                      shape: BoxShape.circle,
                    ),
                  ),
                  const SizedBox(width: 6),
                  Text(
                    label,
                    style: const TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w700,
                      color: Color(0xFF374151),
                    ),
                  ),
                ],
              ),
            );
          }),
        ],
      ),
    );
  }
}

class _RoutePainter extends CustomPainter {
  final List<_RoutePoint> points;
  final double centerLat;
  final double centerLng;
  final double zoomLevel;

  _RoutePainter({
    required this.points,
    required this.centerLat,
    required this.centerLng,
    required this.zoomLevel,
  });

  static const _colors = [
    Color(0xFF1B8C6E),
    Color(0xFFE65100),
    Color(0xFF6A1B9A),
    Color(0xFF1565C0),
  ];

  static Color dayColor(int index) => _colors[index % _colors.length];

  @override
  void paint(Canvas canvas, Size size) {
    if (points.isEmpty) return;

    final projected = {
      for (final point in points) point: _project(point, size),
    };
    final labels = points.map((p) => p.dayLabel).toSet().toList();

    for (final label in labels) {
      final dayPoints = points.where((p) => p.dayLabel == label).toList();
      if (dayPoints.length < 2) continue;
      final color = dayColor(labels.indexOf(label));
      final path = Path()
        ..moveTo(
          projected[dayPoints.first]!.dx,
          projected[dayPoints.first]!.dy,
        );
      for (final point in dayPoints.skip(1)) {
        final offset = projected[point]!;
        path.lineTo(offset.dx, offset.dy);
      }
      canvas.drawPath(
        path,
        Paint()
          ..color = Colors.white.withValues(alpha: 0.9)
          ..strokeWidth = 8
          ..style = PaintingStyle.stroke
          ..strokeCap = StrokeCap.round
          ..strokeJoin = StrokeJoin.round,
      );
      canvas.drawPath(
        path,
        Paint()
          ..color = color
          ..strokeWidth = 4
          ..style = PaintingStyle.stroke
          ..strokeCap = StrokeCap.round
          ..strokeJoin = StrokeJoin.round,
      );
    }

    for (final point in points) {
      final offset = projected[point]!;
      final color = dayColor(labels.indexOf(point.dayLabel));
      final radius = point.slotType == 'lodging' ? 13.0 : 12.0;
      canvas.drawCircle(
        offset.translate(0, 2),
        radius + 3,
        Paint()..color = Colors.black.withValues(alpha: 0.2),
      );
      canvas.drawCircle(offset, radius + 3, Paint()..color = Colors.white);
      canvas.drawCircle(offset, radius, Paint()..color = color);
      _drawNumber(canvas, offset, point.index.toString());
    }
  }

  Offset _project(_RoutePoint point, Size size) {
    final center = _worldPoint(centerLat, centerLng);
    final target = _worldPoint(point.lat, point.lng);
    final scale = math.pow(2, _webZoomFromKakaoLevel(zoomLevel)).toDouble();
    final dx = (target.dx - center.dx) * scale;
    final dy = (target.dy - center.dy) * scale;
    return Offset(size.width / 2 + dx, size.height / 2 + dy);
  }

  Offset _worldPoint(double lat, double lng) {
    final sinLat = math.sin(lat * math.pi / 180).clamp(-0.9999, 0.9999);
    final x = 256 * (lng + 180) / 360;
    final y =
        256 * (0.5 - math.log((1 + sinLat) / (1 - sinLat)) / (4 * math.pi));
    return Offset(x, y);
  }

  double _webZoomFromKakaoLevel(double level) {
    return level.clamp(4, 18).toDouble();
  }

  void _drawNumber(Canvas canvas, Offset offset, String text) {
    final painter = TextPainter(
      text: TextSpan(
        text: text,
        style: const TextStyle(
          color: Colors.white,
          fontSize: 11,
          fontWeight: FontWeight.w800,
        ),
      ),
      textDirection: TextDirection.ltr,
    )..layout();
    painter.paint(
      canvas,
      offset - Offset(painter.width / 2, painter.height / 2),
    );
  }

  @override
  bool shouldRepaint(covariant _RoutePainter oldDelegate) =>
      oldDelegate.points != points ||
      oldDelegate.centerLat != centerLat ||
      oldDelegate.centerLng != centerLng ||
      oldDelegate.zoomLevel != zoomLevel;
}

// ─── 이동 시간 divider ────────────────────────────────────
class _TravelTimeDivider extends StatelessWidget {
  final int? minutes;
  const _TravelTimeDivider({this.minutes});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(left: 10),
      child: Row(
        children: [
          Container(
            width: 2,
            height: 24,
            color: const Color(0xFF2E7D6B).withValues(alpha: 0.15),
          ),
          const SizedBox(width: 14),
          const Icon(
            Icons.directions_car_outlined,
            size: 14,
            color: Colors.grey,
          ),
          const SizedBox(width: 4),
          Text(
            minutes != null ? '자동차 약 $minutes분' : '이동',
            style: const TextStyle(fontSize: 12, color: Colors.grey),
          ),
        ],
      ),
    );
  }
}

// ─── DAY 구분 헤더 ───────────────────────────────────────
class _DayDivider extends StatelessWidget {
  final String label;
  final bool isFirst;
  const _DayDivider({required this.label, this.isFirst = false});

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return Padding(
      padding: EdgeInsets.only(top: isFirst ? 0 : 20, bottom: 12),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
            decoration: BoxDecoration(
              color: colorScheme.primary,
              borderRadius: BorderRadius.circular(20),
            ),
            child: Text(
              label,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 13,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Container(
              height: 1,
              color: colorScheme.primary.withValues(alpha: 0.2),
            ),
          ),
        ],
      ),
    );
  }
}

// ─── 장소 상세 아이템 ─────────────────────────────────────
class _PlaceDetailItem extends StatelessWidget {
  final int index;
  final String name;
  final String overview;
  final String imageUrl;
  final String address;
  final String tel;
  final String usetime;
  final String usefee;
  final bool isLast;
  final String? slotType;
  final String? timeLabel;
  final VoidCallback? onReplace;

  const _PlaceDetailItem({
    required this.index,
    required this.name,
    required this.overview,
    required this.imageUrl,
    required this.address,
    required this.tel,
    required this.usetime,
    required this.usefee,
    required this.isLast,
    this.slotType,
    this.timeLabel,
    this.onReplace,
  });

  IconData _slotIcon() {
    return switch (slotType) {
      'meal' => Icons.restaurant_outlined,
      'cafe' => Icons.local_cafe_outlined,
      'lodging' => Icons.hotel_outlined,
      _ => Icons.place_outlined,
    };
  }

  Color _slotColor(ColorScheme cs) {
    return switch (slotType) {
      'meal' => const Color(0xFFE65100),
      'cafe' => const Color(0xFF8D6E63),
      'lodging' => const Color(0xFF6A1B9A),
      _ => cs.primary,
    };
  }

  String _timeLabelKo() {
    return switch (timeLabel) {
      'morning' => '오전',
      'lunch' => '점심',
      'afternoon' => '오후',
      'dinner' => '저녁',
      'evening' => '숙박',
      _ => '',
    };
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final hasImg = imageUrl.isNotEmpty && !imageUrl.contains('placeholder');
    final slotColor = _slotColor(colorScheme);

    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // 번호 원 + 슬롯 아이콘
        Column(
          children: [
            Container(
              width: 26,
              height: 26,
              margin: const EdgeInsets.only(top: 2),
              decoration: BoxDecoration(
                color: slotColor,
                shape: BoxShape.circle,
              ),
              alignment: Alignment.center,
              child: Icon(_slotIcon(), color: Colors.white, size: 14),
            ),
          ],
        ),
        const SizedBox(width: 12),

        // 장소 내용
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // 시간대 라벨 + 장소명
              Row(
                crossAxisAlignment: CrossAxisAlignment.center,
                children: [
                  if (timeLabel != null && _timeLabelKo().isNotEmpty) ...[
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 6,
                        vertical: 1,
                      ),
                      decoration: BoxDecoration(
                        color: slotColor.withValues(alpha: 0.1),
                        borderRadius: BorderRadius.circular(4),
                      ),
                      child: Text(
                        _timeLabelKo(),
                        style: TextStyle(
                          fontSize: 10,
                          fontWeight: FontWeight.w600,
                          color: slotColor,
                        ),
                      ),
                    ),
                    const SizedBox(width: 6),
                  ],
                  Expanded(
                    child: Text(
                      name,
                      style: const TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.w700,
                        letterSpacing: -0.2,
                      ),
                    ),
                  ),
                ],
              ),

              // 주소
              if (address.isNotEmpty) ...[
                const SizedBox(height: 3),
                Row(
                  children: [
                    Icon(
                      Icons.place_outlined,
                      size: 13,
                      color: const Color(0xFF9CA3AF),
                    ),
                    const SizedBox(width: 3),
                    Expanded(
                      child: Text(
                        address,
                        style: TextStyle(
                          fontSize: 12,
                          color: const Color(0xFF6B7280),
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ],
                ),
              ],
              if (onReplace != null) ...[
                const SizedBox(height: 8),
                OutlinedButton.icon(
                  onPressed: onReplace,
                  icon: const Icon(Icons.refresh, size: 16),
                  label: const Text('다른 장소 보기'),
                  style: OutlinedButton.styleFrom(
                    visualDensity: VisualDensity.compact,
                    minimumSize: const Size(0, 32),
                  ),
                ),
              ],

              // 이용시간 / 요금
              if (usetime.isNotEmpty || usefee.isNotEmpty) ...[
                const SizedBox(height: 4),
                Wrap(
                  spacing: 8,
                  children: [
                    if (usetime.isNotEmpty)
                      _SmallTag(
                        icon: Icons.access_time_outlined,
                        text: usetime,
                      ),
                    if (usefee.isNotEmpty)
                      _SmallTag(icon: Icons.payments_outlined, text: usefee),
                  ],
                ),
              ],

              // 설명
              if (overview.isNotEmpty) ...[
                const SizedBox(height: 6),
                Text(
                  overview,
                  style: const TextStyle(
                    fontSize: 13,
                    color: Color(0xFF6B7280),
                    height: 1.6,
                  ),
                  maxLines: 3,
                  overflow: TextOverflow.ellipsis,
                ),
              ],

              // 이미지
              if (hasImg) ...[
                const SizedBox(height: 8),
                ClipRRect(
                  borderRadius: BorderRadius.circular(8),
                  child: Image.network(
                    imageUrl,
                    height: 120,
                    width: double.infinity,
                    fit: BoxFit.cover,
                    errorBuilder: (context, error, stack) =>
                        const SizedBox.shrink(),
                  ),
                ),
              ],

              const SizedBox(height: 8),
            ],
          ),
        ),
      ],
    );
  }
}

// ─── 주변 장소 아이템 ─────────────────────────────────────
class _NearbyPlaceItem extends StatelessWidget {
  final Map<String, dynamic> place;
  const _NearbyPlaceItem({required this.place});

  @override
  Widget build(BuildContext context) {
    final name = place['name'] as String? ?? '';
    final category = place['categoryName'] as String? ?? '';
    final address = place['address'] as String? ?? '';
    final distance = place['distance'] as String? ?? '';

    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [
          const BoxShadow(
            color: Color(0x05000000),
            blurRadius: 0,
            spreadRadius: 1,
          ),
          const BoxShadow(
            color: Color(0x08000000),
            blurRadius: 6,
            offset: Offset(0, 2),
          ),
        ],
      ),
      child: Row(
        children: [
          const Icon(
            Icons.restaurant_outlined,
            size: 18,
            color: Color(0xFF2E7D6B),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  name,
                  style: const TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                if (category.isNotEmpty || address.isNotEmpty)
                  Text(
                    [
                      if (category.isNotEmpty) category,
                      if (address.isNotEmpty) address,
                    ].join(' · '),
                    style: TextStyle(
                      fontSize: 12,
                      color: const Color(0xFF6B7280),
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
              ],
            ),
          ),
          if (distance.isNotEmpty) ...[
            const SizedBox(width: 8),
            Text(
              '${distance}m',
              style: TextStyle(fontSize: 12, color: const Color(0xFF9CA3AF)),
            ),
          ],
        ],
      ),
    );
  }
}

// ─── 공통 위젯 ────────────────────────────────────────────
class _InfoPill extends StatelessWidget {
  final IconData icon;
  final String label;
  const _InfoPill({required this.icon, required this.label});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(
        color: Colors.grey.shade100,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 14, color: const Color(0xFF6B7280)),
          const SizedBox(width: 4),
          Text(
            label,
            style: TextStyle(
              fontSize: 12,
              color: Colors.grey.shade700,
              fontWeight: FontWeight.w500,
            ),
          ),
        ],
      ),
    );
  }
}

class _SmallTag extends StatelessWidget {
  final IconData icon;
  final String text;
  const _SmallTag({required this.icon, required this.text});

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 12, color: const Color(0xFF9CA3AF)),
        const SizedBox(width: 3),
        Text(
          text,
          style: TextStyle(fontSize: 11, color: const Color(0xFF6B7280)),
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        ),
      ],
    );
  }
}
