package com.gabojago.global.security.oauth2.apple;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gabojago.global.exception.BusinessException;
import com.gabojago.global.exception.ErrorCode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplePublicKeyProviderTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void fetchesAndCachesApplePublicKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        RSAPublicKey rsaPublicKey = (RSAPublicKey) keyPair.getPublic();
        String response = """
                {"keys":[{"kty":"RSA","kid":"apple-key","alg":"RS256","n":"%s","e":"%s"}]}
                """.formatted(encode(rsaPublicKey.getModulus()), encode(rsaPublicKey.getPublicExponent()));
        AtomicInteger requestCount = new AtomicInteger();
        startServer(200, response, requestCount);
        ApplePublicKeyProvider provider = provider();

        assertThat(provider.get("apple-key")).isEqualTo(keyPair.getPublic());
        assertThat(provider.get("apple-key")).isEqualTo(keyPair.getPublic());
        assertThat(requestCount).hasValue(1);
    }

    @Test
  void mapsAppleKeyServerFailureToBadGatewayError() throws Exception {
        startServer(503, "unavailable", new AtomicInteger());
        ApplePublicKeyProvider provider = provider();

        assertThatThrownBy(() -> provider.get("apple-key"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.OAUTH_USERINFO_FAILED));
  }

  @Test
  void doesNotRefetchImmediatelyForAnUnknownKeyId() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    RSAPublicKey publicKey = (RSAPublicKey) generator.generateKeyPair().getPublic();
    String response =
        """
                {"keys":[{"kty":"RSA","kid":"known-key","alg":"RS256","n":"%s","e":"%s"}]}
                """
            .formatted(encode(publicKey.getModulus()), encode(publicKey.getPublicExponent()));
    AtomicInteger requestCount = new AtomicInteger();
    startServer(200, response, requestCount);
    ApplePublicKeyProvider provider = provider();

    assertThatThrownBy(() -> provider.get("unknown-key")).isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> provider.get("unknown-key")).isInstanceOf(BusinessException.class);
    assertThat(requestCount).hasValue(1);
  }

    private ApplePublicKeyProvider provider() {
        return new ApplePublicKeyProvider(
                new ObjectMapper(),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/auth/keys"),
                Duration.ofSeconds(1),
                Duration.ofHours(24),
                Clock.fixed(Instant.parse("2026-09-07T00:00:00Z"), ZoneOffset.UTC));
    }

    private void startServer(int status, String body, AtomicInteger requestCount) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/auth/keys", exchange -> {
            requestCount.incrementAndGet();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    private static String encode(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = java.util.Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
