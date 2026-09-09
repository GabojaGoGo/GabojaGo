package com.gabojago.global.security.oauth2.apple;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gabojago.global.exception.BusinessException;
import com.gabojago.global.exception.ErrorCode;
import com.gabojago.global.security.oauth2.OAuth2UserInfo;
import com.gabojago.global.security.oauth2.OAuthProviderClient;
import com.gabojago.member.user.enums.OAuthProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AppleOAuthProviderClient implements OAuthProviderClient {

  private static final String APPLE_ISSUER = "https://appleid.apple.com";
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final ObjectMapper objectMapper;
  private final ApplePublicKeyProvider publicKeyProvider;
  private final String clientId;

  public AppleOAuthProviderClient(
      ObjectMapper objectMapper,
      ApplePublicKeyProvider publicKeyProvider,
      @Value("${apple.client-id}") String clientId) {
    this.objectMapper = objectMapper;
    this.publicKeyProvider = publicKeyProvider;
    this.clientId = clientId;
  }

  @Override
  public OAuthProvider provider() {
    return OAuthProvider.APPLE;
  }

  @Override
  public OAuth2UserInfo getUserInfo(String identityToken) {
    return getUserInfo(identityToken, null);
  }

  @Override
  public OAuth2UserInfo getUserInfo(String identityToken, String rawNonce) {
    try {
      if (rawNonce == null || rawNonce.isBlank()) {
        throw invalidToken("Apple login nonce is required", null);
      }

      Map<String, Object> header = parseHeader(identityToken);
      if (!"RS256".equals(header.get("alg"))) {
        throw invalidToken("Apple identity token algorithm is invalid", null);
      }
      Object keyId = header.get("kid");
      if (!(keyId instanceof String kid) || kid.isBlank()) {
        throw invalidToken("Apple identity token key id is missing", null);
      }

      PublicKey publicKey = publicKeyProvider.get(kid);
      Claims claims =
          Jwts.parser()
              .verifyWith(publicKey)
              .requireIssuer(APPLE_ISSUER)
              .requireAudience(clientId)
              .build()
              .parseSignedClaims(identityToken)
              .getPayload();

      String expectedNonce = sha256(rawNonce);
      if (!MessageDigest.isEqual(
          expectedNonce.getBytes(StandardCharsets.UTF_8),
          String.valueOf(claims.get("nonce")).getBytes(StandardCharsets.UTF_8))) {
        throw invalidToken("Apple identity token nonce is invalid", null);
      }

      String providerUserId = claims.getSubject();
      if (providerUserId == null || providerUserId.isBlank()) {
        throw invalidToken("Apple identity token subject is missing", null);
      }
      return new OAuth2UserInfo(
          OAuthProvider.APPLE, providerUserId, claims.get("email", String.class), null);
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      throw invalidToken("Apple identity token validation failed", e);
    }
  }

  @Override
  public void unlink(String providerUserId) {
    // Apple token revocation requires the one-time authorization code and client secret.
  }

  private Map<String, Object> parseHeader(String identityToken) throws Exception {
    String[] tokenParts = identityToken.split("\\.");
    if (tokenParts.length != 3) {
      throw new IllegalArgumentException("Malformed Apple identity token");
    }
    byte[] decoded = Base64.getUrlDecoder().decode(tokenParts[0]);
    return objectMapper.readValue(decoded, MAP_TYPE);
  }

  private static String sha256(String value) throws Exception {
    byte[] digest =
        MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    return java.util.HexFormat.of().formatHex(digest);
  }

  private static BusinessException invalidToken(String detail, Throwable cause) {
    return new BusinessException(ErrorCode.OAUTH_TOKEN_INVALID, detail, cause);
  }
}
