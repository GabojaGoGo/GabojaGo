package com.gabojago.place.repository;

import com.gabojago.place.domain.Category;
import com.gabojago.place.domain.enums.CategoryKind;
import com.gabojago.place.domain.enums.PlaceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByKindAndCode(
            CategoryKind kind,
            String code
    );

    List<Category> findAllByPlaceTypeAndKindOrderByNameAsc(
            PlaceType placeType,
            CategoryKind kind
    );
}
