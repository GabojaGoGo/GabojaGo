package com.gabojago.tourism.data.repository;

import com.gabojago.tourism.data.domain.CongestionApiBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CongestionApiBatchRepository extends JpaRepository<CongestionApiBatch, Long> {
    List<CongestionApiBatch> findAllByOrderByFetchedAtDesc();
}
