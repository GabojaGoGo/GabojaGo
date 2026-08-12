import 'package:flutter_test/flutter_test.dart';
import 'package:tripmate/core/models/route_preview.dart';

void main() {
  test('장소의 일자와 순서를 OSRM 요청 모델로 묶는다', () {
    final days = RoutePreviewDay.fromCoursePlaces([
      {'placeId': 101, 'dayLabel': 'DAY 1'},
      {'placeId': 205, 'day': 1},
      {'placeId': 301, 'dayLabel': 'DAY 2'},
    ]);

    expect(days.map((day) => day.toJson()), [
      {
        'day': 1,
        'placeIds': [101, 205],
      },
      {
        'day': 2,
        'placeIds': [301],
      },
    ]);
  });

  test('OSRM 응답을 지도 호환 경로로 변환한다', () {
    final paths = RoutePreviewPath.listFromResponse({
      'routePaths': [
        {
          'day': 1,
          'points': [
            {'lat': 35.1, 'lng': 129.1},
          ],
        },
      ],
    });

    expect(paths.single.toLegacyMap(), {
      'dayLabel': 'DAY 1',
      'points': [
        {'lat': 35.1, 'lng': 129.1},
      ],
    });
  });

  test('자동 채움 뒤 최종 장소 목록으로 유효한 일자별 지도 경로를 만든다', () {
    final days = GeneratedCourseRoutePreview.requestDays([
      {'placeId': 1, 'day': 1},
      {'placeId': 2, 'day': 1},
      {'placeId': 3, 'day': 2},
      {'placeId': 4, 'day': 2},
      {'placeId': 5, 'day': 2},
    ]);
    final paths = GeneratedCourseRoutePreview.detailPaths([
      const RoutePreviewPath(
        day: 1,
        points: [
          RoutePreviewPoint(lat: 35.1, lng: 129.1),
          RoutePreviewPoint(lat: 35.2, lng: 129.2),
        ],
      ),
      const RoutePreviewPath(
        day: 2,
        points: [
          RoutePreviewPoint(lat: 35.3, lng: 129.3),
          RoutePreviewPoint(lat: 35.4, lng: 129.4),
        ],
      ),
    ], dayLabel: (day) => 'DAY $day');

    expect(days.map((day) => day.placeIds), [
      [1, 2],
      [3, 4, 5],
    ]);
    expect(paths, [
      {
        'day': 1,
        'dayLabel': 'DAY 1',
        'points': [
          {'lat': 35.1, 'lng': 129.1},
          {'lat': 35.2, 'lng': 129.2},
        ],
      },
      {
        'day': 2,
        'dayLabel': 'DAY 2',
        'points': [
          {'lat': 35.3, 'lng': 129.3},
          {'lat': 35.4, 'lng': 129.4},
        ],
      },
    ]);
  });
}
