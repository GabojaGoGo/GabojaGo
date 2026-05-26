// user_data_service.dart
// 로컬 우선(SharedPreferences) + 변경 시 백엔드 sync
// - 읽기: 항상 로컬에서 즉시 반환
// - 쓰기: 로컬 갱신 후 비동기 백엔드 sync (실패해도 로컬은 유지)
// - 앱 시작(로그인 후): 백엔드에서 fetch → 로컬 덮어쓰기

import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:http/http.dart' as http;
import 'auth_service.dart';
import 'api_service.dart';

class UserDataService {
  UserDataService._();
  static final UserDataService instance = UserDataService._();

  SharedPreferences? _prefs;

  static const _kPrefs         = 'ud_prefs';          // {purposes:[],duration:''}
  static const _kFootprints    = 'ud_footprints';     // List<FootprintItem JSON>
  static const _kBucketList    = 'ud_bucket_list';    // List<BucketItem JSON>
  static const _kBenefits      = 'ud_benefits';       // {totalSaved:0, items:[]}
  static const _kSavedCourses  = 'ud_saved_courses';  // List<CourseItem JSON>

  // ── 초기화 ────────────────────────────────────────────

  Future<void> init() async {
    _prefs = await SharedPreferences.getInstance();
  }

  /// 로그인 완료 후 호출 — 백엔드 데이터로 로컬 갱신
  Future<void> syncFromServer() async {
    try {
      await Future.wait([
        _fetchPrefs(),
        _fetchFootprints(),
        _fetchBucketList(),
        _fetchBenefits(),
      ]);
    } catch (e) {
      debugPrint('[UserDataService] syncFromServer error: $e');
    }
  }

  // ══════════════════════════════════════════════════════
  // 취향 설정 (purposes + duration)
  // ══════════════════════════════════════════════════════

  Map<String, dynamic> getPrefs() {
    final raw = _prefs?.getString(_kPrefs);
    if (raw == null) return {'purposes': <String>[], 'duration': ''};
    return json.decode(raw) as Map<String, dynamic>;
  }

  List<String> get purposes =>
      List<String>.from(getPrefs()['purposes'] as List? ?? []);
  String get duration => (getPrefs()['duration'] as String?) ?? '';

  Future<void> savePrefs(List<String> purposes, String duration) async {
    final data = {'purposes': purposes, 'duration': duration};
    await _prefs?.setString(_kPrefs, json.encode(data));
    _syncPrefs(purposes, duration);   // 비동기 sync
  }

  Future<void> _fetchPrefs() async {
    final res = await _authGet('/me/prefs');
    if (res.statusCode != 200) return;
    final data = json.decode(res.body) as Map<String, dynamic>;
    final prefs = {
      'purposes': List<String>.from(data['purposes'] as List? ?? []),
      'duration': data['duration'] ?? '',
    };
    await _prefs?.setString(_kPrefs, json.encode(prefs));
  }

  void _syncPrefs(List<String> purposes, String duration) {
    _authPut('/me/prefs', {'purposes': purposes, 'duration': duration})
        .catchError((e) => debugPrint('[UserDataService] prefs sync error: $e'));
  }

  // ══════════════════════════════════════════════════════
  // 족적
  // ══════════════════════════════════════════════════════

  List<Map<String, dynamic>> getFootprints() {
    final raw = _prefs?.getString(_kFootprints);
    if (raw == null) return [];
    return List<Map<String, dynamic>>.from(
        (json.decode(raw) as List).map((e) => Map<String, dynamic>.from(e as Map)));
  }

  Future<Map<String, dynamic>> addFootprint({
    required String spotName,
    required String regionName,
    String? spotId,
    List<String> tags = const [],
  }) async {
    final item = {
      'id':         DateTime.now().millisecondsSinceEpoch,   // 임시 로컬 ID
      'spotId':     spotId ?? '',
      'spotName':   spotName,
      'regionName': regionName,
      'visitedAt':  DateTime.now().toIso8601String(),
      'tags':       tags,
    };
    final list = getFootprints();
    list.insert(0, item);
    await _prefs?.setString(_kFootprints, json.encode(list));

    // 비동기 sync → 서버 ID로 교체
    _authPost('/me/footprints', {
      'spotId':     spotId ?? '',
      'spotName':   spotName,
      'regionName': regionName,
      'tags':       tags,
    }).then((res) {
      if (res.statusCode == 200) {
        final serverId = json.decode(res.body)['id'] as int;
        _replaceLocalId(_kFootprints, item['id'] as int, serverId);
      }
    }).catchError((e) => debugPrint('[UserDataService] footprint sync: $e'));

    return item;
  }

  Future<void> deleteFootprint(dynamic id) async {
    final list = getFootprints()..removeWhere((e) => e['id'] == id);
    await _prefs?.setString(_kFootprints, json.encode(list));
    _authDelete('/me/footprints/$id')
        .catchError((e) => debugPrint('[UserDataService] footprint delete: $e'));
  }

