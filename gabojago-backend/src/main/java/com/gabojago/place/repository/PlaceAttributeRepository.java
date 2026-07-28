package com.gabojago.place.repository;

import com.gabojago.place.domain.PlaceAttribute;
import com.gabojago.place.domain.enums.AttributeKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlaceAttributeRepository extends JpaRepository<PlaceAttribute, Long> {

    List<PlaceAttribute> findAllByPlace_Id(Long placeId);

    List<PlaceAttribute> findAllByPlace_IdIn(List<Long> placeIds);

    Optional<PlaceAttribute> findByPlace_IdAndAttributeKey(Long placeId, AttributeKey attributeKey);
}
