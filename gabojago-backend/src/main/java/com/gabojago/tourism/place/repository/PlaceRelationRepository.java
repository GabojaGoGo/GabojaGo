package com.gabojago.tourism.place.repository;

import com.gabojago.tourism.place.domain.PlaceRelation;
import com.gabojago.tourism.place.domain.enums.PlaceRelationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PlaceRelationRepository extends JpaRepository<PlaceRelation, Long> {
    List<PlaceRelation> findAllByFromPlace_IdInAndRelationType(
            Collection<Long> fromPlaceIds,
            PlaceRelationType relationType
    );

    List<PlaceRelation> findAllByFromPlace_IdAndRelationType(
            Long fromPlaceId,
            PlaceRelationType relationType
    );
}
