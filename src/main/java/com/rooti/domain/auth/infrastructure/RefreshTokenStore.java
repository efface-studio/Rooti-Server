package com.rooti.domain.auth.infrastructure;

import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed refresh-token store.
 *
 * <p>The previous Django implementation kept refresh tokens client-side only, which made
 * "log me out of every device" impossible. Storing them server-side gives us:
 *
 * <ul>
 *   <li>O(1) revocation per user (delete a key)
 *   <li>Detection of refresh-token replay (token in request must equal stored token)
 *   <li>Native TTL — Redis evicts expired entries for us
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenStore {

    private static final String KEY_PREFIX = "rooti:refresh:";

    private final RedisTemplate<String, Object> redisTemplate;

    private String key(long userId) {
        return KEY_PREFIX + userId;
    }

    public void save(long userId, String token, Duration ttl) {
        redisTemplate.opsForValue().set(key(userId), token, ttl);
    }

    public Optional<String> find(long userId) {
        Object value = redisTemplate.opsForValue().get(key(userId));
        return Optional.ofNullable(value).map(Object::toString);
    }

    public void remove(long userId) {
        redisTemplate.delete(key(userId));
    }

    public boolean matches(long userId, String token) {
        return find(userId).map(stored -> stored.equals(token)).orElse(false);
    }
}
