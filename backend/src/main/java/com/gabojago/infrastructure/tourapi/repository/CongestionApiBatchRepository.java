package com.gabojago.infrastructure.tourapi.repository;

import com.gabojago.infrastructure.tourapi.domain.CongestionApiBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CongestionApiBatchRepository extends JpaRepository<CongestionApiBatch, Long> {
    List<CongestionApiBatch> findAllByOrderByFetchedAtDesc();
}
