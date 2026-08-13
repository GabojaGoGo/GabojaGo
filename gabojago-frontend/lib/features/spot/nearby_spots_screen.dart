import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart' show PlatformException;
import 'package:geolocator/geolocator.dart';
import 'package:kakao_maps_flutter/kakao_maps_flutter.dart';

import 'package:tripmate/infrastructure/api_service.dart';
import 'package:tripmate/core/widgets/spot_card.dart';
import 'package:tripmate/features/spot/widgets/congestion_banner.dart';
import 'package:tripmate/features/spot/widgets/spot_card_carousel.dart';
import 'package:tripmate/features/spot/widgets/spot_list_tile.dart';
import 'package:tripmate/features/spot/widgets/spot_marker_builder.dart';

class NearbySpotsScreen extends StatefulWidget {
  final List<SpotData> spots;
  final double currentLat;
  final double currentLng;
  final String locationName;

  /// 홈화면에서 특정 관광지를 탭해서 진입할 때 설정 — 해당 마커로 자동 이동
  final int? initialSpotId;

  const NearbySpotsScreen({
    super.key,
    this.spots = const [],
    this.currentLat = 0.0,
    this.currentLng = 0.0,
    this.locationName = '',
    this.initialSpotId,
  });

  @override
  State<NearbySpotsScreen> createState() => _NearbySpotsScreenState();
}

class _NearbySpotsScreenState extends State<NearbySpotsScreen> {
  static const String _markerLayerId = 'tripmate_marker_layer';
  static const String _spotMarkerStyleId = 'tripmate_spot_marker';
  static const String _spotHighlightedMarkerStyleId =
      'tripmate_spot_marker_highlighted';
  static const String _currentMarkerStyleId =
      'tripmate_current_location_marker';
  static const String _currentMarkerId = 'tripmate_current_location';

  static const double _kCardPanelH = 156.0;
  static const double _kBannerH = 56.0;
  static const double _kSearchBtnThreshold = 300.0;

  KakaoMapController? _mapController;
  late final PageController _cardController;

  bool _isMarkersReady = false;
  bool _isRefreshing = false;
  bool _programmaticMove = false;
  bool _showSearchHereBtn = false;
  int _currentCardIndex = 0;
  int? _programmaticTargetPage;

  late double _lat;
  late double _lng;
  late String _locName;
  bool _isInitializing = false;

  StreamSubscription<CameraMoveEndEvent>? _cameraSub;
  StreamSubscription<LabelClickEvent>? _labelSub;

  LatLng? _cameraCenterLatLng;
  LatLng? _lastFetchCenter;

  List<SpotData> _sortedSpots = [];

  @override
  void initState() {
    super.initState();
    _lat = widget.currentLat;
    _lng = widget.currentLng;
    _locName = widget.locationName.isEmpty ? '위치 확인 중...' : widget.locationName;
    _cardController = PageController(viewportFraction: 0.88);

    if (widget.currentLat == 0.0) {
      _isInitializing = true;
      WidgetsBinding.instance.addPostFrameCallback((_) => _initFromGps());
    } else {
      _sortedSpots = [...widget.spots]
        ..sort((a, b) {
          final dA = Geolocator.distanceBetween(
            _lat,
            _lng,
            a.latitude,
            a.longitude,
          );
          final dB = Geolocator.distanceBetween(
            _lat,
            _lng,
            b.latitude,
            b.longitude,
          );
          return dA.compareTo(dB);
        });
      _lastFetchCenter = LatLng(latitude: _lat, longitude: _lng);
      WidgetsBinding.instance.addPostFrameCallback(
        (_) => _fetchSpotsFull(_lat, _lng),
      );
    }
  }

