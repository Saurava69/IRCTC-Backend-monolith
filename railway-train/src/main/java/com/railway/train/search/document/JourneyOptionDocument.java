package com.railway.train.search.document;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Document(indexName = "journey_options")
@Setting(settingPath = "elasticsearch/journey-options-settings.json")
@Mapping(mappingPath = "elasticsearch/journey-options-mappings.json")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JourneyOptionDocument {

    @Id
    private String id;

    private Long trainRunId;
    private Long trainId;
    private String trainNumber;
    private String trainName;
    private String trainType;

    private LocalDate runDate;
    private Long fromStationId;
    private String fromStationCode;
    private String fromStationName;
    private Long toStationId;
    private String toStationCode;
    private String toStationName;
    private LocalTime departureTime;
    private LocalTime arrivalTime;
    private Integer durationMinutes;
    private Integer distanceKm;

    private Long routeId;
    private Integer fromSequence;
    private Integer toSequence;

    @Field(type = FieldType.Nested)
    private List<CoachAvailability> coachAvailabilities;

    private List<FareInfo> fares;

    private Instant lastUpdated;
}
