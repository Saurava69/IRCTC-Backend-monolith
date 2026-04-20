package com.railway.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventEnvelope<T> {

    @Builder.Default
    private String eventId = UUID.randomUUID().toString();

    private String eventType;
    private String aggregateId;
    private String aggregateType;

    @Builder.Default
    private Instant timestamp = Instant.now();

    @Builder.Default
    private int version = 1;

    private String source;
    private String correlationId;
    private String idempotencyKey;
    private T payload;
}
