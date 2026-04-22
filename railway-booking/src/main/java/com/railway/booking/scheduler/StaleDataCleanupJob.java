package com.railway.booking.scheduler;

import com.railway.booking.repository.TrainRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class StaleDataCleanupJob {

    private final TrainRunRepository trainRunRepository;

    @Value("${app.scheduler.stale-train-run-days:30}")
    private int staleTrainRunDays;

    @Scheduled(cron = "${app.scheduler.stale-cleanup-cron:0 0 4 * * *}")
    @Transactional
    public void cleanupStaleTrainRuns() {
        LocalDate cutoff = LocalDate.now().minusDays(staleTrainRunDays);
        log.info("Cleaning up train runs older than {} ({}+ days)", cutoff, staleTrainRunDays);

        int count = trainRunRepository.markOldRunsCompleted(cutoff);

        if (count > 0) {
            log.info("Marked {} old train runs as COMPLETED", count);
        } else {
            log.debug("No stale train runs to clean up");
        }
    }
}