  Future<void> _fetchFootprints() async {
    final res = await _authGet('/me/footprints');
    if (res.statusCode != 200) return;
    final list = json.decode(res.body) as List;
    await _prefs?.setString(_kFootprints, json.encode(list));
  }

  // ══════════════════════════════════════════════════════
  // 버킷리스트
  // ══════════════════════════════════════════════════════

  List<Map<String, dynamic>> getBucketList() {
    final raw = _prefs?.getString(_kBucketList);
    if (raw == null) return [];
    return List<Map<String, dynamic>>.from(
        (json.decode(raw) as List).map((e) => Map<String, dynamic>.from(e as Map)));
  }

  Future<Map<String, dynamic>> addBucketItem({
    required String title,
    String area = '',
    String note = '',
  }) async {
    final item = {
      'id':        DateTime.now().millisecondsSinceEpoch,
      'title':     title,
      'area':      area,
      'note':      note,
      'completed': false,
      'createdAt': DateTime.now().toIso8601String(),
    };
    final list = getBucketList();
    list.insert(0, item);
    await _prefs?.setString(_kBucketList, json.encode(list));

    _authPost('/me/bucket-list', {'title': title, 'area': area, 'note': note})
        .then((res) {
      if (res.statusCode == 200) {
        final serverId = json.decode(res.body)['id'] as int;
        _replaceLocalId(_kBucketList, item['id'] as int, serverId);
      }
    }).catchError((e) => debugPrint('[UserDataService] bucket sync: $e'));

    return item;
  }

  Future<void> updateBucketItem(dynamic id,
      {String? title, String? area, String? note, bool? completed}) async {
    final list = getBucketList();
    final idx = list.indexWhere((e) => e['id'] == id);
    if (idx == -1) return;
    if (title != null)     list[idx]['title']     = title;
    if (area != null)      list[idx]['area']       = area;
    if (note != null)      list[idx]['note']       = note;
    if (completed != null) list[idx]['completed']  = completed;
    await _prefs?.setString(_kBucketList, json.encode(list));

    _authPatch('/me/bucket-list/$id', {
      if (title != null) 'title': title,
      if (area != null)  'area': area,
      if (note != null)  'note': note,
      if (completed != null) 'completed': completed,
    }).catchError((e) => debugPrint('[UserDataService] bucket update: $e'));
  }

  Future<void> deleteBucketItem(dynamic id) async {
    final list = getBucketList()..removeWhere((e) => e['id'] == id);
    await _prefs?.setString(_kBucketList, json.encode(list));
    _authDelete('/me/bucket-list/$id')
        .catchError((e) => debugPrint('[UserDataService] bucket delete: $e'));
  }

  Future<void> _fetchBucketList() async {
    final res = await _authGet('/me/bucket-list');
    if (res.statusCode != 200) return;
    final list = json.decode(res.body) as List;
    await _prefs?.setString(_kBucketList, json.encode(list));
  }

  // ══════════════════════════════════════════════════════
  // 혜택 리포트
  // ══════════════════════════════════════════════════════

  Map<String, dynamic> getBenefitReports() {
    final raw = _prefs?.getString(_kBenefits);
    if (raw == null) return {'totalSaved': 0, 'items': <dynamic>[]};
    return json.decode(raw) as Map<String, dynamic>;
  }

  int get totalSaved => (getBenefitReports()['totalSaved'] as int?) ?? 0;

  /// 특정 혜택 ID 신청 여부 확인 (일회성 혜택용)
  bool isBenefitApplied(String benefitId) {
    if (benefitId.isEmpty) return false;
    final items = (getBenefitReports()['items'] as List? ?? []);
    return items.any((e) => (e as Map)['benefitId'] == benefitId);
  }

  /// 특정 혜택 ID의 총 신청 횟수 (반복 가능 혜택용)
  int getBenefitApplyCount(String benefitId) {
    if (benefitId.isEmpty) return 0;
    final items = (getBenefitReports()['items'] as List? ?? []);
    return items.where((e) => (e as Map)['benefitId'] == benefitId).length;
  }

  Future<void> addBenefitReport({
    required String benefitType,
    required String benefitLabel,
    required int amount,
    String? benefitId,
  }) async {
    final current = getBenefitReports();
    final items = List<Map<String, dynamic>>.from(
        (current['items'] as List).map((e) => Map<String, dynamic>.from(e as Map)));
    final item = {
      'id':           DateTime.now().millisecondsSinceEpoch,
      'benefitId':    benefitId ?? '',
      'benefitType':  benefitType,
      'benefitLabel': benefitLabel,
      'amount':       amount,
      'appliedAt':    DateTime.now().toIso8601String(),
    };
    items.insert(0, item);
    final newTotal = (current['totalSaved'] as int? ?? 0) + amount;
    await _prefs?.setString(_kBenefits,
        json.encode({'totalSaved': newTotal, 'items': items}));

    _authPost('/me/benefit-reports', {
      'benefitType':  benefitType,
      'benefitLabel': benefitLabel,
      'amount':       amount,
    }).catchError((e) => debugPrint('[UserDataService] benefit sync: $e'));
  }

