package com.tripmate.backend.auth.token;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Service
public class TokenService {

    private final SecretKey key;
    private final long accessExpiryMs;

    public TokenService(@Value("${jwt.secret}") String secret,
                        @Value("${jwt.access-expiry-ms}") long accessExpiryMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpiryMs = accessExpiryMs;
    }

    public String generateAccessToken(String userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessExpiryMs);
        return Jwts.builder()
                .subject(userId)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public Claims validateAndParse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 만료 여부와 무관하게 subject(userId)만 추출 — 만료 토큰 blacklist 처리 등에 사용 */
    public String extractUserIdUnchecked(String token) {
        try {
            return validateAndParse(token).getSubject();
        } catch (ExpiredJwtException e) {
            return e.getClaims().getSubject();
        }
    }

    public LocalDateTime getExpiryAt(String token) {
        Date expiry = validateAndParse(token).getExpiration();
        return expiry.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
}
