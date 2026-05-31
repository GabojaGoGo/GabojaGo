// auth_service.dart
// 백엔드 통제 인증 서비스
// - accessToken: 메모리 저장 (앱 재시작 시 null → refresh로 복원)
// - refreshToken: flutter_secure_storage (영속)
// - nickname, userId: SharedPreferences (비민감 메타데이터)

import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:http/http.dart' as http;
import 'package:tripmate/core/models/user_prefs.dart';
import 'package:tripmate/infrastructure/api_service.dart';
import 'package:tripmate/core/services/social_login_clients.dart';
import 'package:tripmate/core/services/user_data_service.dart';

class AuthService {
  AuthService._();
  static final AuthService instance = AuthService._();

  // ── 저장소 ──────────────────────────────────────────────────
  static const _storage = FlutterSecureStorage(
    aOptions: AndroidOptions(encryptedSharedPreferences: true),
  );
  static const _kRefreshToken = 'auth_refresh_token';
  static const _kNickname  = 'auth_nickname';
  static const _kUserId    = 'auth_user_id';
  static const _kProvider  = 'auth_provider';
  static const _kPurposes  = 'auth_purposes';
  static const _kDuration  = 'auth_duration';

  SharedPreferences? _prefs;

  // accessToken은 메모리에만 저장 (앱 재시작 시 리셋)
  String? _accessToken;
  DateTime? _accessTokenExpiry;

  // 동시 refresh 방지 — 진행 중인 refresh Future를 공유
  Future<String?>? _pendingRefresh;

  // 앱 사용 중 세션 만료(refresh 401) 감지 시 호출되는 콜백
  // main.dart에서 등록 → 로그인 화면으로 강제 이동
  void Function()? onSessionExpired;

  // ── 초기화 ─────────────────────────────────────────────────

  Future<void> init() async {
    _prefs = await SharedPreferences.getInstance();
    // refresh token이 있으면 자동으로 access token 복원 시도
    final rt = await _storage.read(key: _kRefreshToken);
    if (rt != null) {
      try {
        await _refreshAccessToken(rt);
      } catch (_) {
        // 오프라인 등 실패해도 앱은 실행, 인증 필요 화면에서 재시도
      }
    }
  }

  // ── 게터 ────────────────────────────────────────────────────

  bool get isLoggedIn => _prefs?.getString(_kUserId) != null;
  String get userId   => _prefs?.getString(_kUserId)   ?? '';
  String get nickname => _prefs?.getString(_kNickname) ?? '';
  bool   get hasNickname => nickname.trim().isNotEmpty;
  List<String> get purposes => _prefs?.getStringList(_kPurposes) ?? [];
  String get duration => _prefs?.getString(_kDuration) ?? '';
  String get provider => _prefs?.getString(_kProvider) ?? '';

  UserPrefs toUserPrefs() => UserPrefs(
    nickname: nickname,
    purposes: purposes,
    duration: duration,
  );

  // ── 유효한 Access Token 반환 (만료 30초 전이면 자동 갱신) ──
  // 동시 호출 시 동일한 Future를 공유해 refresh 중복 방지 (RT Rotation 보호)

  Future<String?> getValidAccessToken() async {
    if (_accessToken != null && _accessTokenExpiry != null) {
      final remaining = _accessTokenExpiry!.difference(DateTime.now());
      if (remaining.inSeconds > 30) return _accessToken;
    }
    // 이미 refresh 진행 중 → 동일한 Future 대기 (중복 호출 방지)
    if (_pendingRefresh != null) return _pendingRefresh;

    final rt = await _storage.read(key: _kRefreshToken);
    if (rt == null) return null;

    if (_pendingRefresh != null) return _pendingRefresh;

    _pendingRefresh = _doRefresh(rt);
    try {
      return await _pendingRefresh;
    } finally {
      _pendingRefresh = null;
    }
  }

  Future<String?> _doRefresh(String rt) async {
    try {
      await _refreshAccessToken(rt);
      return _accessToken;
    } catch (_) {
      return null;
    }
  }

  // ── 소셜 SDK 로그인 ─────────────────────────────────────

  Future<({bool success, bool isNewUser})> loginWithKakao() =>
      loginWithProvider(SocialLoginProvider.kakao);

  Future<({bool success, bool isNewUser})> loginWithProvider(
      SocialLoginProvider provider) async {
    final providerToken = await _clientFor(provider).login();
    return _loginWithSocialToken(providerToken);
  }

  Future<({bool success, bool isNewUser})> _loginWithSocialToken(
      SocialLoginToken providerToken) async {
    final response = await http.post(
      Uri.parse('${ApiService.baseUrl.replaceAll('/api', '')}/auth/oauth/login'),
      headers: {'Content-Type': 'application/json'},
      body: json.encode({
        'provider': providerToken.provider.apiValue,
        'accessToken': providerToken.accessToken,
      }),
    ).timeout(const Duration(seconds: 15));

    if (response.statusCode == 401) throw Exception('소셜 토큰 검증 실패');
    if (response.statusCode != 200) {
      throw Exception('소셜 로그인 실패: ${response.statusCode}');
    }

    final data = json.decode(utf8.decode(response.bodyBytes)) as Map<String, dynamic>;
    final accessToken = data['accessToken'] as String;
    final refreshToken = data['refreshToken'] as String;
    final uid = data['userId'] as String;
    final nick = data['nickname'] as String? ?? '';
    final isNewUser = data['isNewUser'] as bool? ?? false;

    // 토큰 저장
    _setAccessToken(accessToken);
    await _storage.write(key: _kRefreshToken, value: refreshToken);
    await _prefs?.setString(_kUserId, uid);
    await _prefs?.setString(_kProvider, providerToken.provider.name);
    if (nick.isNotEmpty) await _prefs?.setString(_kNickname, nick);

    debugPrint('[AuthService] ${providerToken.provider.apiValue} 로그인 완료: userId=$uid, isNewUser=$isNewUser');
    return (success: true, isNewUser: isNewUser);
  }

