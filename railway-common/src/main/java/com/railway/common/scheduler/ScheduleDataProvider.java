package com.railway.common.scheduler;

import java.util.List;

public interface ScheduleDataProvider {

    record ScheduleInfo(Long scheduleId, Long trainId, Long routeId,
                        boolean[] runsOnDays, List<SegmentTemplate> segments) {}

    record SegmentTemplate(String coachType, Long fromStationId,
                           Long toStationId, int totalSeats) {}

    List<ScheduleInfo> getAllActiveSchedulesWithSegments();
}
