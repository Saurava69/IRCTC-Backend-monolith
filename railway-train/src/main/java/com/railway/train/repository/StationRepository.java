package com.railway.train.repository;

import com.railway.train.entity.Station;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface StationRepository extends JpaRepository<Station, Long> {

    Optional<Station> findByCode(String code);

    boolean existsByCode(String code);

    @Query("SELECT s FROM Station s WHERE s.isActive = true AND " +
            "(LOWER(s.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(s.code) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(s.city) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Station> search(String query, Pageable pageable);
}
