package com.gabojago.tourism.data.repository;

import com.gabojago.tourism.data.domain.CongestionApiItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CongestionApiItemRepository extends JpaRepository<CongestionApiItem, Long> {
}
