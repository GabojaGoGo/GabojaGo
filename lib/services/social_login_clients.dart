import 'package:flutter/foundation.dart';
import 'package:flutter_dotenv/flutter_dotenv.dart';
import 'package:flutter_naver_login/flutter_naver_login.dart';
import 'package:flutter_naver_login/interface/types/naver_login_status.dart';
import 'package:google_sign_in/google_sign_in.dart';
import 'package:kakao_flutter_sdk_user/kakao_flutter_sdk_user.dart';

enum SocialLoginProvider {
  kakao('KAKAO'),
  naver('NAVER'),
  google('GOOGLE');

  const SocialLoginProvider(this.apiValue);

  final String apiValue;
}

class SocialLoginToken {
  const SocialLoginToken({
    required this.provider,
    required this.accessToken,
  });

  final SocialLoginProvider provider;
  final String accessToken;
}

abstract interface class SocialLoginClient {
  SocialLoginProvider get provider;
  Future<SocialLoginToken> login();
  Future<void> logout();
}

class KakaoSocialLoginClient implements SocialLoginClient {
  @override
  SocialLoginProvider get provider => SocialLoginProvider.kakao;

  static const _scopes = ['profile_nickname', 'account_email'];

  @override
  Future<SocialLoginToken> login() async {
    OAuthToken token;
    if (!kIsWeb && await isKakaoTalkInstalled()) {
      try {
        token = await UserApi.instance.loginWithKakaoTalk();
      } catch (_) {
        token = await UserApi.instance.loginWithKakaoAccount();
      }
    } else {
      token = await UserApi.instance.loginWithKakaoAccount();
    }

    // v1.x: 로그인 후 scope 부족 시 추가 동의 요청
    final scopeToken = await _ensureScopes();
    if (scopeToken != null) token = scopeToken;

    return SocialLoginToken(
      provider: provider,
      accessToken: token.accessToken,
    );
  }

  /// scope 부족 시 추가 동의 요청 (kakao_flutter_sdk_user 1.x 방식)
  Future<OAuthToken?> _ensureScopes() async {
    try {
      final scopeInfo = await UserApi.instance.scopes(scopes: _scopes);
      final missing = scopeInfo.scopes
              ?.where((s) => _scopes.contains(s.id) && !(s.agreed ?? false))
              .map((s) => s.id)
              .toList() ??
          [];
      if (missing.isEmpty) return null;
      return await UserApi.instance.loginWithNewScopes(missing);
    } catch (_) {
      return null;
    }
  }

  @override
  Future<void> logout() async {
    try {
      await UserApi.instance.logout();
    } catch (_) {}
  }
}

class NaverSocialLoginClient implements SocialLoginClient {
  @override
  SocialLoginProvider get provider => SocialLoginProvider.naver;

  @override
  Future<SocialLoginToken> login() async {
    final result = await FlutterNaverLogin.logIn();
    if (result.status != NaverLoginStatus.loggedIn) {
      throw Exception('네이버 로그인이 취소되었거나 실패했습니다.');
    }
    final token = await FlutterNaverLogin.getCurrentAccessToken();
    if (!token.isValid() || token.accessToken.isEmpty) {
      throw Exception('네이버 access token을 가져오지 못했습니다.');
    }
    return SocialLoginToken(
      provider: provider,
      accessToken: token.accessToken,
    );
  }

  @override
  Future<void> logout() async {
    try {
      await FlutterNaverLogin.logOutAndDeleteToken();
    } catch (_) {}
  }
}

class GoogleSocialLoginClient implements SocialLoginClient {
  GoogleSocialLoginClient()
      : _googleSignIn = GoogleSignIn(
          scopes: const ['email', 'profile', 'openid'],
          serverClientId: dotenv.env['GOOGLE_WEB_CLIENT_ID'],
        );

  final GoogleSignIn _googleSignIn;

  @override
  SocialLoginProvider get provider => SocialLoginProvider.google;

  @override
  Future<SocialLoginToken> login() async {
    final account = await _googleSignIn.signIn();
    if (account == null) {
      throw Exception('Google 로그인이 취소되었습니다.');
    }
    final auth = await account.authentication;
    final token = auth.accessToken ?? auth.idToken;
    if (token == null || token.isEmpty) {
      throw Exception('Google token을 가져오지 못했습니다.');
    }
    return SocialLoginToken(
      provider: provider,
      accessToken: token,
    );
  }

  @override
  Future<void> logout() async {
    try {
      await _googleSignIn.signOut();
    } catch (_) {}
  }
}
