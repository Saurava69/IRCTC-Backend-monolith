package com.railway.train.service;

import com.railway.common.exception.ResourceNotFoundException;
import com.railway.train.dto.CreateRouteRequest;
import com.railway.train.dto.RouteResponse;
import com.railway.train.entity.*;
import com.railway.train.repository.RouteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RouteService {

    private final RouteRepository routeRepository;
    private final TrainService trainService;
    private final StationService stationService;

    @Transactional
    public RouteResponse create(CreateRouteRequest request) {
        Train train = trainService.getEntityById(request.trainId());

        Route route = Route.builder()
                .train(train)
                .routeName(request.routeName())
                .build();

        if (request.stations() != null) {
            for (CreateRouteRequest.RouteStationRequest rs : request.stations()) {
                Station station = stationService.getEntityById(rs.stationId());
                RouteStation routeStation = RouteStation.builder()
                        .route(route)
                        .station(station)
                        .sequenceNumber(rs.sequenceNumber())
                        .arrivalTime(rs.arrivalTime())
                        .departureTime(rs.departureTime())
                        .haltMinutes(rs.haltMinutes())
                        .distanceFromOriginKm(rs.distanceFromOriginKm())
                        .dayOffset(rs.dayOffset())
                        .build();
                route.getRouteStations().add(routeStation);
            }
        }

        route = routeRepository.save(route);
        return toResponse(route);
    }

    @Transactional(readOnly = true)
    public RouteResponse getById(Long id) {
        Route route = routeRepository.findByIdWithStations(id)
                .orElseThrow(() -> new ResourceNotFoundException("Route", id));
        return toResponse(route);
    }

    @Transactional(readOnly = true)
    public List<RouteResponse> getByTrainId(Long trainId) {
        return routeRepository.findByTrainId(trainId).stream().map(this::toResponse).toList();
    }

    private RouteResponse toResponse(Route route) {
        List<RouteResponse.RouteStationResponse> stations = route.getRouteStations().stream()
                .map(rs -> new RouteResponse.RouteStationResponse(
                        rs.getId(),
                        stationService.toResponse(rs.getStation()),
                        rs.getSequenceNumber(),
                        rs.getArrivalTime(),
                        rs.getDepartureTime(),
                        rs.getHaltMinutes(),
                        rs.getDistanceFromOriginKm(),
                        rs.getDayOffset()))
                .toList();

        return new RouteResponse(
                route.getId(),
                route.getRouteName(),
                route.getTrain().getId(),
                route.getTrain().getTrainNumber(),
                stations
        );
    }
}
