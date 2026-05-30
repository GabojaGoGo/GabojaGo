package com.gabojago.global.security.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RedisJwtTokenBlacklist {

    // 로그아웃된 access token만 만료 시각까지 차단한다.
    private static final String KEY_PREFIX = "auth:access:blacklist:";

    private final RedisTemplate<String, String> redisTemplate;

    public void add(String token, Duration ttl) {
        // 이미 만료된 토큰은 Redis에 남길 필요가 없다.
        if (token == null || token.isBlank() || ttl == null || ttl.isZero() || ttl.isNegative()) {
            return;
        }
        redisTemplate.opsForValue().set(key(token), "1", ttl);
    }

    public boolean isBlackListed(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(token)));
    }

    private static String key(String token) {
        // Redis에 raw JWT를 저장하지 않기 위해 hash를 key로 사용한다.
        return KEY_PREFIX + TokenHasher.sha256(token);
    }
}
