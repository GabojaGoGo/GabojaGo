package com.gabojago.tourism.place.repository;

import com.gabojago.tourism.place.domain.Region;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RegionRepository extends JpaRepository<Region, Long> {
    Optional<Region> findByRegionKey(String regionKey);
}
