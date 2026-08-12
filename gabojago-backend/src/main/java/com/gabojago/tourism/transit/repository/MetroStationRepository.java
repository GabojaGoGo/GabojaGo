package com.gabojago.tourism.transit.repository;

import com.gabojago.tourism.transit.domain.MetroStation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetroStationRepository extends JpaRepository<MetroStation, String> {

    java.util.List<MetroStation> findByLatitudeIsNotNullAndLongitudeIsNotNull();
}
