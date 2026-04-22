package com.railway.booking.kafka;

import com.railway.booking.entity.TrainRun;
import com.railway.common.event.EventEnvelope;
import com.railway.common.event.TrainRunEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TrainRunEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.train-events}")
    private String trainEventsTopic;

    public void publishTrainRunCreated(TrainRun trainRun) {
        TrainRunEvent payload = new TrainRunEvent(
                trainRun.getId(),
                trainRun.getTrainId(),
                trainRun.getRouteId(),
                trainRun.getScheduleId(),
                trainRun.getRunDate(),
                trainRun.getStatus()
        );

        EventEnvelope<TrainRunEvent> envelope = EventEnvelope.<TrainRunEvent>builder()
                .eventType("TRAIN_RUN_CREATED")
                .aggregateId(String.valueOf(trainRun.getId()))
                .aggregateType("TrainRun")
                .source("railway-booking")
                .payload(payload)
                .build();

        kafkaTemplate.send(trainEventsTopic, String.valueOf(trainRun.getTrainId()), envelope);
        log.info("Published TRAIN_RUN_CREATED for trainRunId={} date={}", trainRun.getId(), trainRun.getRunDate());
    }
}
