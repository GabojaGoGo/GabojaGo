package com.gabojago.tourism.place.repository;

import com.gabojago.tourism.place.domain.Place;
import com.gabojago.tourism.place.domain.enums.PlaceDataSourceType;
import com.gabojago.tourism.place.domain.enums.PlaceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    Optional<Place> findBySourceTypeAndSourcePlaceId(
            PlaceDataSourceType sourceType,
            String sourcePlaceId
    );
    List<Place> findAllByRegion_IdAndPlaceTypeIn(
            Long regionId,
            List<PlaceType> placeTypes
    );
}
