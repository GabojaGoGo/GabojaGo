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

class RoutePreviewPath {
  const RoutePreviewPath({required this.day, required this.points});

  final int day;
  final List<RoutePreviewPoint> points;

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
    );
  }

  /// 기존 지도 위젯이 사용하는 형태로 바꾸는 마지막 호환 경계다.
  Map<String, Object> toLegacyMap() => {
    'dayLabel': 'DAY $day',
    'points': points.map((point) => point.toJson()).toList(),
  };
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
