package com.railway.common.search;

import java.time.LocalDate;
import java.util.List;

public interface SearchDataProvider {

    record TrainRunInfo(Long id, Long trainId, Long routeId, Long scheduleId,
                        LocalDate runDate, String status) {}

    record SegmentAvailability(String coachType, Long fromStationId, Long toStationId,
                               int totalSeats, int availableSeats, int racSeats, int waitlistCount) {}

    TrainRunInfo getTrainRunInfo(Long trainRunId);

    List<SegmentAvailability> getSegmentAvailabilities(Long trainRunId);

    List<Long> getAllActiveTrainRunIds();
}
