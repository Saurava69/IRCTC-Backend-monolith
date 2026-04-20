package com.railway.train.service;

import com.railway.common.exception.DuplicateResourceException;
import com.railway.common.exception.ResourceNotFoundException;
import com.railway.train.dto.CreateStationRequest;
import com.railway.train.dto.StationResponse;
import com.railway.train.entity.Station;
import com.railway.train.repository.StationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StationService {

    private final StationRepository stationRepository;

    @Transactional
    public StationResponse create(CreateStationRequest request) {
        if (stationRepository.existsByCode(request.code().toUpperCase())) {
            throw new DuplicateResourceException("Station", request.code());
        }

        Station station = Station.builder()
                .code(request.code().toUpperCase())
                .name(request.name())
                .city(request.city())
                .state(request.state())
                .zone(request.zone())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .build();

        station = stationRepository.save(station);
        return toResponse(station);
    }

    public StationResponse getByCode(String code) {
        Station station = stationRepository.findByCode(code.toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Station", code));
        return toResponse(station);
    }

    public Page<StationResponse> search(String query, Pageable pageable) {
        return stationRepository.search(query, pageable).map(this::toResponse);
    }

    public Station getEntityById(Long id) {
        return stationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Station", id));
    }

    public StationResponse toResponse(Station station) {
        return new StationResponse(
                station.getId(),
                station.getCode(),
                station.getName(),
                station.getCity(),
                station.getState(),
                station.getZone()
        );
    }
}
