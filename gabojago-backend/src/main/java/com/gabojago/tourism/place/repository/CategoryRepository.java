package com.gabojago.tourism.place.repository;

import com.gabojago.tourism.place.domain.Category;
import com.gabojago.tourism.place.domain.enums.CategoryKind;
import com.gabojago.tourism.place.domain.enums.PlaceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByPlaceTypeAndCode(PlaceType placeType, String code);

    List<Category> findAllByPlaceTypeAndKindAndActiveTrue(PlaceType placeType, CategoryKind kind);
}