  @override
  void dispose() {
    _cameraSub?.cancel();
    _labelSub?.cancel();
    _cardController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final spots = _sortedSpots;
    final lowCount = spots
        .where((s) => s.congestion == '낮음' || s.congestion == '예측중')
        .length;
    final highCount = spots.where((s) => s.congestion == '높음').length;
    final isRelaxed = spots.isEmpty || lowCount >= highCount;

    if (_isInitializing) {
      return Scaffold(
        appBar: AppBar(title: const Text('내 주변 관광지')),
        body: const Center(
          child: CircularProgressIndicator(color: Color(0xFF1B8C6E)),
        ),
      );
    }

    return Scaffold(
      appBar: AppBar(title: Text('$_locName 주변')),
      body: Stack(
        children: [
          KakaoMap(
            initialPosition: LatLng(latitude: _lat, longitude: _lng),
            initialLevel: 14,
            onMapCreated: _handleMapCreated,
          ),
          Positioned(
            top: 12,
            left: 16,
            right: 16,
            child: CongestionBanner(isRelaxed: isRelaxed),
          ),
          if (!_isMarkersReady)
            Positioned.fill(
              child: IgnorePointer(
                child: Container(
                  color: Colors.white.withValues(alpha: 0.45),
                  child: const Center(
                    child: CircularProgressIndicator(
                      color: Color(0xFF1B8C6E),
                      strokeWidth: 3,
                    ),
                  ),
                ),
              ),
            ),
          AnimatedPositioned(
            duration: const Duration(milliseconds: 220),
            curve: Curves.easeOut,
            top: _showSearchHereBtn ? _kBannerH + 10 : _kBannerH - 50,
            left: 0,
            right: 0,
            child: AnimatedOpacity(
              opacity: _showSearchHereBtn ? 1.0 : 0.0,
              duration: const Duration(milliseconds: 200),
              child: IgnorePointer(
                ignoring: !_showSearchHereBtn,
                child: Center(
                  child: GestureDetector(
                    onTap: _onSearchHere,
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 18,
                        vertical: 10,
                      ),
                      decoration: BoxDecoration(
                        color: const Color(0xFF1B8C6E),
                        borderRadius: BorderRadius.circular(22),
                        boxShadow: [
                          BoxShadow(
                            color: const Color(
                              0xFF1B8C6E,
                            ).withValues(alpha: 0.35),
                            blurRadius: 12,
                            offset: const Offset(0, 4),
                          ),
                        ],
                      ),
                      child: const Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(
                            Icons.search_rounded,
                            size: 16,
                            color: Colors.white,
                          ),
                          SizedBox(width: 6),
                          Text(
                            '이 지역 관광지 검색',
                            style: TextStyle(
                              fontSize: 13,
                              fontWeight: FontWeight.w700,
                              color: Colors.white,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
          if (_isRefreshing)
            Positioned(
              top: _kBannerH + 12,
              left: 0,
              right: 0,
              child: Center(
                child: Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 14,
                    vertical: 8,
                  ),
                  decoration: BoxDecoration(
                    color: Colors.white.withValues(alpha: 0.92),
                    borderRadius: BorderRadius.circular(20),
                    boxShadow: [
                      BoxShadow(
                        color: Colors.black.withValues(alpha: 0.1),
                        blurRadius: 8,
                      ),
                    ],
                  ),
                  child: const Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      SizedBox(
                        width: 14,
                        height: 14,
                        child: CircularProgressIndicator(
                          color: Color(0xFF1B8C6E),
                          strokeWidth: 2,
                        ),
                      ),
                      SizedBox(width: 8),
                      Text(
                        '주변 관광지 불러오는 중...',
                        style: TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.w600,
                          color: Color(0xFF1A1A1A),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          Positioned(
            right: 16,
            bottom: _kCardPanelH + 44,
            child: Material(
              color: Colors.white,
              elevation: 5,
              borderRadius: BorderRadius.circular(16),
              child: InkWell(
                borderRadius: BorderRadius.circular(16),
                onTap: _moveCameraToCurrentLocation,
                child: const Padding(
                  padding: EdgeInsets.all(13),
                  child: Icon(
                    Icons.my_location_rounded,
                    color: Color(0xFF1565C0),
                    size: 22,
                  ),
                ),
              ),
            ),
          ),
          Positioned(
            left: 16,
            bottom: _kCardPanelH + 44,
            child: GestureDetector(
              onTap: _showListSheet,
              child: Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 16,
                  vertical: 10,
                ),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(22),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withValues(alpha: 0.13),
                      blurRadius: 8,
                      offset: const Offset(0, 2),
                    ),
                  ],
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(
                      Icons.format_list_bulleted_rounded,
                      size: 16,
                      color: Color(0xFF1A1A1A),
                    ),
                    const SizedBox(width: 6),
                    Text(
                      '목록 ${_sortedSpots.length}',
                      style: const TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w700,
                        color: Color(0xFF1A1A1A),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
          Positioned(
            left: 0,
            right: 0,
            bottom: 30,
            height: _kCardPanelH,
            child: _sortedSpots.isEmpty
                ? _buildEmptyCard()
                : SpotCardCarousel(
                    spots: _sortedSpots,
                    currentIndex: _currentCardIndex,
                    lat: _lat,
                    lng: _lng,
                    pageController: _cardController,
                    onPageChanged: _onCarouselPageChanged,
                  ),
          ),
        ],
      ),
    );
  }

  Widget _buildEmptyCard() {
    return Center(
      child: Container(
        margin: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(16),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.08),
              blurRadius: 8,
            ),
          ],
        ),
        child: const Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.search_off_rounded, color: Color(0xFF607D8B), size: 20),
            SizedBox(width: 10),
            Text(
              '이 지역 주변에 관광지 정보가 없어요',
              style: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w600,
                color: Color(0xFF607D8B),
              ),
            ),
          ],
        ),
      ),
    );
  }

