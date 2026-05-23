package com.tripmate.backend.infrastructure.tourapi.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CongestionApiItemRepository extends JpaRepository<CongestionApiItem, Long> {
}
