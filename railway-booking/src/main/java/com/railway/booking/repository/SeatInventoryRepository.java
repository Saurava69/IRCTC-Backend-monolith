package com.railway.booking.repository;

import com.railway.booking.entity.SeatInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SeatInventoryRepository extends JpaRepository<SeatInventory, Long> {

    @Query("SELECT si FROM SeatInventory si WHERE si.trainRunId = :trainRunId " +
            "AND si.coachType = :coachType " +
            "AND si.fromStationId = :fromStationId AND si.toStationId = :toStationId")
    Optional<SeatInventory> findBySegment(Long trainRunId, String coachType,
                                          Long fromStationId, Long toStationId);

    @Query("SELECT si FROM SeatInventory si WHERE si.trainRunId = :trainRunId AND si.coachType = :coachType")
    List<SeatInventory> findByTrainRunAndCoachType(Long trainRunId, String coachType);

    List<SeatInventory> findByTrainRunId(Long trainRunId);

    @Modifying
    @Query("UPDATE SeatInventory si SET si.availableSeats = si.availableSeats - :count, " +
            "si.version = si.version + 1 " +
            "WHERE si.id = :id AND si.availableSeats >= :count AND si.version = :version")
    int decrementAvailableSeats(Long id, int count, Long version);

    @Modifying
    @Query("UPDATE SeatInventory si SET si.availableSeats = si.availableSeats + :count, " +
            "si.version = si.version + 1 " +
            "WHERE si.id = :id AND si.version = :version")
    int incrementAvailableSeats(Long id, int count, Long version);
}
