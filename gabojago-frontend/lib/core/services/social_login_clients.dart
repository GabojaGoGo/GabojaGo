import 'dart:convert';
import 'dart:math';

import 'package:crypto/crypto.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart' show PlatformException;
import 'package:flutter_dotenv/flutter_dotenv.dart';
import 'package:flutter_naver_login/flutter_naver_login.dart';
import 'package:flutter_naver_login/interface/types/naver_login_status.dart';
import 'package:google_sign_in/google_sign_in.dart';
import 'package:kakao_flutter_sdk_user/kakao_flutter_sdk_user.dart';
import 'package:sign_in_with_apple/sign_in_with_apple.dart';

/// 사용자가 로그인 도중 직접 취소한 경우 — 에러 UI 없이 조용히 복귀해야 함
class SocialLoginCancelledException implements Exception {
  const SocialLoginCancelledException();
}

/// 소셜 로그인 실패를 원인별로 분류한다.
/// token·응답 본문은 절대 이 객체나 로그에 담지 않는다.
enum SocialLoginFailureCode {
  providerSdk,
  providerTokenRejected,
  providerUnavailable,
  backendUnavailable,
  requestTimeout,
  unexpected,
}

class SocialLoginFailure implements Exception {
  const SocialLoginFailure({
    required this.provider,
    required this.code,
    required this.causeType,
  });

  final SocialLoginProvider provider;
  final SocialLoginFailureCode code;
  final String causeType;

  String get userMessage => switch (code) {
    SocialLoginFailureCode.providerSdk =>
      '${provider.label} 앱 인증을 완료하지 못했어요. 설정을 확인한 뒤 다시 시도해 주세요.',
    SocialLoginFailureCode.providerTokenRejected =>
      '${provider.label} 인증 정보가 만료되었거나 유효하지 않아요. 다시 로그인해 주세요.',
    SocialLoginFailureCode.providerUnavailable =>
      '${provider.label} 인증 서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.',
    SocialLoginFailureCode.backendUnavailable =>
      '로그인 서버가 일시적으로 응답하지 않아요. 잠시 후 다시 시도해 주세요.',
    SocialLoginFailureCode.requestTimeout =>
      '로그인 요청 시간이 초과됐어요. 네트워크를 확인한 뒤 다시 시도해 주세요.',
    SocialLoginFailureCode.unexpected =>
      '${provider.label} 로그인을 완료하지 못했어요. 잠시 후 다시 시도해 주세요.',
  };

  String get logLabel =>
      'provider=${provider.apiValue}, code=${code.name}, cause=$causeType';
}

enum SocialLoginProvider {
  kakao('KAKAO', '카카오'),
  naver('NAVER', '네이버'),
  google('GOOGLE', '구글'),
  apple('APPLE', 'Apple');

  const SocialLoginProvider(this.apiValue, this.label);

  final String apiValue;
  final String label;

  static SocialLoginProvider? fromApiValue(String? value) {
    for (final provider in values) {
      if (provider.apiValue == value) return provider;
    }
    return null;
  }
}

class SocialLoginToken {
  const SocialLoginToken({
    required this.provider,
    required this.accessToken,
    this.nonce,
  });

