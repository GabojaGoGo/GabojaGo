package com.gabojago.global.security.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String ACTIVE_KEY_PREFIX = "auth:refresh:active:";
    private static final String REVOKED_KEY_PREFIX = "auth:refresh:revoked:";
    private static final String FAMILY_KEY_PREFIX = "auth:refresh:family:";
    private static final String USER_KEY_PREFIX = "auth:refresh:user:";

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${jwt.refresh-expiry-ms}")
    private long refreshExpiryMs;

    /** refresh token hash를 활성 record로 저장하고 family/user 역인덱스도 함께 기록한다. */
    @Override
    public void saveActive(String tokenHash, RefreshTokenRecord record, Duration ttl) {
        String activeKey = activeKey(tokenHash);
        String familyKey = familyKey(record.familyId());
        String userKey = userKey(record.userId());

        executeTransaction(operations -> {
            operations.opsForHash().putAll(activeKey, toMap(record));
            operations.expire(activeKey, ttl);
            operations.opsForSet().add(familyKey, tokenHash);
            operations.expire(familyKey, ttl);
            operations.opsForSet().add(userKey, record.familyId());
            operations.expire(userKey, ttl);
        });
    }

    /** 아직 사용 가능한 refresh token record를 조회한다. */
    @Override
    public Optional<RefreshTokenRecord> findActive(String tokenHash) {
        return findRecord(activeKey(tokenHash));
    }

    /** 이미 회전/폐기된 refresh token record를 조회해 재사용 공격 탐지에 사용한다. */
    @Override
    public Optional<RefreshTokenRecord> findRevoked(String tokenHash) {
        return findRecord(revokedKey(tokenHash));
    }

    /** 활성 token을 revoked 영역으로 옮기고 active key는 삭제한다. */
    @Override
    public void revoke(String tokenHash, RefreshTokenRecord record, String reason) {
        String activeKey = activeKey(tokenHash);
        String revokedKey = revokedKey(tokenHash);
        Duration ttl = revokedTtl(activeKey);

        executeTransaction(operations -> {
            operations.opsForHash().putAll(revokedKey, Map.of(
                "userId", nullToEmpty(record.userId()),
                "familyId", nullToEmpty(record.familyId()),
                "reason", reason
            ));
            operations.expire(revokedKey, ttl);
            operations.delete(activeKey);
        });
    }

    /** 같은 refresh token family에 속한 모든 활성 token을 폐기한다. */
    @Override
    public void revokeFamily(String familyId, String reason) {
        Set<String> tokenHashes = redisTemplate.opsForSet().members(familyKey(familyId));
        if (tokenHashes == null) {
            return;
        }

        tokenHashes.forEach(tokenHash ->
                findActive(tokenHash).ifPresent(record -> revoke(tokenHash, record, reason)));
        redisTemplate.delete(familyKey(familyId));
    }

    /** 특정 사용자의 모든 refresh token family를 폐기한다. */
    @Override
    public void revokeAllForUser(Long userId, String reason) {
        Set<String> familyIds = redisTemplate.opsForSet().members(userKey(userId));
        if (familyIds == null) {
            return;
        }

        familyIds.forEach(familyId -> revokeFamily(familyId, reason));
        redisTemplate.delete(userKey(userId));
    }

    /** Redis hash 값을 RefreshTokenRecord 객체로 복원한다. */
    private Optional<RefreshTokenRecord> findRecord(String key) {
        Map<Object, Object> values = redisTemplate.opsForHash().entries(key);
        if (values.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new RefreshTokenRecord(
                parseUserId((String) values.get("userId")),
                (String) values.get("familyId")
        ));
    }

    /** RefreshTokenRecord를 Redis hash 저장용 문자열 map으로 변환한다. */
    private static Map<String, String> toMap(RefreshTokenRecord record) {
        return Map.of(
                "userId", String.valueOf(record.userId()),
                "familyId", record.familyId()
        );
    }

    /** Redis MULTI/EXEC 보일러플레이트를 숨기고 실제 저장 명령만 호출부에 남긴다. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void executeTransaction(Consumer<RedisOperations<String, String>> action) {
        redisTemplate.execute(new SessionCallback<List<Object>>() {
            @Override
            public List<Object> execute(RedisOperations operations) {
                RedisOperations<String, String> stringOperations = operations;
                stringOperations.multi();
                action.accept(stringOperations);
                return stringOperations.exec();
            }
        });
    }

    /** 단일 활성 refresh token 저장 key. */
    private static String activeKey(String tokenHash) {
        return ACTIVE_KEY_PREFIX + tokenHash;
    }

    /** 회전/폐기된 refresh token 저장 key. */
    private static String revokedKey(String tokenHash) {
        return REVOKED_KEY_PREFIX + tokenHash;
    }

    /** refresh token rotation family에 속한 token hash 목록 key. */
    private static String familyKey(String familyId) {
        return FAMILY_KEY_PREFIX + familyId;
    }

    /** 사용자별 refresh token family 목록 key. */
    private static String userKey(Long userId) {
        return USER_KEY_PREFIX + userId;
    }

    /** Redis hash에는 null을 저장할 수 없어 빈 문자열로 치환한다. */
    private static String nullToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** revoked token도 반드시 TTL을 갖도록 active TTL이 없으면 refresh 기본 만료 시간을 사용한다. */
    private Duration revokedTtl(String activeKey) {
        Long remainingSeconds = redisTemplate.getExpire(activeKey);
        if (remainingSeconds != null && remainingSeconds > 0) {
            return Duration.ofSeconds(remainingSeconds);
        }
        return Duration.ofMillis(refreshExpiryMs);
    }

    /** Redis에는 문자열로 저장된 userId를 Long PK로 복원한다. */
    private static Long parseUserId(String userId) {
        return Long.valueOf(userId);
    }
}
