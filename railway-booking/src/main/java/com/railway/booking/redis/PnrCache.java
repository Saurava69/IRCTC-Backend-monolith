package com.railway.booking.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.railway.booking.dto.PnrStatusResponse;
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
public class PnrCache {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.cache.pnr-ttl-minutes:15}")
    private long ttlMinutes;

    private static final String PREFIX = "pnr:";

    public void put(String pnr, PnrStatusResponse status) {
        try {
            String json = objectMapper.writeValueAsString(status);
            redisTemplate.opsForValue().set(PREFIX + pnr, json, ttlMinutes, TimeUnit.MINUTES);
        } catch (JsonProcessingException e) {
            log.warn("Failed to cache PNR status for {}", pnr, e);
        }
    }

    public Optional<PnrStatusResponse> get(String pnr) {
        String json = redisTemplate.opsForValue().get(PREFIX + pnr);
        if (json == null) return Optional.empty();

        try {
            return Optional.of(objectMapper.readValue(json, PnrStatusResponse.class));
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize PNR cache for {}", pnr, e);
            return Optional.empty();
        }
    }

    public void evict(String pnr) {
        redisTemplate.delete(PREFIX + pnr);
    }
}
