package com.railway.booking.ratelimit;

import com.railway.common.exception.BusinessException;
import com.railway.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate redisTemplate;

    private static final String SLIDING_WINDOW_SCRIPT =
            "local key = KEYS[1] " +
            "local now = tonumber(ARGV[1]) " +
            "local window = tonumber(ARGV[2]) " +
            "local limit = tonumber(ARGV[3]) " +
            "redis.call('ZREMRANGEBYSCORE', key, 0, now - window * 1000) " +
            "local count = redis.call('ZCARD', key) " +
            "if count < limit then " +
            "  redis.call('ZADD', key, now, now .. '-' .. math.random(1000000)) " +
            "  redis.call('EXPIRE', key, window) " +
            "  return limit - count - 1 " +
            "end " +
            "return -1";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        if (!(handler instanceof HandlerMethod method)) return true;

        RateLimit rateLimit = method.getMethodAnnotation(RateLimit.class);
        if (rateLimit == null) return true;

        String userId = resolveUserId();
        String key = "rate-limit:" + rateLimit.keyPrefix() + ":" + userId;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(SLIDING_WINDOW_SCRIPT, Long.class);
        Long remaining = redisTemplate.execute(script,
                List.of(key),
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(rateLimit.windowSeconds()),
                String.valueOf(rateLimit.requests()));

        if (remaining != null && remaining >= 0) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
            response.setHeader("X-RateLimit-Limit", String.valueOf(rateLimit.requests()));
            return true;
        }

        log.warn("Rate limit exceeded for user {} on {}", userId, rateLimit.keyPrefix());
        throw new BusinessException("RATE_LIMIT_EXCEEDED",
                "Too many requests. Please try again later.");
    }

    private String resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return String.valueOf(user.getId());
        }
        return "anonymous";
    }
}
