package com.railway.booking.redis;

import com.railway.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class SeatLockManager {

    private final StringRedisTemplate redisTemplate;

    @Value("${app.booking.seat-lock-ttl-seconds:600}")
    private long lockTtlSeconds;

    private static final String LOCK_PREFIX = "seat-lock:";
    private static final String AVAIL_PREFIX = "seat-avail:";

    private static final String LOCK_SCRIPT =
            "local available = tonumber(redis.call('GET', KEYS[2]) or '-1') " +
            "if available == -1 then return -1 end " +
            "local requested = tonumber(ARGV[1]) " +
            "if available >= requested then " +
            "  redis.call('DECRBY', KEYS[2], requested) " +
            "  redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3]) " +
            "  return 1 " +
            "end " +
            "return 0";

    private static final String UNLOCK_SCRIPT =
            "local lockVal = redis.call('GET', KEYS[1]) " +
            "if lockVal == ARGV[1] then " +
            "  redis.call('DEL', KEYS[1]) " +
            "  local avail = redis.call('GET', KEYS[2]) " +
            "  if avail then " +
            "    redis.call('INCRBY', KEYS[2], ARGV[2]) " +
            "  end " +
            "  return 1 " +
            "end " +
            "return 0";

    public boolean lockSeats(Long trainRunId, String coachType, Long fromStationId,
                             Long toStationId, int seatCount, String bookingId) {
        String lockKey = buildLockKey(trainRunId, coachType, fromStationId, toStationId, bookingId);
        String availKey = buildAvailKey(trainRunId, coachType, fromStationId, toStationId);

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(LOCK_SCRIPT, Long.class);
        Long result = redisTemplate.execute(script,
                List.of(lockKey, availKey),
                String.valueOf(seatCount), bookingId, String.valueOf(lockTtlSeconds));

        if (result == null || result == -1) {
            log.debug("No availability cache for segment, falling back to DB lock for booking {}", bookingId);
            return lockWithoutCache(lockKey, bookingId);
        }

        if (result == 0) {
            throw new BusinessException("SEATS_UNAVAILABLE",
                    "Not enough seats available for the requested segment");
        }

        log.info("Locked {} seats for booking {} (TTL: {}s)", seatCount, bookingId, lockTtlSeconds);
        return true;
    }

    public boolean releaseLock(Long trainRunId, String coachType, Long fromStationId,
                               Long toStationId, int seatCount, String bookingId) {
        String lockKey = buildLockKey(trainRunId, coachType, fromStationId, toStationId, bookingId);
        String availKey = buildAvailKey(trainRunId, coachType, fromStationId, toStationId);

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);
        Long result = redisTemplate.execute(script,
                List.of(lockKey, availKey),
                bookingId, String.valueOf(seatCount));

        if (result != null && result == 1) {
            log.info("Released seat lock for booking {}", bookingId);
            return true;
        }

        log.warn("Lock release failed for booking {} — lock may have expired", bookingId);
        return false;
    }

    public boolean isLocked(Long trainRunId, String coachType, Long fromStationId,
                            Long toStationId, String bookingId) {
        String lockKey = buildLockKey(trainRunId, coachType, fromStationId, toStationId, bookingId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(lockKey));
    }

    public void setAvailabilityCount(Long trainRunId, String coachType,
                                     Long fromStationId, Long toStationId, int count) {
        String availKey = buildAvailKey(trainRunId, coachType, fromStationId, toStationId);
        redisTemplate.opsForValue().set(availKey, String.valueOf(count),
                lockTtlSeconds * 2, TimeUnit.SECONDS);
    }

    private boolean lockWithoutCache(String lockKey, String bookingId) {
        Boolean set = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, bookingId, lockTtlSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(set);
    }

    private String buildLockKey(Long trainRunId, String coachType,
                                Long fromStationId, Long toStationId, String bookingId) {
        return LOCK_PREFIX + trainRunId + ":" + coachType + ":" +
                fromStationId + ":" + toStationId + ":" + bookingId;
    }

    private String buildAvailKey(Long trainRunId, String coachType,
                                 Long fromStationId, Long toStationId) {
        return AVAIL_PREFIX + trainRunId + ":" + coachType + ":" + fromStationId + ":" + toStationId;
    }
}
