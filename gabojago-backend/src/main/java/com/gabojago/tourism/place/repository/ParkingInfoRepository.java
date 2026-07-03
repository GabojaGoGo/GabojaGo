package com.gabojago.tourism.place.repository;

import com.gabojago.tourism.place.domain.ParkingInfo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParkingInfoRepository extends JpaRepository<ParkingInfo, Long> {
}
