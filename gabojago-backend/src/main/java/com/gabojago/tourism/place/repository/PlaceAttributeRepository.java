package com.gabojago.tourism.place.repository;

import com.gabojago.tourism.place.domain.PlaceAttribute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.gabojago.tourism.place.domain.enums.PlaceAttributeCode;

public interface PlaceAttributeRepository extends JpaRepository<PlaceAttribute, Long> {
    List<PlaceAttribute> findAllByPlace_IdIn(Collection<Long> placeIds);
    List<PlaceAttribute> findAllByPlace_Id(Long placeId);
    Optional<PlaceAttribute> findByPlace_IdAndAttributeCode(Long placeId, PlaceAttributeCode attributeCode);
}
