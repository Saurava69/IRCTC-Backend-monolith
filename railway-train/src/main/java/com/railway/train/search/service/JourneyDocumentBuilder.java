package com.railway.train.search.service;

import com.railway.common.search.SearchDataProvider;
import com.railway.common.search.SearchDataProvider.SegmentAvailability;
import com.railway.common.search.SearchDataProvider.TrainRunInfo;
import com.railway.train.entity.Route;
import com.railway.train.entity.RouteStation;
import com.railway.train.entity.Train;
import com.railway.train.repository.RouteRepository;
import com.railway.train.repository.TrainRepository;
import com.railway.train.search.document.CoachAvailability;
import com.railway.train.search.document.FareInfo;
import com.railway.train.search.document.JourneyOptionDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class JourneyDocumentBuilder {

    private final SearchDataProvider searchDataProvider;
    private final RouteRepository routeRepository;
    private final TrainRepository trainRepository;

    public List<JourneyOptionDocument> buildDocuments(Long trainRunId) {
        TrainRunInfo runInfo = searchDataProvider.getTrainRunInfo(trainRunId);

        Optional<Route> routeOpt = routeRepository.findByIdWithStations(runInfo.routeId());
        if (routeOpt.isEmpty()) {
            log.warn("Route not found for trainRunId={} routeId={}", trainRunId, runInfo.routeId());
            return Collections.emptyList();
        }
        Route route = routeOpt.get();

        Optional<Train> trainOpt = trainRepository.findById(runInfo.trainId());
        if (trainOpt.isEmpty()) {
            log.warn("Train not found for trainRunId={} trainId={}", trainRunId, runInfo.trainId());
            return Collections.emptyList();
        }
        Train train = trainOpt.get();

        List<SegmentAvailability> allAvailabilities = searchDataProvider.getSegmentAvailabilities(trainRunId);
        Map<String, SegmentAvailability> availabilityMap = new HashMap<>();
        for (SegmentAvailability sa : allAvailabilities) {
            String key = sa.coachType() + "_" + sa.fromStationId() + "_" + sa.toStationId();
            availabilityMap.put(key, sa);
        }

        List<RouteStation> stations = route.getRouteStations().stream()
                .sorted(Comparator.comparingInt(RouteStation::getSequenceNumber))
                .toList();

        List<JourneyOptionDocument> documents = new ArrayList<>();

        for (int i = 0; i < stations.size(); i++) {
            for (int j = i + 1; j < stations.size(); j++) {
                RouteStation fromRS = stations.get(i);
                RouteStation toRS = stations.get(j);

                Long fromStationId = fromRS.getStation().getId();
                Long toStationId = toRS.getStation().getId();

                List<CoachAvailability> coachAvails = new ArrayList<>();
                List<FareInfo> fares = new ArrayList<>();

                Set<String> coachTypes = allAvailabilities.stream()
                        .map(SegmentAvailability::coachType)
                        .collect(Collectors.toSet());

                for (String coachType : coachTypes) {
                    String key = coachType + "_" + fromStationId + "_" + toStationId;
                    SegmentAvailability sa = availabilityMap.get(key);

                    if (sa != null) {
                        coachAvails.add(CoachAvailability.builder()
                                .coachType(coachType)
                                .totalSeats(sa.totalSeats())
                                .availableSeats(sa.availableSeats())
                                .racSeats(sa.racSeats())
                                .waitlistCount(sa.waitlistCount())
                                .build());
                    }

                    fares.add(FareInfo.builder()
                            .coachType(coachType)
                            .baseFare(calculateFare(coachType))
                            .build());
                }

                LocalTime departure = fromRS.getDepartureTime();
                LocalTime arrival = toRS.getArrivalTime();
                int durationMinutes = calculateDuration(departure, arrival,
                        fromRS.getDayOffset(), toRS.getDayOffset());
                int distanceKm = toRS.getDistanceFromOriginKm() - fromRS.getDistanceFromOriginKm();

                String docId = trainRunId + "_" + fromStationId + "_" + toStationId;

                JourneyOptionDocument doc = JourneyOptionDocument.builder()
                        .id(docId)
                        .trainRunId(trainRunId)
                        .trainId(train.getId())
                        .trainNumber(train.getTrainNumber())
                        .trainName(train.getName())
                        .trainType(train.getTrainType().name())
                        .runDate(runInfo.runDate())
                        .fromStationId(fromStationId)
                        .fromStationCode(fromRS.getStation().getCode())
                        .fromStationName(fromRS.getStation().getName())
                        .toStationId(toStationId)
                        .toStationCode(toRS.getStation().getCode())
                        .toStationName(toRS.getStation().getName())
                        .departureTime(departure != null ? departure.toString() : null)
                        .arrivalTime(arrival != null ? arrival.toString() : null)
                        .durationMinutes(durationMinutes)
                        .distanceKm(distanceKm)
                        .routeId(route.getId())
                        .fromSequence(fromRS.getSequenceNumber())
                        .toSequence(toRS.getSequenceNumber())
                        .coachAvailabilities(coachAvails)
                        .fares(fares)
                        .lastUpdated(Instant.now())
                        .build();

                documents.add(doc);
            }
        }

        log.info("Built {} journey documents for trainRunId={}", documents.size(), trainRunId);
        return documents;
    }

    public void updateAvailability(Long trainRunId, List<JourneyOptionDocument> existingDocs) {
        List<SegmentAvailability> allAvailabilities = searchDataProvider.getSegmentAvailabilities(trainRunId);
        Map<String, SegmentAvailability> availabilityMap = new HashMap<>();
        for (SegmentAvailability sa : allAvailabilities) {
            String key = sa.coachType() + "_" + sa.fromStationId() + "_" + sa.toStationId();
            availabilityMap.put(key, sa);
        }

        for (JourneyOptionDocument doc : existingDocs) {
            List<CoachAvailability> updatedAvails = new ArrayList<>();
            for (CoachAvailability ca : doc.getCoachAvailabilities()) {
                String key = ca.getCoachType() + "_" + doc.getFromStationId() + "_" + doc.getToStationId();
                SegmentAvailability sa = availabilityMap.get(key);
                if (sa != null) {
                    updatedAvails.add(CoachAvailability.builder()
                            .coachType(sa.coachType())
                            .totalSeats(sa.totalSeats())
                            .availableSeats(sa.availableSeats())
                            .racSeats(sa.racSeats())
                            .waitlistCount(sa.waitlistCount())
                            .build());
                } else {
                    updatedAvails.add(ca);
                }
            }
            doc.setCoachAvailabilities(updatedAvails);
            doc.setLastUpdated(Instant.now());
        }
    }

    private BigDecimal calculateFare(String coachType) {
        return switch (coachType) {
            case "FIRST_AC" -> new BigDecimal("2500");
            case "SECOND_AC" -> new BigDecimal("1500");
            case "THIRD_AC" -> new BigDecimal("1000");
            case "SLEEPER" -> new BigDecimal("500");
            default -> new BigDecimal("200");
        };
    }

    private int calculateDuration(LocalTime departure, LocalTime arrival,
                                   int fromDayOffset, int toDayOffset) {
        if (departure == null || arrival == null) return 0;
        int depMinutes = fromDayOffset * 1440 + departure.getHour() * 60 + departure.getMinute();
        int arrMinutes = toDayOffset * 1440 + arrival.getHour() * 60 + arrival.getMinute();
        return Math.max(0, arrMinutes - depMinutes);
    }
}
