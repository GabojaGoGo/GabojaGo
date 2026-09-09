package com.gabojago.global.security.oauth2.apple;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gabojago.global.exception.BusinessException;
import com.gabojago.global.exception.ErrorCode;
import com.gabojago.global.security.oauth2.OAuth2UserInfo;
import com.gabojago.member.user.enums.OAuthProvider;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppleOAuthProviderClientTest {

    private static final String CLIENT_ID = "com.gabojago.app";
    private static final String KEY_ID = "apple-key";
    private static final String RAW_NONCE = "raw-nonce";

    private ApplePublicKeyProvider publicKeyProvider;
    private AppleOAuthProviderClient client;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        publicKeyProvider = mock(ApplePublicKeyProvider.class);
        when(publicKeyProvider.get(KEY_ID)).thenReturn(keyPair.getPublic());
        client = new AppleOAuthProviderClient(new ObjectMapper(), publicKeyProvider, CLIENT_ID);
    }

    @Test
    void validatesIdentityTokenAndMapsUserInfo() throws Exception {
        String token = identityToken(CLIENT_ID, sha256(RAW_NONCE), Instant.now().plusSeconds(300));

        OAuth2UserInfo result = client.getUserInfo(token, RAW_NONCE);

        assertThat(result.provider()).isEqualTo(OAuthProvider.APPLE);
        assertThat(result.providerUserId()).isEqualTo("apple-user");
        assertThat(result.email()).isEqualTo("user@example.com");
        assertThat(result.nickname()).isNull();
    }

    @Test
    void rejectsNonceMismatch() throws Exception {
        String token = identityToken(CLIENT_ID, sha256("different-nonce"), Instant.now().plusSeconds(300));

        assertInvalidToken(() -> client.getUserInfo(token, RAW_NONCE));
    }

    @Test
    void rejectsWrongAudience() throws Exception {
        String token = identityToken("another-client", sha256(RAW_NONCE), Instant.now().plusSeconds(300));

        assertInvalidToken(() -> client.getUserInfo(token, RAW_NONCE));
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        String token = identityToken(CLIENT_ID, sha256(RAW_NONCE), Instant.now().minusSeconds(60));

        assertInvalidToken(() -> client.getUserInfo(token, RAW_NONCE));
    }

    private String identityToken(String audience, String nonce, Instant expiration) {
        return Jwts.builder()
                .header().keyId(KEY_ID).and()
                .issuer("https://appleid.apple.com")
                .audience().add(audience).and()
                .subject("apple-user")
                .claim("email", "user@example.com")
                .claim("nonce", nonce)
                .issuedAt(Date.from(Instant.now().minusSeconds(10)))
                .expiration(Date.from(expiration))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return java.util.HexFormat.of().formatHex(digest);
    }

    private static void assertInvalidToken(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action)
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.OAUTH_TOKEN_INVALID));
    }
}
