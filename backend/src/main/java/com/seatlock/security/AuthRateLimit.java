package com.seatlock.security;

import com.seatlock.shared.ApiError;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class AuthRateLimit {
    private static final DefaultRedisScript<Long> COUNT = new DefaultRedisScript<>("""
        local n = redis.call('INCR', KEYS[1])
        if n == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
        return n
        """, Long.class);
    private final StringRedisTemplate redis;
    private final boolean enabled;

    public AuthRateLimit(StringRedisTemplate redis, @Value("${AUTH_RATE_LIMIT:false}") boolean enabled) {
        this.redis = redis;
        this.enabled = enabled;
    }

    public void check(String address) {
        if (!enabled) return;
        try {
            Long requests = redis.execute(COUNT, java.util.List.of("seatlock:auth:" + address),
                Long.toString(Duration.ofMinutes(1).toSeconds()));
            if (requests != null && requests > 60) {
                throw new ApiError(HttpStatus.TOO_MANY_REQUESTS,"RATE_LIMITED","Try again in a minute.");
            }
        } catch (org.springframework.data.redis.RedisConnectionFailureException error) {
            throw new ApiError(HttpStatus.SERVICE_UNAVAILABLE,"AUTH_UNAVAILABLE","Sign-in is temporarily unavailable.");
        }
    }
}
