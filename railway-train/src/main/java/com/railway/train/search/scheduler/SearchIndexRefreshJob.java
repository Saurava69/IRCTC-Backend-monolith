package com.railway.train.search.scheduler;

import com.railway.train.search.service.TrainSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SearchIndexRefreshJob {

    private final TrainSearchService trainSearchService;

    @Scheduled(cron = "${app.scheduler.search-reindex-cron:0 30 3 * * *}")
    public void nightlyReindex() {
        log.info("Starting nightly ES reindex");
        try {
            long count = trainSearchService.reindexAll();
            log.info("Nightly reindex complete: {} documents indexed", count);
        } catch (Exception e) {
            log.error("Nightly reindex failed: {}", e.getMessage(), e);
        }
    }
}