  // ── 프로필 저장 ──────────────────────────────────────────

  Future<void> saveProfile({
    required String nickname,
    required List<String> purposes,
    required String duration,
  }) async {
    await _prefs?.setString(_kNickname, nickname.trim());
    await _prefs?.setStringList(_kPurposes, purposes);
    await _prefs?.setString(_kDuration, duration);

    // 백엔드 닉네임 동기화
    final token = await getValidAccessToken();
    if (token != null) {
      final response = await http.patch(
        Uri.parse('${ApiService.baseUrl.replaceAll('/api', '')}/me/profile'),
        headers: {
          'Authorization': 'Bearer $token',
          'Content-Type': 'application/json',
        },
        body: json.encode({'nickname': nickname.trim()}),
      ).timeout(const Duration(seconds: 10));
      if (response.statusCode != 200) {
        throw Exception('닉네임 서버 저장 실패: ${response.statusCode}');
      }
    }
  }

  Future<void> updatePrefs({
    required List<String> purposes,
    required String duration,
  }) async {
    await _prefs?.setStringList(_kPurposes, purposes);
    await _prefs?.setString(_kDuration, duration);
  }

  // ── 로그아웃 ─────────────────────────────────────────────

  Future<void> logout() async {
    final rt = await _storage.read(key: _kRefreshToken);
    final token = _accessToken;

    // 백엔드 세션 폐기
    if (token != null && rt != null) {
      try {
        await http.post(
          Uri.parse('${ApiService.baseUrl.replaceAll('/api', '')}/auth/logout'),
          headers: {
            'Authorization': 'Bearer $token',
            'Content-Type': 'application/json',
          },
          body: json.encode({'refreshToken': rt}),
        ).timeout(const Duration(seconds: 10));
      } catch (_) {}
    }

    await _clearLocal();
  }

  // ── 회원 탈퇴 ────────────────────────────────────────────

  Future<void> unlink() async {
    final token = _accessToken;
    if (token != null) {
      try {
        await http.post(
          Uri.parse('${ApiService.baseUrl.replaceAll('/api', '')}/auth/unlink'),
          headers: {'Authorization': 'Bearer $token'},
        ).timeout(const Duration(seconds: 10));
      } catch (_) {}
    }
    await _clearLocal();
  }

  // ── 비회원 모드 (기존 호환) ───────────────────────────────

  Future<void> setGuestMode() async {
    await _prefs?.setString(_kUserId, 'guest');
  }

  // ── 내부 유틸 ────────────────────────────────────────────

  Future<void> _refreshAccessToken(String rawRefreshToken) async {
    final response = await http.post(
      Uri.parse('${ApiService.baseUrl.replaceAll('/api', '')}/auth/refresh'),
      headers: {'Content-Type': 'application/json'},
      body: json.encode({'refreshToken': rawRefreshToken}),
    ).timeout(const Duration(seconds: 10));

    if (response.statusCode == 401) {
      // refresh token 무효(재사용/만료) → 로컬 삭제 후 로그인 화면 이동 트리거
      await _clearLocal();
      onSessionExpired?.call();
      throw Exception('세션 만료');
    }
    if (response.statusCode != 200) throw Exception('갱신 실패: ${response.statusCode}');

    final data = json.decode(response.body) as Map<String, dynamic>;
    _setAccessToken(data['accessToken'] as String);
    final newRt = data['refreshToken'] as String;
    await _storage.write(key: _kRefreshToken, value: newRt);
  }

  void _setAccessToken(String token) {
    _accessToken = token;
    // JWT payload에서 exp 추출
    try {
      final parts = token.split('.');
      final payload = json.decode(
          utf8.decode(base64Url.decode(base64Url.normalize(parts[1]))));
      final exp = payload['exp'] as int;
      _accessTokenExpiry =
          DateTime.fromMillisecondsSinceEpoch(exp * 1000);
    } catch (_) {
      _accessTokenExpiry = DateTime.now().add(const Duration(minutes: 14));
    }
  }

  Future<void> _clearLocal() async {
    _accessToken = null;
    _accessTokenExpiry = null;
    await UserDataService.instance.clearLocal();
    await _storage.delete(key: _kRefreshToken);
    await _prefs?.remove(_kUserId);
    await _prefs?.remove(_kNickname);
    await _prefs?.remove(_kProvider);
    await _prefs?.remove(_kPurposes);
    await _prefs?.remove(_kDuration);
  }

  SocialLoginClient _clientFor(SocialLoginProvider provider) {
    return switch (provider) {
      SocialLoginProvider.kakao => KakaoSocialLoginClient(),
      SocialLoginProvider.naver => NaverSocialLoginClient(),
      SocialLoginProvider.google => GoogleSocialLoginClient(),
    };
  }
}