  Future<void> _fetchBenefits() async {
    final res = await _authGet('/me/benefit-reports');
    if (res.statusCode != 200) return;
    final data = json.decode(res.body) as Map<String, dynamic>;
    await _prefs?.setString(_kBenefits, json.encode(data));
  }

  // ══════════════════════════════════════════════════════
  // 로컬 데이터 초기화 (로그아웃 / 탈퇴)
  // ══════════════════════════════════════════════════════

  // ══════════════════════════════════════════════════════
  // 저장된 코스 (로컬 전용)
  // ══════════════════════════════════════════════════════

  List<Map<String, dynamic>> getSavedCourses() {
    final raw = _prefs?.getString(_kSavedCourses);
    if (raw == null) return [];
    return List<Map<String, dynamic>>.from(
        (json.decode(raw) as List).map((e) => Map<String, dynamic>.from(e as Map)));
  }

  bool isCourseSaved(String contentId) =>
      getSavedCourses().any((e) => e['contentId'] == contentId);

  Future<void> saveCourse(Map<String, dynamic> course) async {
    if (isCourseSaved(course['contentId'] as String? ?? '')) return;
    final list = getSavedCourses();
    list.insert(0, {...course, 'savedAt': DateTime.now().toIso8601String()});
    await _prefs?.setString(_kSavedCourses, json.encode(list));
  }

  Future<void> removeSavedCourse(String contentId) async {
    final list = getSavedCourses()
      ..removeWhere((e) => e['contentId'] == contentId);
    await _prefs?.setString(_kSavedCourses, json.encode(list));
  }

  // ══════════════════════════════════════════════════════
  // 로컬 데이터 초기화 (로그아웃 / 탈퇴)
  // ══════════════════════════════════════════════════════

  Future<void> clearLocal() async {
    await _prefs?.remove(_kPrefs);
    await _prefs?.remove(_kFootprints);
    await _prefs?.remove(_kBucketList);
    await _prefs?.remove(_kBenefits);
    await _prefs?.remove(_kSavedCourses);
  }

  // ══════════════════════════════════════════════════════
  // 내부 HTTP 헬퍼
  // ══════════════════════════════════════════════════════

  String get _base => ApiService.baseUrl.replaceAll('/api', '');

  Future<http.Response> _authGet(String path) async {
    final token = await AuthService.instance.getValidAccessToken();
    return http.get(
      Uri.parse('$_base$path'),
      headers: {'Authorization': 'Bearer $token'},
    ).timeout(const Duration(seconds: 10));
  }

  Future<http.Response> _authPost(String path, Map<String, dynamic> body) async {
    final token = await AuthService.instance.getValidAccessToken();
    return http.post(
      Uri.parse('$_base$path'),
      headers: {'Authorization': 'Bearer $token', 'Content-Type': 'application/json'},
      body: json.encode(body),
    ).timeout(const Duration(seconds: 10));
  }

  Future<http.Response> _authPut(String path, Map<String, dynamic> body) async {
    final token = await AuthService.instance.getValidAccessToken();
    return http.put(
      Uri.parse('$_base$path'),
      headers: {'Authorization': 'Bearer $token', 'Content-Type': 'application/json'},
      body: json.encode(body),
    ).timeout(const Duration(seconds: 10));
  }

  Future<http.Response> _authPatch(String path, Map<String, dynamic> body) async {
    final token = await AuthService.instance.getValidAccessToken();
    return http.patch(
      Uri.parse('$_base$path'),
      headers: {'Authorization': 'Bearer $token', 'Content-Type': 'application/json'},
      body: json.encode(body),
    ).timeout(const Duration(seconds: 10));
  }

  Future<http.Response> _authDelete(String path) async {
    final token = await AuthService.instance.getValidAccessToken();
    return http.delete(
      Uri.parse('$_base$path'),
      headers: {'Authorization': 'Bearer $token'},
    ).timeout(const Duration(seconds: 10));
  }

  /// 로컬 임시 ID → 서버 발급 ID로 교체
  void _replaceLocalId(String key, int localId, int serverId) {
    final raw = _prefs?.getString(key);
    if (raw == null) return;
    final list = (json.decode(raw) as List)
        .map((e) => Map<String, dynamic>.from(e as Map))
        .toList();
    for (final item in list) {
      if (item['id'] == localId) item['id'] = serverId;
    }
    _prefs?.setString(key, json.encode(list));
  }
}