  final SocialLoginProvider provider;
  final String accessToken;
  final String? nonce;
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
    try {
      final talkInstalled = !kIsWeb && await isKakaoTalkInstalled();
      debugPrint('[KakaoSocialLoginClient] KakaoTalk installed=$talkInstalled');

      OAuthToken token;
      if (talkInstalled) {
        try {
          token = await UserApi.instance.loginWithKakaoTalk();
        } catch (error) {
          // 사용자 취소면 중단, 그 외(카톡에 카카오계정 미연결 등)는 웹 OAuth로 fallback
          if (_isUserCancelled(error)) {
            throw const SocialLoginCancelledException();
          }
          debugPrint(
            '[KakaoSocialLoginClient] talk login fallback: ${error.runtimeType}',
          );
          token = await _loginWithAccount();
        }
      } else {
        token = await _loginWithAccount();
      }

      return SocialLoginToken(
        provider: provider,
        accessToken: token.accessToken,
      );
    } on SocialLoginCancelledException {
      rethrow;
    } on SocialLoginFailure {
      rethrow;
    } catch (error) {
      throw SocialLoginFailure(
        provider: provider,
        code: SocialLoginFailureCode.providerSdk,
        causeType: error.runtimeType.toString(),
      );
    }
  }

  Future<OAuthToken> _loginWithAccount() async {
    try {
      return await UserApi.instance.loginWithKakaoAccount();
    } catch (e) {
      if (_isUserCancelled(e)) throw const SocialLoginCancelledException();
      throw SocialLoginFailure(
        provider: provider,
        code: SocialLoginFailureCode.providerSdk,
        causeType: _kakaoFailureType(e),
      );
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

  static String _kakaoFailureType(Object error) {
    if (error is KakaoAuthException) {
      return 'KakaoAuthException.${error.error.name}';
    }
    if (error is KakaoClientException) {
      return 'KakaoClientException.${error.reason.name}';
    }
    if (error is PlatformException) {
      return 'PlatformException.${error.code}';
    }
    return error.runtimeType.toString();
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
    try {
      final result = await FlutterNaverLogin.logIn();
      // 플러그인이 사용자 취소를 loggedOut 상태로 돌려줌 (error와 구분)
      if (result.status == NaverLoginStatus.loggedOut) {
        throw const SocialLoginCancelledException();
      }
      if (result.status != NaverLoginStatus.loggedIn) {
        final message = result.errorMessage ?? '';
        if (message.toLowerCase().contains('cancel')) {
          throw const SocialLoginCancelledException();
        }
        throw SocialLoginFailure(
          provider: provider,
          code: SocialLoginFailureCode.providerSdk,
          causeType: 'NaverLoginStatus.${result.status.name}',
        );
      }
      final token = await FlutterNaverLogin.getCurrentAccessToken();
      if (!token.isValid() || token.accessToken.isEmpty) {
        throw SocialLoginFailure(
          provider: provider,
          code: SocialLoginFailureCode.providerSdk,
          causeType: 'missing_access_token',
        );
      }
      return SocialLoginToken(
        provider: provider,
        accessToken: token.accessToken,
      );
    } on SocialLoginCancelledException {
      rethrow;
    } on SocialLoginFailure {
      rethrow;
    } catch (error) {
      throw SocialLoginFailure(
        provider: provider,
        code: SocialLoginFailureCode.providerSdk,
        causeType: error.runtimeType.toString(),
      );
    }
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
    try {
      final account = await _googleSignIn.signIn();
      if (account == null) {
        throw const SocialLoginCancelledException();
      }
      final auth = await account.authentication;
      // 백엔드가 userinfo 엔드포인트에 Bearer로 검증하므로 accessToken만 유효 (idToken 불가)
      final token = auth.accessToken;
      if (token == null || token.isEmpty) {
        throw SocialLoginFailure(
          provider: provider,
          code: SocialLoginFailureCode.providerSdk,
          causeType: 'missing_access_token',
        );
      }
      return SocialLoginToken(provider: provider, accessToken: token);
    } on SocialLoginCancelledException {
      rethrow;
    } on SocialLoginFailure {
      rethrow;
    } catch (error) {
      throw SocialLoginFailure(
        provider: provider,
        code: SocialLoginFailureCode.providerSdk,
        causeType: error.runtimeType.toString(),
      );
    }
  }

  @override
  Future<void> logout() async {
    try {
      await _googleSignIn.signOut();
    } catch (_) {}
  }
}

typedef AppleCredentialRequester =
    Future<AuthorizationCredentialAppleID> Function(String hashedNonce);

class AppleSocialLoginClient implements SocialLoginClient {
  AppleSocialLoginClient({AppleCredentialRequester? requestCredential})
    : _requestCredential = requestCredential ?? _requestAppleCredential;

  final AppleCredentialRequester _requestCredential;

  @override
  SocialLoginProvider get provider => SocialLoginProvider.apple;

  @override
  Future<SocialLoginToken> login() async {
    final rawNonce = _generateNonce();
    final hashedNonce = sha256.convert(utf8.encode(rawNonce)).toString();

    try {
      final credential = await _requestCredential(hashedNonce);
      final identityToken = credential.identityToken;
      if (identityToken == null || identityToken.isEmpty) {
        throw SocialLoginFailure(
          provider: provider,
          code: SocialLoginFailureCode.providerSdk,
          causeType: 'missing_identity_token',
        );
      }
      return SocialLoginToken(
        provider: provider,
        accessToken: identityToken,
        nonce: rawNonce,
      );
    } on SignInWithAppleAuthorizationException catch (error) {
      if (error.code == AuthorizationErrorCode.canceled) {
        throw const SocialLoginCancelledException();
      }
      throw SocialLoginFailure(
        provider: provider,
        code: SocialLoginFailureCode.providerSdk,
        causeType: 'AppleAuthorizationException.${error.code.name}',
      );
    } on SocialLoginFailure {
      rethrow;
    } catch (error) {
      throw SocialLoginFailure(
        provider: provider,
        code: SocialLoginFailureCode.providerSdk,
        causeType: error.runtimeType.toString(),
      );
    }
  }

  @override
  Future<void> logout() async {
    // AuthenticationServices에는 앱이 종료할 Apple ID 세션이 없다.
  }

  static Future<AuthorizationCredentialAppleID> _requestAppleCredential(
    String hashedNonce,
  ) {
    return SignInWithApple.getAppleIDCredential(
      scopes: const [
        AppleIDAuthorizationScopes.email,
        AppleIDAuthorizationScopes.fullName,
      ],
      nonce: hashedNonce,
    );
  }

  static String _generateNonce() {
    final random = Random.secure();
    final bytes = List<int>.generate(32, (_) => random.nextInt(256));
    return base64Url.encode(bytes).replaceAll('=', '');
  }
}
