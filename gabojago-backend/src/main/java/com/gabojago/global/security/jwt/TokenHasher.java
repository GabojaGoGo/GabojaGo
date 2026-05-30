package com.gabojago.global.security.jwt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * raw token을 Redis에 평문으로 저장하지 않기 위한 SHA-256 해시 유틸.
 * refresh token / access token blacklist 양쪽에서 동일하게 사용한다.
 */
public final class TokenHasher {

    private TokenHasher() {
    }

    public static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다", e);
        }
    }
}
