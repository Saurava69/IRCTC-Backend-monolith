package com.railway.booking.scheduler;

import com.railway.booking.service.TrainRunService;
import com.railway.common.scheduler.ScheduleDataProvider;
import com.railway.common.scheduler.ScheduleDataProvider.ScheduleInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class TrainRunGenerationJob {

    private final ScheduleDataProvider scheduleDataProvider;
    private final TrainRunService trainRunService;

    @Value("${app.scheduler.train-run-advance-days:7}")
    private int advanceDays;

    @Scheduled(cron = "${app.scheduler.train-run-generation-cron:0 0 2 * * *}")
    public void generateUpcomingTrainRuns() {
        log.info("Starting auto-generation of train runs for next {} days", advanceDays);

        LocalDate from = LocalDate.now().plusDays(1);
        LocalDate to = LocalDate.now().plusDays(advanceDays);

        List<ScheduleInfo> schedules = scheduleDataProvider.getAllActiveSchedulesWithSegments();
        int totalRuns = 0;

        for (ScheduleInfo schedule : schedules) {
            try {
                List<TrainRunService.SegmentInfo> segments = schedule.segments().stream()
                        .map(st -> new TrainRunService.SegmentInfo(
                                st.coachType(), st.fromStationId(), st.toStationId(), st.totalSeats()))
                        .toList();

                int count = trainRunService.generateTrainRuns(
                        schedule.trainId(), schedule.scheduleId(), schedule.routeId(),
                        schedule.runsOnDays(), from, to, segments);
                totalRuns += count;
            } catch (Exception e) {
                log.error("Failed to generate runs for schedule {}: {}",
                        schedule.scheduleId(), e.getMessage());
            }
        }

        log.info("Auto-generation complete: {} train runs created for {} to {}", totalRuns, from, to);
    }
}
