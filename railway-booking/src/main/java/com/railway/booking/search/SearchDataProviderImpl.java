package com.railway.booking.search;

import com.railway.booking.entity.SeatInventory;
import com.railway.booking.entity.TrainRun;
import com.railway.booking.repository.SeatInventoryRepository;
import com.railway.booking.repository.TrainRunRepository;
import com.railway.common.exception.ResourceNotFoundException;
import com.railway.common.search.SearchDataProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SearchDataProviderImpl implements SearchDataProvider {

    private final TrainRunRepository trainRunRepository;
    private final SeatInventoryRepository seatInventoryRepository;

    @Override
    public TrainRunInfo getTrainRunInfo(Long trainRunId) {
        TrainRun tr = trainRunRepository.findById(trainRunId)
                .orElseThrow(() -> new ResourceNotFoundException("TrainRun", trainRunId));
        return new TrainRunInfo(tr.getId(), tr.getTrainId(), tr.getRouteId(),
                tr.getScheduleId(), tr.getRunDate(), tr.getStatus());
    }

    @Override
    public List<SegmentAvailability> getSegmentAvailabilities(Long trainRunId) {
        List<SeatInventory> inventories = seatInventoryRepository.findByTrainRunId(trainRunId);
        return inventories.stream()
                .map(si -> new SegmentAvailability(
                        si.getCoachType(), si.getFromStationId(), si.getToStationId(),
                        si.getTotalSeats(), si.getAvailableSeats(),
                        si.getRacSeats(), si.getWaitlistCount()))
                .toList();
    }

    @Override
    public List<Long> getAllActiveTrainRunIds() {
        return trainRunRepository.findAllActiveTrainRunIds();
    }
}
