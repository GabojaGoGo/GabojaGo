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
}
