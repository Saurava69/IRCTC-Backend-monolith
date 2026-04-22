package com.railway.train.search.service;

import com.railway.common.search.SearchDataProvider;
import com.railway.train.dto.JourneySearchResponse;
import com.railway.train.dto.StationSuggestionResponse;
import com.railway.train.search.document.JourneyOptionDocument;
import com.railway.train.search.repository.JourneyOptionSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

import co.elastic.clients.elasticsearch._types.query_dsl.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrainSearchService {

    private final JourneyOptionSearchRepository searchRepository;
    private final JourneyDocumentBuilder journeyDocumentBuilder;
    private final SearchDataProvider searchDataProvider;
    private final ElasticsearchTemplate elasticsearchTemplate;

    public Page<JourneySearchResponse> searchTrains(String from, String to,
                                                      LocalDate date, String coachType,
                                                      Pageable pageable) {
        Page<JourneyOptionDocument> results = searchRepository
                .findByFromStationCodeAndToStationCodeAndRunDate(from.toUpperCase(), to.toUpperCase(), date, pageable);

        return results.map(this::toResponse);
    }

    public List<JourneySearchResponse> searchTrainsAdvanced(String from, String to,
                                                              LocalDate date, String coachType) {
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();

        boolBuilder.must(Query.of(q -> q.term(t -> t.field("runDate").value(date.toString()))));

        boolBuilder.must(buildStationQuery("fromStationCode", "fromStationName", from));
        boolBuilder.must(buildStationQuery("toStationCode", "toStationName", to));

        if (coachType != null && !coachType.isBlank()) {
            boolBuilder.must(Query.of(q -> q.nested(n -> n
                    .path("coachAvailabilities")
                    .query(nq -> nq.term(t -> t
                            .field("coachAvailabilities.coachType")
                            .value(coachType.toUpperCase()))))));
        }

        NativeQuery query = NativeQuery.builder()
                .withQuery(Query.of(q -> q.bool(boolBuilder.build())))
                .withSort(s -> s.field(f -> f.field("departureTime").order(co.elastic.clients.elasticsearch._types.SortOrder.Asc)))
                .build();

        SearchHits<JourneyOptionDocument> hits = elasticsearchTemplate.search(query, JourneyOptionDocument.class);

        return hits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(this::toResponse)
                .toList();
    }

    public long reindexAll() {
        searchRepository.deleteAll();
        List<Long> trainRunIds = searchDataProvider.getAllActiveTrainRunIds();
        long totalDocs = 0;

        for (Long trainRunId : trainRunIds) {
            try {
                List<JourneyOptionDocument> docs = journeyDocumentBuilder.buildDocuments(trainRunId);
                if (!docs.isEmpty()) {
                    searchRepository.saveAll(docs);
                    totalDocs += docs.size();
                }
            } catch (Exception e) {
                log.error("Failed to index trainRunId={}: {}", trainRunId, e.getMessage());
            }
        }

        log.info("Reindex complete: {} train runs, {} journey documents", trainRunIds.size(), totalDocs);
        return totalDocs;
    }

    private Query buildStationQuery(String codeField, String nameField, String input) {
        return Query.of(q -> q.bool(b -> b
                .should(Query.of(sq -> sq.term(t -> t.field(codeField).value(input.toUpperCase()))))
                .should(Query.of(sq -> sq.match(m -> m.field(nameField).query(input))))
                .minimumShouldMatch("1")));
    }

    private JourneySearchResponse toResponse(JourneyOptionDocument doc) {
        List<JourneySearchResponse.CoachAvailabilityInfo> availability = doc.getCoachAvailabilities() != null
                ? doc.getCoachAvailabilities().stream()
                .map(ca -> new JourneySearchResponse.CoachAvailabilityInfo(
                        ca.getCoachType(), ca.getTotalSeats(), ca.getAvailableSeats(),
                        ca.getRacSeats(), ca.getWaitlistCount()))
                .toList()
                : List.of();

        List<JourneySearchResponse.FareDetail> fares = doc.getFares() != null
                ? doc.getFares().stream()
                .map(f -> new JourneySearchResponse.FareDetail(f.getCoachType(), f.getBaseFare()))
                .toList()
                : List.of();

        return new JourneySearchResponse(
                doc.getTrainRunId(),
                doc.getTrainId(),
                doc.getTrainNumber(),
                doc.getTrainName(),
                doc.getTrainType(),
                doc.getRunDate(),
                new JourneySearchResponse.StationInfo(doc.getFromStationId(), doc.getFromStationCode(), doc.getFromStationName()),
                new JourneySearchResponse.StationInfo(doc.getToStationId(), doc.getToStationCode(), doc.getToStationName()),
                doc.getDepartureTime(),
                doc.getArrivalTime(),
                doc.getDurationMinutes(),
                doc.getDistanceKm(),
                availability,
                fares
        );
    }
}
