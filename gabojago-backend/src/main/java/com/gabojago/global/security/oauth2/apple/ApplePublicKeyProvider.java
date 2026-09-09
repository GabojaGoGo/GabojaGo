package com.gabojago.global.security.oauth2.apple;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gabojago.global.exception.BusinessException;
import com.gabojago.global.exception.ErrorCode;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ApplePublicKeyProvider {

  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private final URI keysUri;
  private final Duration requestTimeout;
  private final Duration cacheDuration;
  private final Clock clock;
  private volatile KeyCache cache = new KeyCache(Map.of(), Instant.EPOCH, Instant.EPOCH);

  public ApplePublicKeyProvider(
      ObjectMapper objectMapper,
      @Value("${apple.keys-url}") String keysUrl,
      @Value("${apple.connect-timeout-ms}") long connectTimeoutMs,
      @Value("${apple.request-timeout-ms}") long requestTimeoutMs,
      @Value("${apple.keys-cache-seconds}") long cacheSeconds) {
    this(
        objectMapper,
        HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectTimeoutMs)).build(),
        URI.create(keysUrl),
        Duration.ofMillis(requestTimeoutMs),
        Duration.ofSeconds(cacheSeconds),
        Clock.systemUTC());
  }

  ApplePublicKeyProvider(
      ObjectMapper objectMapper,
      HttpClient httpClient,
      URI keysUri,
      Duration requestTimeout,
      Duration cacheDuration,
      Clock clock) {
    this.objectMapper = objectMapper;
    this.httpClient = httpClient;
    this.keysUri = keysUri;
    this.requestTimeout = requestTimeout;
    this.cacheDuration = cacheDuration;
    this.clock = clock;
  }

  public PublicKey get(String keyId) {
    KeyCache snapshot = cache;
    PublicKey cachedKey = snapshot.keys().get(keyId);
    Instant now = clock.instant();
    if (cachedKey != null && snapshot.expiresAt().isAfter(now)) {
      return cachedKey;
    }
    if (snapshot.expiresAt().isAfter(now) && snapshot.nextRefreshAt().isAfter(now)) {
      throw keyNotFound();
    }

    synchronized (this) {
      snapshot = cache;
      cachedKey = snapshot.keys().get(keyId);
      now = clock.instant();
      if (cachedKey != null && snapshot.expiresAt().isAfter(now)) {
        return cachedKey;
      }
      if (snapshot.expiresAt().isAfter(now) && snapshot.nextRefreshAt().isAfter(now)) {
        throw keyNotFound();
      }

      Map<String, PublicKey> refreshedKeys = fetchKeys();
      cache = new KeyCache(refreshedKeys, now.plus(cacheDuration), now.plus(Duration.ofMinutes(1)));
      PublicKey refreshedKey = refreshedKeys.get(keyId);
      if (refreshedKey == null) {
        throw keyNotFound();
      }
      return refreshedKey;
    }
  }

  private static BusinessException keyNotFound() {
    return new BusinessException(
        ErrorCode.OAUTH_TOKEN_INVALID, "Apple identity token key id not found");
  }

  private Map<String, PublicKey> fetchKeys() {
    try {
      HttpRequest request = HttpRequest.newBuilder(keysUri).timeout(requestTimeout).GET().build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IllegalStateException(
            "Apple public keys response status=" + response.statusCode());
      }

      AppleKeysResponse keysResponse =
          objectMapper.readValue(response.body(), AppleKeysResponse.class);
      return keysResponse.keys().stream()
          .filter(key -> "RSA".equals(key.kty()) && "RS256".equals(key.alg()))
          .collect(
              Collectors.toUnmodifiableMap(AppleKey::kid, ApplePublicKeyProvider::toPublicKey));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new BusinessException(
          ErrorCode.OAUTH_USERINFO_FAILED, "Apple public key request interrupted", e);
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      throw new BusinessException(
          ErrorCode.OAUTH_USERINFO_FAILED, "Apple public key request failed", e);
    }
  }

  private static PublicKey toPublicKey(AppleKey key) {
    try {
      Base64.Decoder decoder = Base64.getUrlDecoder();
      BigInteger modulus = new BigInteger(1, decoder.decode(key.n()));
      BigInteger exponent = new BigInteger(1, decoder.decode(key.e()));
      return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent));
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid Apple public key", e);
    }
  }

  private record KeyCache(Map<String, PublicKey> keys, Instant expiresAt, Instant nextRefreshAt) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record AppleKeysResponse(List<AppleKey> keys) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record AppleKey(String kty, String kid, String alg, String n, String e) {}
}
