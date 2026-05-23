package com.tripmate.backend.infrastructure.tourapi.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CongestionApiBatchRepository extends JpaRepository<CongestionApiBatch, Long> {
    List<CongestionApiBatch> findAllByOrderByFetchedAtDesc();
}
