package com.gabojago.infrastructure.tourapi.repository;

import com.gabojago.infrastructure.tourapi.domain.CongestionApiItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CongestionApiItemRepository extends JpaRepository<CongestionApiItem, Long> {
}
