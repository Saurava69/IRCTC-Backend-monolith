package com.railway.train.dto;

import java.time.LocalDate;

public record ScheduleResponse(
        Long id,
        Long trainId,
        String trainNumber,
        Long routeId,
        boolean runsOnMonday,
        boolean runsOnTuesday,
        boolean runsOnWednesday,
        boolean runsOnThursday,
        boolean runsOnFriday,
        boolean runsOnSaturday,
        boolean runsOnSunday,
        LocalDate effectiveFrom,
        LocalDate effectiveUntil,
        boolean isActive
) {
}
