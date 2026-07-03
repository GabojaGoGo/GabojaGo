package com.gabojago.tourism.place.repository;

import com.gabojago.tourism.place.domain.PlaceSuitability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.gabojago.tourism.place.domain.enums.SuitabilityTargetType;

public interface PlaceSuitabilityRepository extends JpaRepository<PlaceSuitability, Long> {
    List<PlaceSuitability> findAllByPlace_IdIn(Collection<Long> placeIds);
    List<PlaceSuitability> findAllByPlace_Id(Long placeId);
    Optional<PlaceSuitability> findByPlace_IdAndTargetTypeAndTargetCode(
            Long placeId,
            SuitabilityTargetType targetType,
            String targetCode
    );
}
