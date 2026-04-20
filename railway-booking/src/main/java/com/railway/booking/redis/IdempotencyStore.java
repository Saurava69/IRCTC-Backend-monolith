package com.railway.booking.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyStore {

    private final StringRedisTemplate redisTemplate;

    private static final String PREFIX = "idempotency:";
    private static final long TTL_HOURS = 24;

    public enum Status { PROCESSING, COMPLETED }

    public boolean tryAcquire(String key) {
        Boolean set = redisTemplate.opsForValue()
                .setIfAbsent(PREFIX + key, Status.PROCESSING.name(), TTL_HOURS, TimeUnit.HOURS);
        return Boolean.TRUE.equals(set);
    }

    public void markCompleted(String key, String responseJson) {
        redisTemplate.opsForValue().set(PREFIX + key,
                Status.COMPLETED.name() + "|" + responseJson, TTL_HOURS, TimeUnit.HOURS);
    }

    public void remove(String key) {
        redisTemplate.delete(PREFIX + key);
    }

    public Optional<String> getCompletedResponse(String key) {
        String val = redisTemplate.opsForValue().get(PREFIX + key);
        if (val == null) return Optional.empty();

        if (val.startsWith(Status.COMPLETED.name() + "|")) {
            return Optional.of(val.substring(Status.COMPLETED.name().length() + 1));
        }
        return Optional.empty();
    }

    public boolean isProcessing(String key) {
        String val = redisTemplate.opsForValue().get(PREFIX + key);
        return Status.PROCESSING.name().equals(val);
    }
}
