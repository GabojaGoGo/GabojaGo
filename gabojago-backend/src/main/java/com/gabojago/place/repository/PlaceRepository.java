package com.gabojago.tourism.place.repository;

import com.gabojago.tourism.place.domain.Place;
import com.gabojago.tourism.place.domain.enums.PlaceDataSourceType;
import com.gabojago.tourism.place.domain.enums.PlaceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    Optional<Place> findBySourceTypeAndSourcePlaceId(
            PlaceDataSourceType sourceType,
            String sourcePlaceId
    );

    @EntityGraph(attributePaths = "region")
    Page<Place> findAllByPlaceType(PlaceType placeType, Pageable pageable);
}
