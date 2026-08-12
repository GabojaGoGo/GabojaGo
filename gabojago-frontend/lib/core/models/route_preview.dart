/// OSRM 경로 미리보기 API의 요청·응답 모델이다.
///
/// JSON의 untyped 값은 이 모델을 만들 때만 다루고, 화면과 서비스 사이에는
/// 타입이 있는 값을 전달한다.
class RoutePreviewDay {
  const RoutePreviewDay({
    required this.day,
    required this.placeIds,
    this.startAnchor,
  });

  final int day;
  final List<int> placeIds;
  final RoutePreviewStartAnchor? startAnchor;

  static List<RoutePreviewDay> fromCoursePlaces(
    Iterable<Map<String, Object?>> places,
  ) {
    final idsByDay = <int, List<int>>{};
    for (final place in places) {
      final placeId = place['placeId'];
      if (placeId is! num) continue;
      final explicitDay = place['day'];
      final day = explicitDay is num
          ? explicitDay.toInt()
          : _dayFromLabel(place['dayLabel'] as String?);
      idsByDay.putIfAbsent(day, () => []).add(placeId.toInt());
    }
    return idsByDay.entries
        .map((entry) => RoutePreviewDay(day: entry.key, placeIds: entry.value))
        .toList();
  }

  Map<String, Object?> toJson() => {
    'day': day,
    'placeIds': placeIds,
    'startAnchor': ?startAnchor?.toJson(),
  };

  static int _dayFromLabel(String? label) {
    final value = RegExp(r'\d+').firstMatch(label ?? '')?.group(0);
    return value == null ? 1 : int.parse(value);
  }
}

class RoutePreviewStartAnchor {
  const RoutePreviewStartAnchor({
    required this.name,
    required this.lat,
    required this.lng,
  });

  final String name;
  final double lat;
  final double lng;

  Map<String, Object> toJson() => {'name': name, 'lat': lat, 'lng': lng};
}

/// 저장 직전의 최종 장소 목록으로 경로 미리보기 요청·지도 경로를 만든다.
///
/// 편집 중 미리보기는 아직 선택되지 않은 슬롯을 포함하지 않을 수 있으므로,
/// 자동 채움 뒤에는 이 변환만 사용해 최종 코스 지도 경로를 교체한다.
class GeneratedCourseRoutePreview {
  const GeneratedCourseRoutePreview._();

  static List<RoutePreviewDay> requestDays(
    Iterable<Map<String, dynamic>> places,
  ) => RoutePreviewDay.fromCoursePlaces(
    places.map((place) => Map<String, Object?>.from(place)),
  ).where((day) => day.placeIds.length >= 2).toList();

  static List<Map<String, dynamic>> detailPaths(
    Iterable<RoutePreviewPath> paths, {
    required String Function(int day) dayLabel,
  }) => paths
      .where((path) => path.points.length >= 2)
      .map(
        (path) => {
          'day': path.day,
          'dayLabel': dayLabel(path.day),
          'points': path.points.map((point) => point.toJson()).toList(),
        },
      )
      .toList();
}

class RoutePreviewPath {
  const RoutePreviewPath({
    required this.day,
    required this.points,
    this.transitSegments = const [],
  });

  final int day;
  final List<RoutePreviewPoint> points;
  final List<RoutePreviewTransitSegment> transitSegments;

  static List<RoutePreviewPath> listFromResponse(Object? response) {
    if (response is! Map) {
      throw const FormatException('경로 미리보기 응답 형식이 올바르지 않습니다.');
    }
    final values = response['routePaths'];
    if (values is! List) return const [];
    return values
        .whereType<Map>()
        .map((value) => RoutePreviewPath.fromJson(value))
        .toList();
  }

  factory RoutePreviewPath.fromJson(Map value) {
    final day = value['day'];
    if (day is! num) {
      throw const FormatException('경로 일자 값이 올바르지 않습니다.');
    }
    final rawPoints = value['points'];
    return RoutePreviewPath(
      day: day.toInt(),
      points: rawPoints is List
          ? rawPoints.whereType<Map>().map(RoutePreviewPoint.fromJson).toList()
          : const [],
      transitSegments: (value['transitSegments'] as List? ?? const [])
          .whereType<Map>()
          .map(RoutePreviewTransitSegment.fromJson)
          .toList(),
    );
  }

  /// 기존 지도 위젯이 사용하는 형태로 바꾸는 마지막 호환 경계다.
  Map<String, Object> toLegacyMap() => {
    'dayLabel': 'DAY $day',
    'points': points.map((point) => point.toJson()).toList(),
  };
}

/// 대중교통 미리보기에서 장소 사이 한 구간의 실제 선택 결과다.
class RoutePreviewTransitSegment {
  const RoutePreviewTransitSegment({
    required this.recommendedMode,
    required this.durationSeconds,
    required this.directWalkDurationSeconds,
    required this.distanceMeters,
    required this.walkingMeters,
    required this.transferCount,
    required this.fromName,
    required this.toName,
    this.boardStationName,
    this.boardExitNumber,
    this.alightStationName,
    this.alightExitNumber,
  });

  final String recommendedMode;
  final int durationSeconds;
  final int directWalkDurationSeconds;
  final int distanceMeters;
  final int walkingMeters;
  final int transferCount;
  final String fromName;
  final String toName;
  final String? boardStationName;
  final String? boardExitNumber;
  final String? alightStationName;
  final String? alightExitNumber;

  factory RoutePreviewTransitSegment.fromJson(Map value) =>
      RoutePreviewTransitSegment(
        recommendedMode: value['recommendedMode'] as String? ?? 'WALK',
        durationSeconds: (value['durationSeconds'] as num?)?.toInt() ?? 0,
        directWalkDurationSeconds:
            (value['directWalkDurationSeconds'] as num?)?.toInt() ?? 0,
        distanceMeters: (value['distanceMeters'] as num?)?.toInt() ?? 0,
        walkingMeters: (value['walkingMeters'] as num?)?.toInt() ?? 0,
        transferCount: (value['transferCount'] as num?)?.toInt() ?? 0,
        fromName: value['fromName'] as String? ?? '',
        toName: value['toName'] as String? ?? '',
        boardStationName: value['boardStationName'] as String?,
        boardExitNumber: value['boardExitNumber'] as String?,
        alightStationName: value['alightStationName'] as String?,
        alightExitNumber: value['alightExitNumber'] as String?,
      );
}

class RoutePreviewPoint {
  const RoutePreviewPoint({required this.lat, required this.lng});

  final double lat;
  final double lng;

  factory RoutePreviewPoint.fromJson(Map value) {
    final lat = value['lat'];
    final lng = value['lng'];
    if (lat is! num || lng is! num) {
      throw const FormatException('경로 좌표 값이 올바르지 않습니다.');
    }
    return RoutePreviewPoint(lat: lat.toDouble(), lng: lng.toDouble());
  }

  Map<String, double> toJson() => {'lat': lat, 'lng': lng};
}
