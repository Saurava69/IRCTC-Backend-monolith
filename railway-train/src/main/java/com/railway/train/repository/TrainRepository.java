package com.railway.train.repository;

import com.railway.train.entity.Train;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TrainRepository extends JpaRepository<Train, Long> {

    Optional<Train> findByTrainNumber(String trainNumber);

    boolean existsByTrainNumber(String trainNumber);

    @Query("SELECT t FROM Train t JOIN FETCH t.sourceStation JOIN FETCH t.destStation WHERE t.isActive = true")
    List<Train> findAllActive();
}
