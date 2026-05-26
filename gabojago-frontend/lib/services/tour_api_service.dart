// tour_api_service.dart
// 한국관광공사 TourAPI v4 호출 서비스
//
// 사용 API:
//  - KorService2     : detailCommon2, detailIntro2, detailInfo2, detailImage2
//  - PhotoGalleryService1 : gallerySearchList1
//
// ⚠️  .env 에 TOUR_API_KEY=<발급받은 서비스키> 를 추가해야 합니다.
//      data.go.kr → 한국관광공사_국문 관광정보 서비스_GW / 관광사진 정보_GW 신청

import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter_dotenv/flutter_dotenv.dart';
import 'package:http/http.dart' as http;

class TourApiService {
  static const String _korBase =
      'https://apis.data.go.kr/B551011/KorService2';
  static const String _photoBase =
      'https://apis.data.go.kr/B551011/PhotoGalleryService1';

  static String get _key => dotenv.env['TOUR_API_KEY'] ?? '';

  // ── URL 조립 ────────────────────────────────────────────────────
  // serviceKey는 base64 계열 문자가 포함되어 있어 Uri.queryParameters로
  // 넘기면 이중 인코딩 문제가 발생할 수 있으므로 직접 쿼리 문자열을 구성한다.
  static Uri _buildUri(String base, String endpoint,
      Map<String, String> params) {
    final common =
        'serviceKey=$_key&MobileOS=IOS&MobileApp=가보자GO&_type=json';
    final extra = params.entries
        .map((e) => '${e.key}=${Uri.encodeComponent(e.value)}')
        .join('&');
    return Uri.parse('$base/$endpoint?$common&$extra');
  }

  // ── 공통정보 조회 (개요·주소·전화·홈페이지·대표이미지) ─────────────
  static Future<Map<String, dynamic>?> getDetailCommon(
      String contentId) async {
    final uri = _buildUri(_korBase, 'detailCommon2', {
      'contentId': contentId,
      'defaultYN': 'Y',
      'firstImageYN': 'Y',
      'addrinfoYN': 'Y',
      'overviewYN': 'Y',
      'mapinfoYN': 'Y',
    });
    return _fetchOne(uri);
  }

  // ── 소개정보 조회 (휴관일·이용시간·주차 등) ─────────────────────
  static Future<Map<String, dynamic>?> getDetailIntro(
      String contentId, String contentTypeId) async {
    final uri = _buildUri(_korBase, 'detailIntro2', {
      'contentId': contentId,
      'contentTypeId': contentTypeId,
    });
    return _fetchOne(uri);
  }

  // ── 반복정보 조회 (입장료·부대시설 등 row 형식) ──────────────────
  static Future<List<Map<String, dynamic>>> getDetailInfo(
      String contentId, String contentTypeId) async {
    final uri = _buildUri(_korBase, 'detailInfo2', {
      'contentId': contentId,
      'contentTypeId': contentTypeId,
      'numOfRows': '20',
    });
    return _fetchList(uri);
  }

  // ── 이미지정보 조회 ──────────────────────────────────────────────
  static Future<List<String>> getDetailImages(String contentId) async {
    final uri = _buildUri(_korBase, 'detailImage2', {
      'contentId': contentId,
      'imageYN': 'Y',
      'numOfRows': '10',
      'pageNo': '1',
    });
    final items = await _fetchList(uri);
    return items
        .map((e) => (e['originimgurl'] as String?) ?? '')
        .where((url) => url.isNotEmpty)
        .toList();
  }

  // ── 관광사진 갤러리 키워드 검색 ──────────────────────────────────
  static Future<List<String>> getPhotoGallery(String keyword) async {
    final uri = _buildUri(_photoBase, 'gallerySearchList1', {
      'keyword': keyword,
      'numOfRows': '8',
      'pageNo': '1',
      'arrange': 'C',
    });
    final items = await _fetchList(uri);
    return items
        .map((e) => (e['galWebImageUrl'] as String?) ?? '')
        .where((url) => url.isNotEmpty)
        .toList();
  }

  // ── 공통 HTTP 헬퍼 ──────────────────────────────────────────────

  static Future<Map<String, dynamic>?> _fetchOne(Uri uri) async {
    try {
      debugPrint('[TourAPI] GET $uri');
      final res = await http.get(uri).timeout(const Duration(seconds: 15));
      if (res.statusCode != 200) return null;
      final body = json.decode(utf8.decode(res.bodyBytes));
      final items = _extractItems(body);
      if (items is List && items.isNotEmpty) {
        return Map<String, dynamic>.from(items.first as Map);
      }
      if (items is Map) return Map<String, dynamic>.from(items);
      return null;
    } catch (e) {
      debugPrint('[TourAPI] _fetchOne error: $e');
      return null;
    }
  }

  static Future<List<Map<String, dynamic>>> _fetchList(Uri uri) async {
    try {
      debugPrint('[TourAPI] GET $uri');
      final res = await http.get(uri).timeout(const Duration(seconds: 15));
      if (res.statusCode != 200) return [];
      final body = json.decode(utf8.decode(res.bodyBytes));
      final items = _extractItems(body);
      if (items is List) {
        return items
            .whereType<Map>()
            .map((e) => Map<String, dynamic>.from(e))
            .toList();
      }
      if (items is Map) {
        return [Map<String, dynamic>.from(items)];
      }
      return [];
    } catch (e) {
      debugPrint('[TourAPI] _fetchList error: $e');
      return [];
    }
  }

  /// response → body → items → item 경로에서 실제 데이터 추출
  static dynamic _extractItems(dynamic body) {
    try {
      return body['response']?['body']?['items']?['item'];
    } catch (_) {
      return null;
    }
  }
}
