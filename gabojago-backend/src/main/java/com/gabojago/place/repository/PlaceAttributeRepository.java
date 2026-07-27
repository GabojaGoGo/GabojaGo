package com.gabojago.tourism.place.repository;

import com.gabojago.tourism.place.domain.PlaceAttribute;
import com.gabojago.tourism.place.domain.enums.AttributeKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlaceAttributeRepository extends JpaRepository<PlaceAttribute, Long> {

    List<PlaceAttribute> findAllByPlace_Id(Long placeId);

    Optional<PlaceAttribute> findByPlace_IdAndAttributeKey(Long placeId, AttributeKey attributeKey);
}
