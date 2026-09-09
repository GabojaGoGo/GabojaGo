import 'dart:convert';

import 'package:crypto/crypto.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sign_in_with_apple/sign_in_with_apple.dart';
import 'package:tripmate/core/services/social_login_clients.dart';

void main() {
  const credential = AuthorizationCredentialAppleID(
    userIdentifier: 'apple-user',
    givenName: null,
    familyName: null,
    authorizationCode: 'authorization-code',
    email: 'user@example.com',
    identityToken: 'identity-token',
    state: null,
  );

  test('identity token과 replay 방지용 nonce를 반환한다', () async {
    String? requestedNonce;
    final client = AppleSocialLoginClient(
      requestCredential: (hashedNonce) async {
        requestedNonce = hashedNonce;
        return credential;
      },
    );

    final token = await client.login();

    expect(token.provider, SocialLoginProvider.apple);
    expect(token.accessToken, 'identity-token');
    expect(token.nonce, isNotNull);
    expect(
      requestedNonce,
      sha256.convert(utf8.encode(token.nonce!)).toString(),
    );
  });

  test('Apple 인증 취소는 사용자 취소로 분류한다', () async {
    final client = AppleSocialLoginClient(
      requestCredential: (_) =>
          throw const SignInWithAppleAuthorizationException(
            code: AuthorizationErrorCode.canceled,
            message: 'cancelled',
          ),
    );

    await expectLater(
      client.login(),
      throwsA(isA<SocialLoginCancelledException>()),
    );
  });

  test('identity token이 없으면 SDK 실패로 분류한다', () async {
    final client = AppleSocialLoginClient(
      requestCredential: (_) async => const AuthorizationCredentialAppleID(
        userIdentifier: 'apple-user',
        givenName: null,
        familyName: null,
        authorizationCode: 'authorization-code',
        email: null,
        identityToken: null,
        state: null,
      ),
    );

    await expectLater(
      client.login(),
      throwsA(
        isA<SocialLoginFailure>()
            .having(
              (error) => error.code,
              'code',
              SocialLoginFailureCode.providerSdk,
            )
            .having(
              (error) => error.causeType,
              'causeType',
              'missing_identity_token',
            ),
      ),
    );
  });
}
