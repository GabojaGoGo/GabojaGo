package com.gabojago.tourism.place.repository;

import com.gabojago.tourism.place.domain.Place;
import com.gabojago.tourism.place.domain.enums.CurationStatus;
import com.gabojago.tourism.place.domain.enums.PlaceDataSourceType;
import com.gabojago.tourism.place.domain.enums.PlaceStatus;
import com.gabojago.tourism.place.domain.enums.PlaceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface PlaceRepository extends JpaRepository<Place, Long>, JpaSpecificationExecutor<Place> {
    Optional<Place> findBySourceTypeAndSourcePlaceId(
            PlaceDataSourceType sourceType,
            String sourcePlaceId
    );

    List<Place> findAllByRegion_IdAndPrimaryTypeAndStatusAndCurationStatus(
            Long regionId,
            PlaceType primaryType,
            PlaceStatus status,
            CurationStatus curationStatus
    );

    List<Place> findAllByRegion_IdAndPrimaryTypeInAndStatusAndCurationStatusIn(
            Long regionId,
            List<PlaceType> primaryTypes,
            PlaceStatus status,
            List<CurationStatus> curationStatuses
    );
}
