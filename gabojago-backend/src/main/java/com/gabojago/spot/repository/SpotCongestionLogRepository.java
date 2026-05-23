package com.gabojago.spot.repository;

import com.gabojago.spot.domain.SpotCongestionLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpotCongestionLogRepository extends JpaRepository<SpotCongestionLog, Long> {
}
