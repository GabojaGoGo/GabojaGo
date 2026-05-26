import 'dart:async';
import 'dart:math' as math;
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/foundation.dart' show compute;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart' show PlatformException;
import 'package:geolocator/geolocator.dart';
import 'package:image/image.dart' as img;
import 'package:kakao_maps_flutter/kakao_maps_flutter.dart';

import '../services/api_service.dart';

import '../widgets/benefit_chip.dart';
import '../widgets/spot_card.dart';
import 'spot_detail_screen.dart';

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
  static const String _spotHighlightedMarkerStyleId = 'tripmate_spot_marker_highlighted';
  static const String _currentMarkerStyleId = 'tripmate_current_location_marker';
  static const String _currentMarkerId = 'tripmate_current_location';

  /// 하단 카드 패널 높이 (px) — 카메라 오프셋 계산에도 사용
  static const double _kCardPanelH = 156.0;
  /// 상단 배너 영역 높이 (top padding 12 + 배너 본체 ~44)
  static const double _kBannerH = 56.0;

  KakaoMapController? _mapController;
  late final PageController _cardController;

  bool _isMarkersReady = false;
  bool _isRefreshing = false;       // 새 관광지 fetch 중 표시
  bool _programmaticMove = false;   // 코드 이동과 사용자 패닝 구분
  bool _showSearchHereBtn = false;  // "이 지역 검색" 버튼 노출 여부
  int _currentCardIndex = 0;
  int? _programmaticTargetPage;     // animateToPage 목표 페이지 — 도달 전까지 onPageChanged 무시

  // 탭으로 사용될 때 자체 GPS 초기화 상태
  late double _lat;
  late double _lng;
  late String _locName;
  bool _isInitializing = false;

  // 카메라 이동 종료 스트림 구독 + 마커 탭 스트림 구독
  StreamSubscription<CameraMoveEndEvent>? _cameraSub;
  StreamSubscription<LabelClickEvent>? _labelSub;

  /// 현재 카메라 중심 좌표 (onCameraMoveEnd 에서 업데이트)
  LatLng? _cameraCenterLatLng;
  /// 마지막으로 fetch한 중심 좌표 — "이 지역 검색" 버튼 표시 기준
  LatLng? _lastFetchCenter;
  /// "이 지역 검색" 버튼을 띄우는 최소 이동 거리 (m)
  static const double _kSearchBtnThreshold = 300.0;

  /// 거리순 정렬된 관광지 목록 (카드 순서, 업데이트 가능)
  List<SpotData> _sortedSpots = [];

  @override
  void initState() {
    super.initState();
    _lat = widget.currentLat;
    _lng = widget.currentLng;
    _locName = widget.locationName.isEmpty ? '위치 확인 중...' : widget.locationName;
    _cardController = PageController(viewportFraction: 0.88);

    if (widget.currentLat == 0.0) {
      // 탭으로 진입: GPS 자체 취득
      _isInitializing = true;
      _sortedSpots = [];
      WidgetsBinding.instance.addPostFrameCallback((_) => _initFromGps());
    } else {
      // 파라미터로 진입: 넘긴 데이터 즉시 표시 후 풀 fetch
      _sortedSpots = [...widget.spots]
        ..sort((a, b) {
          final dA = Geolocator.distanceBetween(_lat, _lng, a.latitude, a.longitude);
          final dB = Geolocator.distanceBetween(_lat, _lng, b.latitude, b.longitude);
          return dA.compareTo(dB);
        });
      _lastFetchCenter = LatLng(latitude: _lat, longitude: _lng);
      WidgetsBinding.instance.addPostFrameCallback((_) => _fetchSpotsFull(_lat, _lng));
    }
  }

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
          position.latitude, position.longitude);
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

  Future<void> _fetchSpotsFull(double lat, double lng) async {
    try {
      final results = await Future.wait([
        ApiService.getNearbySpots(lat, lng, limit: 300),
        ApiService.getNearbySpotCongestions(lat, lng, limit: 300),
      ]);
      if (!mounted) return;

      final spotsRaw = results[0];
      final congestionRaw = results[1];

      final congestionById = <int, Map<String, dynamic>>{
        for (final item in congestionRaw)
          ((item as Map<String, dynamic>)['id'] as num?)?.toInt() ?? -1: item,
      };

      var spots = spotsRaw
          .map((json) => SpotData.fromJson(json as Map<String, dynamic>))
          .toList();
      spots = spots.map((spot) {
        final c = congestionById[spot.id];
        if (c == null) return spot;
        return spot.copyWith(
          congestion: (c['congestion'] as String?)?.trim(),
          congestionSource: c['congestionSource'] as String?,
          congestionBaseYmd: c['congestionBaseYmd'] as String?,
        );
      }).toList()
        ..sort((a, b) {
          final dA = Geolocator.distanceBetween(lat, lng, a.latitude, a.longitude);
          final dB = Geolocator.distanceBetween(lat, lng, b.latitude, b.longitude);
          return dA.compareTo(dB);
        });

      setState(() => _sortedSpots = spots);
      // 마커를 새 목록으로 갱신 (setState 완료 후 다음 프레임에서 실행)
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _updateMapMarkers();
      });
    } catch (_) {
      // 실패해도 기존 목록 유지
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
    final lowCount =
        spots.where((s) => s.congestion == '낮음' || s.congestion == '예측중').length;
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
      appBar: AppBar(
        title: Text('$_locName 주변'),
      ),
      // 지도 전체화면 — 배너·카드 모두 hover overlay
      body: Stack(
        children: [
          // ── 지도 (전체 body) ───────────────────────────────────
          KakaoMap(
            initialPosition: LatLng(latitude: _lat, longitude: _lng),
            initialLevel: 14,
            onMapCreated: _handleMapCreated,
          ),

          // ── 혼잡도 배너 (상단 overlay) ─────────────────────────
          Positioned(
            top: 12, left: 16, right: 16,
            child: _CongestionBanner(isRelaxed: isRelaxed),
          ),

          // ── 마커 로딩 오버레이 ─────────────────────────────────
          if (!_isMarkersReady)
            Positioned.fill(
              child: IgnorePointer(
                child: Container(
                  color: Colors.white.withValues(alpha: 0.45),
                  child: const Center(
                    child: CircularProgressIndicator(
                        color: Color(0xFF1B8C6E), strokeWidth: 3),
                  ),
                ),
              ),
            ),

          // ── "이 지역 관광지 검색" 수동 버튼 ──────────────────────
          // 카메라가 마지막 fetch 위치에서 _kSearchBtnThreshold 이상 벗어나면 표시
          AnimatedPositioned(
            duration: const Duration(milliseconds: 220),
            curve: Curves.easeOut,
            top: _showSearchHereBtn ? _kBannerH + 10 : _kBannerH - 50,
            left: 0, right: 0,
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
                          horizontal: 18, vertical: 10),
                      decoration: BoxDecoration(
                        color: const Color(0xFF1B8C6E),
                        borderRadius: BorderRadius.circular(22),
                        boxShadow: [
                          BoxShadow(
                            color: const Color(0xFF1B8C6E).withValues(alpha: 0.35),
                            blurRadius: 12,
                            offset: const Offset(0, 4),
                          ),
                        ],
                      ),
                      child: const Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(Icons.search_rounded,
                              size: 16, color: Colors.white),
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

          // ── 새 관광지 fetch 중 — 반투명 스피너 ─────────────────
          if (_isRefreshing)
            Positioned(
              top: _kBannerH + 12,
              left: 0,
              right: 0,
              child: Center(
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                  decoration: BoxDecoration(
                    color: Colors.white.withValues(alpha: 0.92),
                    borderRadius: BorderRadius.circular(20),
                    boxShadow: [
                      BoxShadow(
                          color: Colors.black.withValues(alpha: 0.1),
                          blurRadius: 8),
                    ],
                  ),
                  child: const Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      SizedBox(
                        width: 14,
                        height: 14,
                        child: CircularProgressIndicator(
                            color: Color(0xFF1B8C6E), strokeWidth: 2),
                      ),
                      SizedBox(width: 8),
                      Text('주변 관광지 불러오는 중...',
                          style: TextStyle(
                              fontSize: 12,
                              fontWeight: FontWeight.w600,
                              color: Color(0xFF1A1A1A))),
                    ],
                  ),
                ),
              ),
            ),

          // ── 현재 위치 버튼 + 목록 버튼 ────────────────────────
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
                  child: Icon(Icons.my_location_rounded,
                      color: Color(0xFF1565C0), size: 22),
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
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
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
                    const Icon(Icons.format_list_bulleted_rounded,
                        size: 16, color: Color(0xFF1A1A1A)),
                    const SizedBox(width: 6),
                    Text(
                      '목록 ${_sortedSpots.length}',
                      style: const TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w700,
                          color: Color(0xFF1A1A1A)),
                    ),
                  ],
                ),
              ),
            ),
          ),

          // ── 카드 캐러셀 (하단 hover) ───────────────────────────
          Positioned(
            left: 0, right: 0, bottom: 30,
            height: _kCardPanelH,
            child: _sortedSpots.isEmpty
                ? _buildEmptyCard()
                : _buildCardPanel(),
          ),
        ],
      ),
    );
  }

  /// 관광지가 없을 때 표시되는 빈 상태 카드
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
                color: Colors.black.withValues(alpha: 0.08), blurRadius: 8),
          ],
        ),
        child: const Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.search_off_rounded,
                color: Color(0xFF607D8B), size: 20),
            SizedBox(width: 10),
            Text('이 지역 주변에 관광지 정보가 없어요',
                style: TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                    color: Color(0xFF607D8B))),
          ],
        ),
      ),
    );
  }

  // 목록 modal bottom sheet
  void _showListSheet() {
    showModalBottomSheet(
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
              // 핸들 + 헤더
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 12, 20, 8),
                child: Column(
                  children: [
                    Container(
                      width: 36, height: 4,
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
                              color: Color(0xFF1A1A1A)),
                        ),
                        const Spacer(),
                        TextButton.icon(
                          onPressed: () => Navigator.pop(ctx),
                          icon: const Icon(Icons.close, size: 18),
                          label: const Text('닫기'),
                          style: TextButton.styleFrom(
                              foregroundColor: Colors.grey.shade600),
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
                  itemBuilder: (ctx, i) => _buildListTile(
                    ctx,
                    _sortedSpots[i],
                    onTapOverride: () {
                      Navigator.pop(ctx);
                      _onSpotTapped(_sortedSpots[i]);
                    },
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  // ── 지도 초기화 ───────────────────────────────────────────────

  Future<void> _handleMapCreated(KakaoMapController controller) async {
    _mapController = controller;

    // ── 스트림 구독 ──────────────────────────────────────────────
    // 카메라 이동 종료 → 가장 가까운 관광지 하이라이트 + 새 관광지 fetch 트리거
    _cameraSub = controller.onCameraMoveEndStream.listen(_onCameraMoveEnd);
    // 마커(레이블) 탭 → 해당 카드 선택 + 카메라 이동
    _labelSub = controller.onLabelClickedStream.listen(_onMarkerTapped);

    await _initializeMarkers(controller);
  }

  Future<void> _initializeMarkers(
    KakaoMapController controller, {
    int attempt = 0,
  }) async {
    try {
      final markerBytes = await _buildSpotMarkerBytes();
      final highlightedMarkerBytes = await _buildSpotMarkerHighlightedBytes();
      final currentBytes = await _buildCurrentLocationMarkerBytes();

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
                bytes: highlightedMarkerBytes,
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
                  longitude: entry.value.longitude),
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
      // initialSpotId가 있으면 해당 관광지로 이동, 없으면 현재 위치로
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

  void _jumpToInitialSpot() {
    final id = widget.initialSpotId;
    if (id == null) return;
    final idx = _sortedSpots.indexWhere((s) => s.id == id);
    if (idx < 0) return;
    final prevIdx = _currentCardIndex;
    setState(() => _currentCardIndex = idx);
    // 첫 프레임 이후 페이지 이동
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_cardController.hasClients) {
        _cardController.jumpToPage(idx);
      }
    });
    _updateHighlightedMarker(prevIdx, idx);
    _moveCameraToSpot(_sortedSpots[idx]);
  }

  /// 화면 전체가 지도지만 배너(상단)·카드(하단)가 가리므로
  /// 실제 가시 중심 = 화면 중심보다 위쪽 → 카메라 타깃을 남쪽으로 보정해
  /// 마커가 가시 영역 정중앙에 오도록 만든다.
  ///
  /// offset_deg = pixelOffset × metersPerPixel / 111_139
  ///   pixelOffset = (_kCardPanelH - _kBannerH) / 2   (~50px)
  ///   metersPerPixel ≈ 4 (Kakao zoom 14, 위도 37°N 기준)
  double _latOffset() {
    const offsetPx = (_kCardPanelH - _kBannerH) / 2; // ≈ 50 px
    const metersPerPx = 4.0; // zoom 14 근사값
    return offsetPx * metersPerPx / 111139.0; // ≈ 0.0018°
  }

  Future<void> _moveCameraToCurrentLocation() async {
    final controller = _mapController;
    if (controller == null) return;
    _programmaticMove = true;
    final offset = _latOffset();
    await controller.moveCamera(
      cameraUpdate: CameraUpdate(
        position: LatLng(
          latitude: _lat - offset,
          longitude: _lng,
        ),
        zoomLevel: 14,
        type: 0,
      ),
      animation: const CameraAnimation(
          duration: 400, autoElevation: true, isConsecutive: false),
    );
  }

  Future<void> _moveCameraToSpot(SpotData spot) async {
    final controller = _mapController;
    if (controller == null) return;
    _programmaticMove = true;
    final offset = _latOffset();
    await controller.moveCamera(
      cameraUpdate: CameraUpdate(
        position: LatLng(
          latitude: spot.latitude - offset,
          longitude: spot.longitude,
        ),
        zoomLevel: 15,
        type: 0,
      ),
      animation: const CameraAnimation(
          duration: 350, autoElevation: true, isConsecutive: false),
    );
  }

  void _onSpotTapped(SpotData spot) {
    final idx = _sortedSpots.indexWhere((s) => s.id == spot.id);
    if (idx >= 0) {
      setState(() => _currentCardIndex = idx);
      _cardController.animateToPage(idx,
          duration: const Duration(milliseconds: 300), curve: Curves.easeOut);
    }
    _moveCameraToSpot(spot);
  }

  // ── 카메라 이동 종료 핸들러 ────────────────────────────────────

  void _onCameraMoveEnd(CameraMoveEndEvent event) {
    // 현재 카메라 중심 항상 기록
    _cameraCenterLatLng =
        LatLng(latitude: event.latitude, longitude: event.longitude);

    if (_programmaticMove) {
      // 카드 스와이프 / 내 위치 버튼 등 코드 이동 → 버튼·하이라이트 건너뜀
      _programmaticMove = false;
      return;
    }

    // 마지막 fetch 위치에서 임계값 이상 벗어났으면 "이 지역 검색" 버튼 표시
    final prev = _lastFetchCenter;
    if (prev != null) {
      final dist = Geolocator.distanceBetween(
          event.latitude, event.longitude, prev.latitude, prev.longitude);
      final shouldShow = dist >= _kSearchBtnThreshold;
      if (shouldShow != _showSearchHereBtn) {
        setState(() => _showSearchHereBtn = shouldShow);
      }
    }
  }

  // ── 마커 탭 핸들러 (onLabelClickedStream) ─────────────────────

  void _onMarkerTapped(LabelClickEvent event) {
    if (event.labelId == _currentMarkerId) return; // 내 위치 마커 제외
    final spotId = int.tryParse(event.labelId);
    if (spotId == null) return;
    final idx = _sortedSpots.indexWhere((s) => s.id == spotId);
    if (idx < 0) return;
    final prevIdx = _currentCardIndex;
    _programmaticMove = true; // 카드 선택 시 카메라 이동은 코드 이동
    _programmaticTargetPage = idx; // 목표 도달 전까지 onPageChanged 무시
    setState(() => _currentCardIndex = idx);
    _updateHighlightedMarker(prevIdx, idx);
    if (_cardController.hasClients) {
      _cardController.animateToPage(idx,
          duration: const Duration(milliseconds: 300), curve: Curves.easeOut);
    }
    _moveCameraToSpot(_sortedSpots[idx]);
  }

  // ── "이 지역 검색" 버튼 탭 핸들러 ────────────────────────────────

  void _onSearchHere() {
    final center = _cameraCenterLatLng;
    if (center == null) return;
    setState(() => _showSearchHereBtn = false);
    _fetchNewSpots(center.latitude, center.longitude);
  }

  // ── 새 관광지 수동 fetch ──────────────────────────────────────

  Future<void> _fetchNewSpots(double lat, double lng) async {
    if (!mounted) return;
    setState(() {
      _isRefreshing = true;
      _showSearchHereBtn = false; // 버튼 숨김 (fetch 시작)
    });

    try {
      // 관광지 + 혼잡도 병렬 요청
      final results = await Future.wait([
        ApiService.getNearbySpots(lat, lng, limit: 300),
        ApiService.getNearbySpotCongestions(lat, lng, limit: 300),
      ]);

      if (!mounted) return;

      final spotsRaw = results[0];
      final congestionRaw = results[1];

      // 혼잡도 ID 맵 구성
      final congestionById = <int, Map<String, dynamic>>{
        for (final item in congestionRaw)
          ((item as Map<String, dynamic>)['id'] as num?)?.toInt() ?? -1: item,
      };

      // SpotData 파싱 + 혼잡도 병합
      var newSpots = spotsRaw
          .map((json) => SpotData.fromJson(json as Map<String, dynamic>))
          .toList();
      newSpots = newSpots.map((spot) {
        final c = congestionById[spot.id];
        if (c == null) return spot;
        return spot.copyWith(
          congestion: (c['congestion'] as String?)?.trim(),
          congestionSource: c['congestionSource'] as String?,
          congestionBaseYmd: c['congestionBaseYmd'] as String?,
        );
      }).toList();

      // 새 중심 기준 거리순 정렬
      newSpots.sort((a, b) {
        final dA = Geolocator.distanceBetween(lat, lng, a.latitude, a.longitude);
        final dB = Geolocator.distanceBetween(lat, lng, b.latitude, b.longitude);
        return dA.compareTo(dB);
      });

      _lastFetchCenter = LatLng(latitude: lat, longitude: lng);

      setState(() {
        _sortedSpots = newSpots;
        _currentCardIndex = 0;
        _isRefreshing = false;
      });

      // 카드 첫 번째로 이동
      if (_cardController.hasClients && newSpots.isNotEmpty) {
        _cardController.animateToPage(0,
            duration: const Duration(milliseconds: 300), curve: Curves.easeOut);
      }

      // 지도 마커 갱신
      await _updateMapMarkers();
    } catch (e) {
      debugPrint('[NearbySpotsScreen] fetchNewSpots error: $e');
      if (mounted) setState(() => _isRefreshing = false);
    }
  }

  // ── 마커 전체 갱신 ────────────────────────────────────────────

  Future<void> _updateMapMarkers() async {
    final controller = _mapController;
    if (controller == null) return;
    try {
      // 기존 마커 전부 제거 후 재등록
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
          ..._sortedSpots.asMap().entries.map((entry) => MarkerOption(
                id: entry.value.id.toString(),
                latLng: LatLng(
                    latitude: entry.value.latitude,
                    longitude: entry.value.longitude),
                styleId: entry.key == _currentCardIndex
                    ? _spotHighlightedMarkerStyleId
                    : _spotMarkerStyleId,
                rank: entry.key == _currentCardIndex ? 10001 : 9999,
                text: entry.value.spotName,
              )),
        ],
      );
    } on PlatformException catch (e) {
      debugPrint('[NearbySpotsScreen] updateMapMarkers error: $e');
    }
  }

  // ── 카드 캐러셀 ───────────────────────────────────────────────

  Widget _buildCardPanel() {
    return Container(
      color: Colors.transparent,
      child: PageView.builder(
        controller: _cardController,
        onPageChanged: (idx) {
          // 마커 탭으로 animateToPage 중이면 목표 페이지 도달 전까지 무시
          if (_programmaticTargetPage != null) {
            if (idx == _programmaticTargetPage) _programmaticTargetPage = null;
            return;
          }
          // 사용자 직접 스와이프
          final prevIdx = _currentCardIndex;
          setState(() => _currentCardIndex = idx);
          _updateHighlightedMarker(prevIdx, idx);
          _moveCameraToSpot(_sortedSpots[idx]);
        },
        itemCount: _sortedSpots.length,
        itemBuilder: (context, idx) =>
            _buildSpotPageCard(_sortedSpots[idx], idx == _currentCardIndex),
      ),
    );
  }

  Widget _buildSpotPageCard(SpotData spot, bool isActive) {
    Color congestionColor() {
      switch (spot.congestion) {
        case '낮음': return const Color(0xFF1B8C6E);
        case '보통': return const Color(0xFFF57C00);
        case '높음': return const Color(0xFFD84315);
        case '예측중': return const Color(0xFF607D8B);
        default: return const Color(0xFF616161);
      }
    }

    final distM = Geolocator.distanceBetween(
        _lat, _lng, spot.latitude, spot.longitude);
    final distLabel = distM < 1000
        ? '${distM.round()}m'
        : '${(distM / 1000).toStringAsFixed(distM >= 10000 ? 0 : 1)}km';

    return GestureDetector(
      // 탭 → 상세 화면으로 이동
      onTap: () => Navigator.push(
        context,
        MaterialPageRoute(
          builder: (_) => SpotDetailScreen(spot: spot),
        ),
      ),
      child: AnimatedContainer(
      duration: const Duration(milliseconds: 220),
      curve: Curves.easeOut,
      margin: EdgeInsets.only(
        left: 8, right: 8,
        top: isActive ? 8 : 20,   // 활성 카드는 위로 올라옴
        bottom: isActive ? 8 : 4,
      ),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: isActive
            ? Border.all(color: const Color(0xFF1B8C6E), width: 1.5)
            : null,
        boxShadow: [
          const BoxShadow(color: Color(0x05000000), blurRadius: 0, spreadRadius: 1),
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
            // 썸네일
            SizedBox(
              width: 110,
              child: (spot.imageUrl != null && spot.imageUrl!.isNotEmpty)
                  ? Image.network(spot.imageUrl!,
                      fit: BoxFit.cover,
                      height: double.infinity,
                      errorBuilder: (_, __, ___) => _cardPlaceholder(spot))
                  : _cardPlaceholder(spot),
            ),
            // 정보
            Expanded(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
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
                        Icon(Icons.place_outlined,
                            size: 11, color: Colors.grey.shade500),
                        const SizedBox(width: 2),
                        Expanded(
                          child: Text(spot.areaName,
                              style: TextStyle(
                                  fontSize: 11, color: Colors.grey.shade500),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis),
                        ),
                      ],
                    ),
                    const SizedBox(height: 3),
                    Row(
                      children: [
                        Icon(Icons.directions_walk,
                            size: 11, color: Colors.grey.shade600),
                        const SizedBox(width: 2),
                        Text(distLabel,
                            style: TextStyle(
                                fontSize: 11,
                                fontWeight: FontWeight.w600,
                                color: Colors.grey.shade700)),
                      ],
                    ),
                    const SizedBox(height: 8),
                    Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 8, vertical: 3),
                      decoration: BoxDecoration(
                        color: congestionColor().withValues(alpha: 0.1),
                        borderRadius: BorderRadius.circular(6),
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(Icons.people_outline,
                              size: 11, color: congestionColor()),
                          const SizedBox(width: 3),
                          Text('혼잡도 ${spot.congestion}',
                              style: TextStyle(
                                  fontSize: 10,
                                  fontWeight: FontWeight.w600,
                                  color: congestionColor())),
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
    ),   // AnimatedContainer
    );   // GestureDetector
  }

  Widget _cardPlaceholder(SpotData spot) {
    return Container(
      color: spot.placeholderColor.withValues(alpha: 0.1),
      child: Center(
        child: Icon(Icons.image_not_supported_outlined,
            color: spot.placeholderColor, size: 24),
      ),
    );
  }

  // ── 리스트 아이템 ─────────────────────────────────────────────

  Widget _buildListTile(BuildContext context, SpotData spot,
      {VoidCallback? onTapOverride}) {
    final isSelected = _sortedSpots.indexOf(spot) == _currentCardIndex;

    Color congestionColor() {
      switch (spot.congestion) {
        case '예측중':
          return const Color(0xFF607D8B);
        case '낮음':
          return const Color(0xFF1B8C6E);
        case '보통':
          return const Color(0xFFF57C00);
        case '높음':
          return const Color(0xFFD84315);
        default:
          return const Color(0xFF616161);
      }
    }

    final distM = Geolocator.distanceBetween(
        _lat, _lng, spot.latitude, spot.longitude);
    final distLabel = distM < 1000
        ? '${distM.round()}m'
        : '${(distM / 1000).toStringAsFixed(distM >= 10000 ? 0 : 1)}km';

    return InkWell(
      onTap: onTapOverride ?? () => _onSpotTapped(spot),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        color: isSelected
            ? const Color(0xFF1B8C6E).withValues(alpha: 0.07)
            : Colors.transparent,
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        child: Row(
          children: [
            // 썸네일
            Stack(
              children: [
                ClipRRect(
                  borderRadius: BorderRadius.circular(10),
                  child: Container(
                    width: 72,
                    height: 72,
                    color: spot.placeholderColor.withValues(alpha: 0.1),
                    child: (spot.imageUrl != null && spot.imageUrl!.isNotEmpty)
                        ? Image.network(spot.imageUrl!,
                            fit: BoxFit.cover,
                            errorBuilder: (_, __, ___) =>
                                _buildPlaceholder(spot))
                        : _buildPlaceholder(spot),
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
                      child: const Icon(Icons.check,
                          color: Colors.white, size: 10),
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
                      Icon(Icons.place_outlined,
                          size: 12, color: Colors.grey.shade500),
                      const SizedBox(width: 2),
                      Expanded(
                        child: Text(
                          spot.areaName,
                          style: TextStyle(
                              fontSize: 12, color: Colors.grey.shade500),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      const SizedBox(width: 8),
                      Icon(Icons.directions_walk,
                          size: 12, color: Colors.grey.shade600),
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
                    backgroundColor:
                        congestionColor().withValues(alpha: 0.1),
                    textColor: congestionColor(),
                    icon: Icons.people_outline,
                  ),
                ],
              ),
            ),
            const SizedBox(width: 8),
            Icon(Icons.chevron_right_rounded,
                color: isSelected
                    ? const Color(0xFF1B8C6E)
                    : Colors.grey.shade400,
                size: 20),
          ],
        ),
      ),
    );
  }

  Widget _buildPlaceholder(SpotData spot) {
    return Center(
      child: Icon(Icons.image_not_supported_outlined,
          color: spot.placeholderColor, size: 22),
    );
  }

  // ── 마커 이미지 빌더 ──────────────────────────────────────────

  Future<Uint8List> _buildSpotMarkerBytes() async {
    const double w = 48, h = 68;
    const double cx = w / 2;
    const double headR = 18.0;
    const double headCy = headR + 2;
    const double tipY = h - 3;

    final recorder = ui.PictureRecorder();
    final canvas = Canvas(recorder);

    canvas.drawPath(
      _pinPath(cx, headCy + 1.5, headR, tipY + 1.5),
      Paint()
        ..color = const Color(0x40000000)
        ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 4),
    );
    canvas.drawPath(
      _pinPath(cx, headCy, headR, tipY),
      Paint()..color = const Color(0xFF1B8C6E),
    );
    canvas.drawCircle(
        Offset(cx, headCy), 7, Paint()..color = const Color(0xFFFFFFFF));

    final image =
        await recorder.endRecording().toImage(w.toInt(), h.toInt());
    return _toRgba8Png(image);
  }

  Path _pinPath(double cx, double cy, double r, double tipY) {
    return Path()
      ..arcTo(Rect.fromCircle(center: Offset(cx, cy), radius: r), 0,
          -math.pi, false)
      ..quadraticBezierTo(cx - r * 0.5, cy + r * 1.6, cx, tipY)
      ..quadraticBezierTo(cx + r * 0.5, cy + r * 1.6, cx + r, cy)
      ..close();
  }

  Future<Uint8List> _buildSpotMarkerHighlightedBytes() async {
    const double w = 48, h = 68;
    const double cx = w / 2;
    const double headR = 18.0;
    const double headCy = headR + 2;
    const double tipY = h - 3;

    final recorder = ui.PictureRecorder();
    final canvas = Canvas(recorder);

    canvas.drawPath(
      _pinPath(cx, headCy + 1.5, headR, tipY + 1.5),
      Paint()
        ..color = const Color(0x40000000)
        ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 4),
    );
    canvas.drawPath(
      _pinPath(cx, headCy, headR, tipY),
      Paint()..color = const Color(0xFFFF6D00), // 주황색 하이라이트
    );
    canvas.drawCircle(
        Offset(cx, headCy), 7, Paint()..color = const Color(0xFFFFFFFF));

    final image =
        await recorder.endRecording().toImage(w.toInt(), h.toInt());
    return _toRgba8Png(image);
  }

  /// 이전 하이라이트 마커를 일반 스타일로, 새 마커를 하이라이트 스타일로 교체.
  /// prevIdx == -1 이면 이전 복원 생략 (최초 설정 시).
  Future<void> _updateHighlightedMarker(int prevIdx, int newIdx) async {
    final controller = _mapController;
    if (controller == null) return;
    try {
      // 이전 하이라이트 → 일반 스타일로 복원
      if (prevIdx >= 0 && prevIdx != newIdx && prevIdx < _sortedSpots.length) {
        final prev = _sortedSpots[prevIdx];
        await controller.removeMarker(
            id: prev.id.toString(), layerId: _markerLayerId);
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
      // 새 하이라이트 → 하이라이트 스타일 적용
      if (newIdx >= 0 && newIdx < _sortedSpots.length) {
        final next = _sortedSpots[newIdx];
        await controller.removeMarker(
            id: next.id.toString(), layerId: _markerLayerId);
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

  Future<Uint8List> _buildCurrentLocationMarkerBytes() async {
    final recorder = ui.PictureRecorder();
    final canvas = Canvas(recorder);
    const size = 88.0;
    const center = Offset(size / 2, size / 2);

    canvas.drawCircle(center, 24, Paint()..color = const Color(0x881565C0));
    canvas.drawCircle(center, 16, Paint()..color = const Color(0xFFFFFFFF));
    canvas.drawCircle(center, 10, Paint()..color = const Color(0xFF1565C0));

    final image =
        await recorder.endRecording().toImage(size.toInt(), size.toInt());
    return _toRgba8Png(image);
  }

  /// ui.Image → KakaoMaps SDK가 지원하는 8-bit RGBA PNG로 변환.
  /// Flutter의 기본 png 인코더가 일부 iOS 버전에서 16-bit 채널 PNG를
  /// 생성하여 KakaoMaps에서 "unsupport png pixel format: 7" 크래시가 발생하므로
  /// image 패키지로 강제 8-bit 인코딩. encodePng는 CPU 집약적이므로 isolate에서 실행.
  Future<Uint8List> _toRgba8Png(ui.Image uiImage) async {
    final rawData =
        await uiImage.toByteData(format: ui.ImageByteFormat.rawRgba);
    if (rawData == null) throw StateError('marker rawRgba failed');
    return compute(_encodePng8bit, _PngEncodeParams(
      width: uiImage.width,
      height: uiImage.height,
      rgba: rawData.buffer.asUint8List(),
    ));
  }
}

// ── PNG 8-bit 인코딩 (isolate용 top-level) ────────────────────────

class _PngEncodeParams {
  final int width;
  final int height;
  final Uint8List rgba;
  const _PngEncodeParams({required this.width, required this.height, required this.rgba});
}

Uint8List _encodePng8bit(_PngEncodeParams p) {
  final image = img.Image.fromBytes(
    width: p.width,
    height: p.height,
    bytes: p.rgba.buffer,
    numChannels: 4,
  );
  return Uint8List.fromList(img.encodePng(image));
}

// ── 혼잡도 배너 위젯 ───────────────────────────────────────────────

class _CongestionBanner extends StatelessWidget {
  final bool isRelaxed;
  const _CongestionBanner({required this.isRelaxed});

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
            isRelaxed
                ? '지금 주변은 대체로 여유로워요'
                : '지금 주변은 다소 혼잡한 편이에요',
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

// ── 지도 중앙 크로스헤어 위젯 ─────────────────────────────────────────

// ── 정렬 토글 위젯 ─────────────────────────────────────────────────

class _SortToggle extends StatelessWidget {
  final bool byDistance;
  final ValueChanged<bool> onToggle;

  const _SortToggle({required this.byDistance, required this.onToggle});

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 30,
      decoration: BoxDecoration(
        color: const Color(0xFFF0F0F0),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          _chip('거리순', byDistance, () => onToggle(true)),
          _chip('혼잡도순', !byDistance, () => onToggle(false)),
        ],
      ),
    );
  }

  Widget _chip(String label, bool active, VoidCallback onTap) {
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 180),
        padding: const EdgeInsets.symmetric(horizontal: 10),
        decoration: BoxDecoration(
          color: active ? const Color(0xFF1B8C6E) : Colors.transparent,
          borderRadius: BorderRadius.circular(7),
        ),
        alignment: Alignment.center,
        child: Text(
          label,
          style: TextStyle(
            fontSize: 12,
            fontWeight: FontWeight.w600,
            color: active ? Colors.white : Colors.grey.shade600,
          ),
        ),
      ),
    );
  }
}