  void _onCarouselPageChanged(int idx) {
    if (_programmaticTargetPage != null) {
      if (idx == _programmaticTargetPage) _programmaticTargetPage = null;
      return;
    }
    final prevIdx = _currentCardIndex;
    setState(() => _currentCardIndex = idx);
    _updateHighlightedMarker(prevIdx, idx);
    _moveCameraToSpot(_sortedSpots[idx]);
  }

  // ── 목록 바텀시트 ─────────────────────────────────────────────

  void _showListSheet() {
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => DraggableScrollableSheet(
        initialChildSize: 0.65,
        minChildSize: 0.4,
        maxChildSize: 0.95,
        builder: (ctx, scrollController) => Container(
          decoration: const BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
          ),
          child: Column(
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 12, 20, 8),
                child: Column(
                  children: [
                    Container(
                      width: 36,
                      height: 4,
                      decoration: BoxDecoration(
                        color: Colors.grey.shade300,
                        borderRadius: BorderRadius.circular(2),
                      ),
                    ),
                    const SizedBox(height: 12),
                    Row(
                      children: [
                        Text(
                          '주변 관광지 ${_sortedSpots.length}곳',
                          style: const TextStyle(
                            fontSize: 17,
                            fontWeight: FontWeight.w800,
                            color: Color(0xFF1A1A1A),
                          ),
                        ),
                        const Spacer(),
                        TextButton.icon(
                          onPressed: () => Navigator.pop(ctx),
                          icon: const Icon(Icons.close, size: 18),
                          label: const Text('닫기'),
                          style: TextButton.styleFrom(
                            foregroundColor: Colors.grey.shade600,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
              const Divider(height: 1),
              Expanded(
                child: ListView.builder(
                  controller: scrollController,
                  itemCount: _sortedSpots.length,
                  itemBuilder: (ctx, i) {
                    final spot = _sortedSpots[i];
                    return SpotListTile(
                      spot: spot,
                      isSelected: i == _currentCardIndex,
                      lat: _lat,
                      lng: _lng,
                      onTap: () {
                        Navigator.pop(ctx);
                        _onSpotTapped(spot);
                      },
                    );
                  },
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  // ── GPS 초기화 ────────────────────────────────────────────────

  Future<void> _initFromGps() async {
    try {
      var permission = await Geolocator.checkPermission();
      if (permission == LocationPermission.denied) {
        permission = await Geolocator.requestPermission();
      }
      if (permission == LocationPermission.denied ||
          permission == LocationPermission.deniedForever) {
        if (mounted) setState(() => _isInitializing = false);
        return;
      }
      final position = await ApiService.getCurrentLocation();
      final address = await ApiService.getAddressFromLatLng(
        position.latitude,
        position.longitude,
      );
      if (!mounted) return;
      setState(() {
        _lat = position.latitude;
        _lng = position.longitude;
        _locName = address;
        _lastFetchCenter = LatLng(latitude: _lat, longitude: _lng);
        _isInitializing = false;
      });
      await _fetchSpotsFull(_lat, _lng);
    } catch (_) {
      if (mounted) setState(() => _isInitializing = false);
    }
  }

  // ── 데이터 fetch ──────────────────────────────────────────────

  Future<void> _fetchSpotsFull(double lat, double lng) async {
    try {
      final results = await Future.wait([
        ApiService.getNearbySpots(lat, lng, limit: 300),
        ApiService.getNearbySpotCongestions(lat, lng, limit: 300),
      ]);
      if (!mounted) return;

      final spots = _mergeSpotCongestion(results[0], results[1], lat, lng);
      setState(() => _sortedSpots = spots);
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _updateMapMarkers();
      });
    } catch (_) {}
  }

  Future<void> _fetchNewSpots(double lat, double lng) async {
    if (!mounted) return;
    setState(() {
      _isRefreshing = true;
      _showSearchHereBtn = false;
    });
    try {
      final results = await Future.wait([
        ApiService.getNearbySpots(lat, lng, limit: 300),
        ApiService.getNearbySpotCongestions(lat, lng, limit: 300),
      ]);
      if (!mounted) return;

      final newSpots = _mergeSpotCongestion(results[0], results[1], lat, lng);
      _lastFetchCenter = LatLng(latitude: lat, longitude: lng);

      setState(() {
        _sortedSpots = newSpots;
        _currentCardIndex = 0;
        _isRefreshing = false;
      });

      if (_cardController.hasClients && newSpots.isNotEmpty) {
        unawaited(
          _cardController.animateToPage(
            0,
            duration: const Duration(milliseconds: 300),
            curve: Curves.easeOut,
          ),
        );
      }
      await _updateMapMarkers();
    } catch (e) {
      debugPrint('[NearbySpotsScreen] fetchNewSpots error: $e');
      if (mounted) setState(() => _isRefreshing = false);
    }
  }

  List<SpotData> _mergeSpotCongestion(
    List<dynamic> spotsRaw,
    List<dynamic> congestionRaw,
    double lat,
    double lng,
  ) {
    final congestionById = <int, Map<String, dynamic>>{
      for (final item in congestionRaw)
        ((item as Map<String, dynamic>)['id'] as num?)?.toInt() ?? -1: item,
    };
    return spotsRaw
        .map((json) => SpotData.fromJson(json as Map<String, dynamic>))
        .map((spot) {
          final c = congestionById[spot.id];
          if (c == null) return spot;
          return spot.copyWith(
            congestion: (c['congestion'] as String?)?.trim(),
            congestionSource: c['congestionSource'] as String?,
            congestionBaseYmd: c['congestionBaseYmd'] as String?,
          );
        })
        .toList()
      ..sort((a, b) {
        final dA = Geolocator.distanceBetween(
          lat,
          lng,
          a.latitude,
          a.longitude,
        );
        final dB = Geolocator.distanceBetween(
          lat,
          lng,
          b.latitude,
          b.longitude,
        );
        return dA.compareTo(dB);
      });
  }

  // ── 지도 초기화 ───────────────────────────────────────────────

  Future<void> _handleMapCreated(KakaoMapController controller) async {
    _mapController = controller;
    _cameraSub = controller.onCameraMoveEndStream.listen(_onCameraMoveEnd);
    _labelSub = controller.onLabelClickedStream.listen(_onMarkerTapped);
    await _initializeMarkers(controller);
  }

  Future<void> _initializeMarkers(
    KakaoMapController controller, {
    int attempt = 0,
  }) async {
    try {
      final markerBytes = await SpotMarkerBuilder.buildSpotMarker();
      final highlightedBytes =
          await SpotMarkerBuilder.buildSpotMarkerHighlighted();
      final currentBytes = await SpotMarkerBuilder.buildCurrentLocationMarker();

      await controller.registerMarkerStyles(
        styles: [
          MarkerStyle(
            styleId: _spotMarkerStyleId,
            perLevels: [
              MarkerPerLevelStyle.fromBytes(
                bytes: markerBytes,
                textStyle: const MarkerTextStyle(
                  fontSize: 16,
                  fontColorArgb: 0xFF1A1A1A,
                  strokeThickness: 2,
                  strokeColorArgb: 0xFFFFFFFF,
                ),
                level: 0,
              ),
            ],
          ),
          MarkerStyle(
            styleId: _spotHighlightedMarkerStyleId,
            perLevels: [
              MarkerPerLevelStyle.fromBytes(
                bytes: highlightedBytes,
                textStyle: const MarkerTextStyle(
                  fontSize: 16,
                  fontColorArgb: 0xFFFF6D00,
                  strokeThickness: 2,
                  strokeColorArgb: 0xFFFFFFFF,
                ),
                level: 0,
              ),
            ],
          ),
          MarkerStyle(
            styleId: _currentMarkerStyleId,
            perLevels: [
              MarkerPerLevelStyle.fromBytes(
                bytes: currentBytes,
                textStyle: const MarkerTextStyle(
                  fontSize: 14,
                  fontColorArgb: 0xFF1565C0,
                  strokeThickness: 2,
                  strokeColorArgb: 0xFFFFFFFF,
                ),
                level: 0,
              ),
            ],
          ),
        ],
      );

      await controller.addMarkerLayer(
        layerId: _markerLayerId,
        zOrder: 1000,
        clickable: true,
      );
      await controller.addMarkers(
        layerId: _markerLayerId,
        markerOptions: [
          MarkerOption(
            id: _currentMarkerId,
            latLng: LatLng(latitude: _lat, longitude: _lng),
            styleId: _currentMarkerStyleId,
            rank: 10000,
            text: '내 위치',
          ),
          ..._sortedSpots.asMap().entries.map(
            (entry) => MarkerOption(
              id: entry.value.id.toString(),
              latLng: LatLng(
                latitude: entry.value.latitude,
                longitude: entry.value.longitude,
              ),
              styleId: entry.key == _currentCardIndex
                  ? _spotHighlightedMarkerStyleId
                  : _spotMarkerStyleId,
              rank: entry.key == _currentCardIndex ? 10001 : 9999,
              text: entry.value.spotName,
            ),
          ),
        ],
      );

      if (mounted) setState(() => _isMarkersReady = true);
      if (widget.initialSpotId != null) {
        _jumpToInitialSpot();
      } else {
        await _moveCameraToCurrentLocation();
      }
    } on AssertionError {
      if (attempt >= 15 || !mounted) rethrow;
      await Future<void>.delayed(const Duration(milliseconds: 200));
      await _initializeMarkers(controller, attempt: attempt + 1);
    } on PlatformException catch (e) {
      if (e.code != 'E000' || attempt >= 15 || !mounted) rethrow;
      await Future<void>.delayed(const Duration(milliseconds: 200));
      await _initializeMarkers(controller, attempt: attempt + 1);
    }
  }

  // ── 카메라 이동 ───────────────────────────────────────────────

  /// 배너(상단)·카드(하단)가 지도를 가리므로 마커가 가시 영역 정중앙에 오도록 보정.
  /// offset_deg = pixelOffset × metersPerPixel / 111_139
  double _latOffset() {
    const offsetPx = (_kCardPanelH - _kBannerH) / 2;
    const metersPerPx = 4.0;
    return offsetPx * metersPerPx / 111139.0;
  }

  Future<void> _moveCameraToCurrentLocation() async {
    final controller = _mapController;
    if (controller == null) return;
    _programmaticMove = true;
    await controller.moveCamera(
      cameraUpdate: CameraUpdate(
        position: LatLng(latitude: _lat - _latOffset(), longitude: _lng),
        zoomLevel: 14,
        type: 0,
      ),
      animation: const CameraAnimation(
        duration: 400,
        autoElevation: true,
        isConsecutive: false,
      ),
    );
  }

  Future<void> _moveCameraToSpot(SpotData spot) async {
    final controller = _mapController;
    if (controller == null) return;
    _programmaticMove = true;
    await controller.moveCamera(
      cameraUpdate: CameraUpdate(
        position: LatLng(
          latitude: spot.latitude - _latOffset(),
          longitude: spot.longitude,
        ),
        zoomLevel: 15,
        type: 0,
      ),
      animation: const CameraAnimation(
        duration: 350,
        autoElevation: true,
        isConsecutive: false,
      ),
    );
  }

  void _jumpToInitialSpot() {
    final id = widget.initialSpotId;
    if (id == null) return;
    final idx = _sortedSpots.indexWhere((s) => s.id == id);
    if (idx < 0) return;
    final prevIdx = _currentCardIndex;
    setState(() => _currentCardIndex = idx);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_cardController.hasClients) _cardController.jumpToPage(idx);
    });
    _updateHighlightedMarker(prevIdx, idx);
    _moveCameraToSpot(_sortedSpots[idx]);
  }

  void _onSpotTapped(SpotData spot) {
    final idx = _sortedSpots.indexWhere((s) => s.id == spot.id);
    if (idx >= 0) {
      setState(() => _currentCardIndex = idx);
      _cardController.animateToPage(
        idx,
        duration: const Duration(milliseconds: 300),
        curve: Curves.easeOut,
      );
    }
    _moveCameraToSpot(spot);
  }

  // ── 이벤트 핸들러 ─────────────────────────────────────────────

  void _onCameraMoveEnd(CameraMoveEndEvent event) {
    _cameraCenterLatLng = LatLng(
      latitude: event.latitude,
      longitude: event.longitude,
    );

    if (_programmaticMove) {
      _programmaticMove = false;
      return;
    }

    final prev = _lastFetchCenter;
    if (prev != null) {
      final dist = Geolocator.distanceBetween(
        event.latitude,
        event.longitude,
        prev.latitude,
        prev.longitude,
      );
      final shouldShow = dist >= _kSearchBtnThreshold;
      if (shouldShow != _showSearchHereBtn) {
        setState(() => _showSearchHereBtn = shouldShow);
      }
    }
  }

  void _onMarkerTapped(LabelClickEvent event) {
    if (event.labelId == _currentMarkerId) return;
    final spotId = int.tryParse(event.labelId);
    if (spotId == null) return;
    final idx = _sortedSpots.indexWhere((s) => s.id == spotId);
    if (idx < 0) return;
    final prevIdx = _currentCardIndex;
    _programmaticMove = true;
    _programmaticTargetPage = idx;
    setState(() => _currentCardIndex = idx);
    _updateHighlightedMarker(prevIdx, idx);
    if (_cardController.hasClients) {
      _cardController.animateToPage(
        idx,
        duration: const Duration(milliseconds: 300),
        curve: Curves.easeOut,
      );
    }
    _moveCameraToSpot(_sortedSpots[idx]);
  }

  void _onSearchHere() {
    final center = _cameraCenterLatLng;
    if (center == null) return;
    setState(() => _showSearchHereBtn = false);
    _fetchNewSpots(center.latitude, center.longitude);
  }

  // ── 마커 갱신 ─────────────────────────────────────────────────

  Future<void> _updateMapMarkers() async {
    final controller = _mapController;
    if (controller == null) return;
    try {
      await controller.clearMarkers(layerId: _markerLayerId);
      await controller.addMarkers(
        layerId: _markerLayerId,
        markerOptions: [
          MarkerOption(
            id: _currentMarkerId,
            latLng: LatLng(latitude: _lat, longitude: _lng),
            styleId: _currentMarkerStyleId,
            rank: 10000,
            text: '내 위치',
          ),
          ..._sortedSpots.asMap().entries.map(
            (entry) => MarkerOption(
              id: entry.value.id.toString(),
              latLng: LatLng(
                latitude: entry.value.latitude,
                longitude: entry.value.longitude,
              ),
              styleId: entry.key == _currentCardIndex
                  ? _spotHighlightedMarkerStyleId
                  : _spotMarkerStyleId,
              rank: entry.key == _currentCardIndex ? 10001 : 9999,
              text: entry.value.spotName,
            ),
          ),
        ],
      );
    } on PlatformException catch (e) {
      debugPrint('[NearbySpotsScreen] updateMapMarkers error: $e');
    }
  }

  Future<void> _updateHighlightedMarker(int prevIdx, int newIdx) async {
    final controller = _mapController;
    if (controller == null) return;
    try {
      if (prevIdx >= 0 && prevIdx != newIdx && prevIdx < _sortedSpots.length) {
        final prev = _sortedSpots[prevIdx];
        await controller.removeMarker(
          id: prev.id.toString(),
          layerId: _markerLayerId,
        );
        await controller.addMarker(
          markerOption: MarkerOption(
            id: prev.id.toString(),
            latLng: LatLng(latitude: prev.latitude, longitude: prev.longitude),
            styleId: _spotMarkerStyleId,
            rank: 9999,
            text: prev.spotName,
          ),
          layerId: _markerLayerId,
        );
      }
      if (newIdx >= 0 && newIdx < _sortedSpots.length) {
        final next = _sortedSpots[newIdx];
        await controller.removeMarker(
          id: next.id.toString(),
          layerId: _markerLayerId,
        );
        await controller.addMarker(
          markerOption: MarkerOption(
            id: next.id.toString(),
            latLng: LatLng(latitude: next.latitude, longitude: next.longitude),
            styleId: _spotHighlightedMarkerStyleId,
            rank: 10001,
            text: next.spotName,
          ),
          layerId: _markerLayerId,
        );
      }
    } on PlatformException catch (e) {
      debugPrint('[NearbySpotsScreen] updateHighlightedMarker error: $e');
    }
  }
}
