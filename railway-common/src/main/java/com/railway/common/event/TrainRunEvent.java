package com.railway.common.event;

import java.time.LocalDate;

public record TrainRunEvent(
        Long trainRunId,
        Long trainId,
        Long routeId,
        Long scheduleId,
        LocalDate runDate,
        String status
) {}
