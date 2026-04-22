package com.railway.train.service;

import com.railway.common.exception.DuplicateResourceException;
import com.railway.common.exception.ResourceNotFoundException;
import com.railway.train.dto.CreateTrainRequest;
import com.railway.train.dto.TrainResponse;
import com.railway.train.entity.*;
import com.railway.train.repository.TrainRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TrainService {

    private final TrainRepository trainRepository;
    private final StationService stationService;

    @Transactional
    public TrainResponse create(CreateTrainRequest request) {
        if (trainRepository.existsByTrainNumber(request.trainNumber())) {
            throw new DuplicateResourceException("Train", request.trainNumber());
        }

        Station source = stationService.getEntityById(request.sourceStationId());
        Station dest = stationService.getEntityById(request.destStationId());

        Train train = Train.builder()
                .trainNumber(request.trainNumber())
                .name(request.name())
                .trainType(TrainType.valueOf(request.trainType().toUpperCase()))
                .sourceStation(source)
                .destStation(dest)
                .build();

        if (request.coaches() != null) {
            for (CreateTrainRequest.CoachRequest cr : request.coaches()) {
                Coach coach = Coach.builder()
                        .train(train)
                        .coachNumber(cr.coachNumber())
                        .coachType(cr.coachType())
                        .totalSeats(cr.totalSeats())
                        .totalBerths(cr.totalBerths())
                        .sequenceInTrain(cr.sequenceInTrain())
                        .build();
                train.getCoaches().add(coach);
            }
        }

        train = trainRepository.save(train);
        return toResponse(train);
    }

    @Transactional(readOnly = true)
    public TrainResponse getByTrainNumber(String trainNumber) {
        Train train = trainRepository.findByTrainNumber(trainNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Train", trainNumber));
        return toResponse(train);
    }

    @Transactional(readOnly = true)
    public List<TrainResponse> getAllActive() {
        return trainRepository.findAllActive().stream().map(this::toResponse).toList();
    }

    public Train getEntityById(Long id) {
        return trainRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Train", id));
    }

    private TrainResponse toResponse(Train train) {
        List<TrainResponse.CoachResponse> coaches = train.getCoaches().stream()
                .map(c -> new TrainResponse.CoachResponse(
                        c.getId(),
                        c.getCoachNumber(),
                        c.getCoachType().name(),
                        c.getTotalSeats(),
                        c.getTotalBerths(),
                        c.getSequenceInTrain()))
                .toList();

        return new TrainResponse(
                train.getId(),
                train.getTrainNumber(),
                train.getName(),
                train.getTrainType().name(),
                stationService.toResponse(train.getSourceStation()),
                stationService.toResponse(train.getDestStation()),
                coaches
        );
    }
}
