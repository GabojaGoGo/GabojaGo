import 'dart:async';
import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:flutter_dotenv/flutter_dotenv.dart';
import 'package:http/http.dart' as http;
import 'package:geolocator/geolocator.dart';
import 'package:geocoding/geocoding.dart';

class ApiService {
  // API_BASE_URL은 .env에서 읽음:
  //   iOS 시뮬레이터/Mac: http://localhost:8080/api
  //   Android 에뮬레이터: http://10.0.2.2:8080/api
  //   실기기: http://<Mac_IP>:8080/api  (예: http://192.168.219.124:8080/api)
  static String get baseUrl =>
      dotenv.env['API_BASE_URL'] ?? 'http://localhost:8080/api';

  static Future<bool> hasUsableLocationPermission() async {
    final serviceEnabled = await Geolocator.isLocationServiceEnabled();
    if (!serviceEnabled) {
      return false;
    }
    final permission = await Geolocator.checkPermission();
    return permission != LocationPermission.denied &&
        permission != LocationPermission.deniedForever;
  }

  static Future<LocationPermission> requestLocationPermission() async {
    final serviceEnabled = await Geolocator.isLocationServiceEnabled();
    if (!serviceEnabled) {
      return LocationPermission.denied;
    }
    var permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) {
      permission = await Geolocator.requestPermission();
    }
    return permission;
  }

  static Future<Position> getCurrentLocation() async {
    bool serviceEnabled;
    LocationPermission permission;

    serviceEnabled = await Geolocator.isLocationServiceEnabled();
    if (!serviceEnabled) {
      return Future.error('위치 서비스가 비활성화되어 있습니다.');
    }

    permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) {
      permission = await Geolocator.requestPermission();
      if (permission == LocationPermission.denied) {
        return Future.error('위치 권한이 거부되었습니다.');
      }
    }

    if (permission == LocationPermission.deniedForever) {
      return Future.error('위치 권한이 영구적으로 거부되었습니다.');
    }

    return await Geolocator.getCurrentPosition();
  }

  /// 📍 위경도를 주소로 변환 (예: "서울시 강남구")
  static Future<String> getAddressFromLatLng(double lat, double lng) async {
    try {
      // locale 설정 (한글 주소를 위해)
      try {
        await setLocaleIdentifier('ko_KR');
      } catch (_) {}

      List<Placemark> placemarks = await placemarkFromCoordinates(lat, lng);
      if (placemarks.isNotEmpty) {
        Placemark place = placemarks[0];
        // 주소 구성 조합 (시/도 + 구/군)
        String address = '';
        if (place.administrativeArea != null) {
          address += '${place.administrativeArea} ';
        }
        if (place.locality != null &&
            place.locality != place.administrativeArea) {
          address += '${place.locality} ';
        }
        if (place.subLocality != null && place.subLocality!.isNotEmpty) {
          address += place.subLocality!;
        }

        return address.trim().isEmpty ? '내 주변' : address.trim();
      }
      return '알 수 없는 지역';
    } catch (e) {
      debugPrint('Geocoding error: $e');
      return '지역명 확인 실패';
    }
  }

  static Future<List<dynamic>> getNearbySpots(double lat, double lng, {int limit = 10}) async {
    final url = '$baseUrl/spots?lat=$lat&lng=$lng&limit=$limit';
    debugPrint('Requesting: $url');
    try {
      final response = await http
          .get(Uri.parse(url))
          .timeout(const Duration(seconds: 20));
      debugPrint('Response Code: ${response.statusCode}');
      if (response.statusCode == 200) {
        return json.decode(utf8.decode(response.bodyBytes));
      } else {
        throw Exception('Server error: ${response.statusCode}');
      }
    } catch (e) {
      debugPrint('Connection Error (Spots): $e');
      rethrow;
    }
  }

  static Future<List<dynamic>> getNearbySpotCongestions(
    double lat,
    double lng, {
    int limit = 10,
  }) async {
    final url = '$baseUrl/spots/congestion?lat=$lat&lng=$lng&limit=$limit';
    debugPrint('Requesting: $url');
    try {
      final response = await http
          .get(Uri.parse(url))
          .timeout(const Duration(seconds: 20));
      debugPrint('Response Code: ${response.statusCode}');
      if (response.statusCode == 200) {
        return json.decode(utf8.decode(response.bodyBytes));
      } else {
        throw Exception('Server error: ${response.statusCode}');
      }
    } catch (e) {
      debugPrint('Connection Error (Spot Congestion): $e');
      rethrow;
    }
  }

  static Future<List<Map<String, dynamic>>> getBenefits() async {
    final url = '$baseUrl/benefits';
    debugPrint('Requesting: $url');
    try {
      final response = await http
          .get(Uri.parse(url))
          .timeout(const Duration(seconds: 20));
      debugPrint('Response Code: ${response.statusCode}');
      if (response.statusCode == 200) {
        final list = json.decode(utf8.decode(response.bodyBytes)) as List;
        return list.cast<Map<String, dynamic>>();
      } else {
        throw Exception('Server error: ${response.statusCode}');
      }
    } catch (e) {
      debugPrint('Connection Error (Benefits): $e');
      rethrow;
    }
  }

  static Future<Map<String, dynamic>> getCourseDetail(
    String contentId, {
    List<String> purposes = const [],
  }) async {
    try {
      final purposesParam = purposes.join(',');
      final url = '$baseUrl/courses/$contentId/detail?purposes=$purposesParam';
      final response = await http
          .get(Uri.parse(url))
          .timeout(const Duration(seconds: 30));
      if (response.statusCode == 200) {
        return json.decode(utf8.decode(response.bodyBytes)) as Map<String, dynamic>;
      }
      throw Exception('Server error: ${response.statusCode}');
    } on TimeoutException {
      throw Exception('코스 상세 요청 시간 초과');
    } catch (e) {
      debugPrint('[ApiService] getCourseDetail error: $e');
      rethrow;
    }
  }

  /// 코스 목록 + 각 코스 상세(places/distance/taketime)를 병렬로 로드
  static Future<List<Map<String, dynamic>>> getCoursesWithDetail({
    List<String> purposes = const [],
    String duration = '',
    double? lat,
    double? lng,
  }) async {
    final courses = await getCourses(purposes: purposes, duration: duration, lat: lat, lng: lng);
    if (courses.isEmpty) return [];

    final alreadyHydrated =
        courses.every((c) => (c['places'] as List?)?.isNotEmpty == true);
    if (alreadyHydrated) return courses;

    final details = await Future.wait(
      courses.map((c) => getCourseDetail(
        c['contentId'] as String? ?? '',
        purposes: purposes,
      ).catchError((_) => <String, dynamic>{})),
    );

    return List.generate(courses.length, (i) => {
      ...courses[i],
      'places':   (details[i]['places']   as List?) ?? [],
      'distance': (details[i]['distance'] as String?) ?? '',
      'taketime': (details[i]['taketime'] as String?) ?? '',
      'nearbyRestaurants':    (details[i]['nearbyRestaurants']    as List?) ?? [],
      'nearbyAccommodations': (details[i]['nearbyAccommodations'] as List?) ?? [],
    });
  }

  static Future<List<Map<String, dynamic>>> getCourses({
    List<String> purposes = const [],
    String duration = '',
    double? lat,
    double? lng,
    String? preferredAnchor,
  }) async {
    final purposesParam = purposes.join(',');
    var url = '$baseUrl/courses?purposes=$purposesParam&duration=$duration';
    if (lat != null && lng != null) {
      url += '&lat=$lat&lng=$lng';
    }
    if (preferredAnchor != null && preferredAnchor.isNotEmpty) {
      url += '&preferredAnchor=${Uri.encodeComponent(preferredAnchor)}';
    }
    debugPrint('Requesting: $url');
    try {
      final response = await http
          .get(Uri.parse(url))
          .timeout(const Duration(seconds: 20));
      debugPrint('Response Code: ${response.statusCode}');
      if (response.statusCode == 200) {
        final list = json.decode(utf8.decode(response.bodyBytes)) as List;
        return list.cast<Map<String, dynamic>>();
      } else {
        throw Exception('Server error: ${response.statusCode}');
      }
    } catch (e) {
      debugPrint('Connection Error (Courses): $e');
      rethrow;
    }
  }

  static Future<List<dynamic>> getNearbyFestivals(
    double lat,
    double lng,
  ) async {
    final url = '$baseUrl/festivals?lat=$lat&lng=$lng';
    debugPrint('Requesting: $url');
    try {
      final response = await http
          .get(Uri.parse(url))
          .timeout(const Duration(seconds: 20));
      debugPrint('Response Code: ${response.statusCode}');
      if (response.statusCode == 200) {
        return json.decode(utf8.decode(response.bodyBytes));
      } else {
        throw Exception('Server error: ${response.statusCode}');
      }
    } catch (e) {
      debugPrint('Connection Error (Festivals): $e');
      rethrow;
    }
  }
}
