package com.railway.booking.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.railway.booking.dto.SeatAvailabilityResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class AvailabilityCache {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.cache.availability-ttl-minutes:5}")
    private long ttlMinutes;

    private static final String PREFIX = "avail:";

    public void put(Long trainRunId, String coachType, SeatAvailabilityResponse availability) {
        String key = buildKey(trainRunId, coachType);
        try {
            String json = objectMapper.writeValueAsString(availability);
            redisTemplate.opsForValue().set(key, json, ttlMinutes, TimeUnit.MINUTES);
        } catch (JsonProcessingException e) {
            log.warn("Failed to cache availability for key {}", key, e);
        }
    }

    public Optional<SeatAvailabilityResponse> get(Long trainRunId, String coachType) {
        String key = buildKey(trainRunId, coachType);
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) return Optional.empty();

        try {
            return Optional.of(objectMapper.readValue(json, SeatAvailabilityResponse.class));
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize availability cache for key {}", key, e);
            return Optional.empty();
        }
    }

    public void evict(Long trainRunId, String coachType) {
        redisTemplate.delete(buildKey(trainRunId, coachType));
    }

    public void evictAllForTrainRun(Long trainRunId) {
        var keys = redisTemplate.keys(PREFIX + trainRunId + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private String buildKey(Long trainRunId, String coachType) {
        return PREFIX + trainRunId + ":" + coachType;
    }
}
