package com.railway.train.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateScheduleRequest(
        @NotNull Long trainId,
        @NotNull Long routeId,
        boolean runsOnMonday,
        boolean runsOnTuesday,
        boolean runsOnWednesday,
        boolean runsOnThursday,
        boolean runsOnFriday,
        boolean runsOnSaturday,
        boolean runsOnSunday,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveUntil
) {
}
