import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart' show PlatformException;
import 'package:flutter_dotenv/flutter_dotenv.dart';
import 'package:flutter_naver_login/flutter_naver_login.dart';
import 'package:flutter_naver_login/interface/types/naver_login_status.dart';
import 'package:google_sign_in/google_sign_in.dart';
import 'package:kakao_flutter_sdk_user/kakao_flutter_sdk_user.dart';

/// 사용자가 로그인 도중 직접 취소한 경우 — 에러 UI 없이 조용히 복귀해야 함
class SocialLoginCancelledException implements Exception {
  const SocialLoginCancelledException();
}

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

  @override
  Future<SocialLoginToken> login() async {
    final talkInstalled = !kIsWeb && await isKakaoTalkInstalled();
    debugPrint('[KakaoSocialLoginClient] KakaoTalk installed=$talkInstalled');

    OAuthToken token;
    if (talkInstalled) {
      try {
        token = await UserApi.instance.loginWithKakaoTalk();
      } catch (e) {
        // 사용자 취소면 중단, 그 외(카톡에 카카오계정 미연결 등)는 웹 OAuth로 fallback
        if (_isUserCancelled(e)) throw const SocialLoginCancelledException();
        debugPrint(
            '[KakaoSocialLoginClient] talk login failed, fallback to account: $e');
        token = await _loginWithAccount();
      }
    } else {
      token = await _loginWithAccount();
    }

    return SocialLoginToken(
      provider: provider,
      accessToken: token.accessToken,
    );
  }

  Future<OAuthToken> _loginWithAccount() async {
    try {
      return await UserApi.instance.loginWithKakaoAccount();
    } catch (e) {
      if (_isUserCancelled(e)) throw const SocialLoginCancelledException();
      rethrow;
    }
  }

  static bool _isUserCancelled(Object e) {
    if (e is PlatformException && e.code == 'CANCELED') return true;
    if (e is KakaoAuthException && e.error == AuthErrorCause.accessDenied) {
      return true;
    }
    if (e is KakaoClientException && e.reason == ClientErrorCause.cancelled) {
      return true;
    }
    return false;
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
    // 플러그인이 사용자 취소를 loggedOut 상태로 돌려줌 (error와 구분)
    if (result.status == NaverLoginStatus.loggedOut) {
      throw const SocialLoginCancelledException();
    }
    if (result.status != NaverLoginStatus.loggedIn) {
      throw Exception('네이버 로그인 실패: ${result.errorMessage ?? '알 수 없는 오류'}');
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
      throw const SocialLoginCancelledException();
    }
    final auth = await account.authentication;
    // 백엔드가 userinfo 엔드포인트에 Bearer로 검증하므로 accessToken만 유효 (idToken 불가)
    final token = auth.accessToken;
    if (token == null || token.isEmpty) {
      throw Exception('Google access token을 가져오지 못했습니다.');
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
