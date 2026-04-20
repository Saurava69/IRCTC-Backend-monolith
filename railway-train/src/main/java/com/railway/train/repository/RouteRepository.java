package com.railway.train.repository;

import com.railway.train.entity.Route;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface RouteRepository extends JpaRepository<Route, Long> {

    @Query("SELECT r FROM Route r JOIN FETCH r.routeStations rs JOIN FETCH rs.station WHERE r.id = :id")
    Optional<Route> findByIdWithStations(Long id);

    List<Route> findByTrainId(Long trainId);
}
