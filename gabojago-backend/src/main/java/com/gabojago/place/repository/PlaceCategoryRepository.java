package com.gabojago.tourism.place.repository;

import com.gabojago.tourism.place.domain.PlaceCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlaceCategoryRepository extends JpaRepository<PlaceCategory, Long> {

    List<PlaceCategory> findAllByPlace_Id(Long placeId);

    Optional<PlaceCategory> findByPlace_IdAndCategory_Id(Long placeId, Long categoryId);
}
