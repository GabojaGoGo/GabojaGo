package com.gabojago.place.repository;

import com.gabojago.place.domain.Place;
import com.gabojago.place.domain.enums.PlaceDataSourceType;
import com.gabojago.place.domain.enums.PlaceType;
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

    @Override
    @EntityGraph(attributePaths = "region")
    Page<Place> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "region")
    Optional<Place> findWithRegionById(Long id);

    @EntityGraph(attributePaths = "region")
    Page<Place> findAllByPlaceType(PlaceType placeType, Pageable pageable);
}
