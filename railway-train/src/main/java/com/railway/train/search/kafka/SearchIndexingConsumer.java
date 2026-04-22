package com.railway.train.search.kafka;

import com.railway.common.event.BookingEvent;
import com.railway.common.event.EventEnvelope;
import com.railway.common.event.TrainRunEvent;
import com.railway.train.search.document.JourneyOptionDocument;
import com.railway.train.search.repository.JourneyOptionSearchRepository;
import com.railway.train.search.service.JourneyDocumentBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SearchIndexingConsumer {

    private final JourneyDocumentBuilder journeyDocumentBuilder;
    private final JourneyOptionSearchRepository searchRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "${app.kafka.topics.train-events}", groupId = "search-indexing")
    public void handleTrainEvent(EventEnvelope<?> envelope) {
        String eventType = envelope.getEventType();
        log.info("Search indexing received train event: {} for aggregate {}",
                eventType, envelope.getAggregateId());

        if ("TRAIN_RUN_CREATED".equals(eventType)) {
            TrainRunEvent event = objectMapper.convertValue(envelope.getPayload(), TrainRunEvent.class);
            indexTrainRun(event.trainRunId());
        }
    }

    @KafkaListener(topics = "${app.kafka.topics.booking-events}", groupId = "search-indexing")
    public void handleBookingEvent(EventEnvelope<?> envelope) {
        String eventType = envelope.getEventType();

        if ("BOOKING_CONFIRMED".equals(eventType) || "BOOKING_FAILED".equals(eventType)) {
            BookingEvent event = objectMapper.convertValue(envelope.getPayload(), BookingEvent.class);
            log.info("Search indexing updating availability for trainRunId={} due to {}",
                    event.trainRunId(), eventType);
            updateAvailability(event.trainRunId());
        }
    }

    private void indexTrainRun(Long trainRunId) {
        try {
            List<JourneyOptionDocument> docs = journeyDocumentBuilder.buildDocuments(trainRunId);
            if (!docs.isEmpty()) {
                searchRepository.saveAll(docs);
                log.info("Indexed {} journey documents for trainRunId={}", docs.size(), trainRunId);
            }
        } catch (Exception e) {
            log.error("Failed to index trainRunId={}: {}", trainRunId, e.getMessage(), e);
            throw e;
        }
    }

    private void updateAvailability(Long trainRunId) {
        try {
            List<JourneyOptionDocument> existingDocs = searchRepository.findByTrainRunId(trainRunId);
            if (existingDocs.isEmpty()) {
                log.warn("No existing documents for trainRunId={}, performing full index", trainRunId);
                indexTrainRun(trainRunId);
                return;
            }

            journeyDocumentBuilder.updateAvailability(trainRunId, existingDocs);
            searchRepository.saveAll(existingDocs);
            log.info("Updated availability for {} documents, trainRunId={}", existingDocs.size(), trainRunId);
        } catch (Exception e) {
            log.error("Failed to update availability for trainRunId={}: {}", trainRunId, e.getMessage(), e);
            throw e;
        }
    }
}
