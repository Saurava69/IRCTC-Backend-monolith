package com.railway.train.search.repository;

import com.railway.train.search.document.JourneyOptionDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.time.LocalDate;
import java.util.List;

public interface JourneyOptionSearchRepository extends ElasticsearchRepository<JourneyOptionDocument, String> {

    Page<JourneyOptionDocument> findByFromStationCodeAndToStationCodeAndRunDate(
            String fromStationCode, String toStationCode, LocalDate runDate, Pageable pageable);

    List<JourneyOptionDocument> findByTrainRunId(Long trainRunId);

    void deleteByTrainRunId(Long trainRunId);

    long countByRunDate(LocalDate runDate);
}
