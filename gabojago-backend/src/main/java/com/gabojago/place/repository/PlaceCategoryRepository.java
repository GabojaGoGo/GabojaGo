package com.gabojago.place.repository;

import com.gabojago.place.domain.PlaceCategory;
import com.gabojago.place.domain.enums.CategoryKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlaceCategoryRepository extends JpaRepository<PlaceCategory, Long> {

    List<PlaceCategory> findAllByPlace_Id(Long placeId);

    Optional<PlaceCategory> findByPlace_IdAndCategory_Id(Long placeId, Long categoryId);

    @Query("""
            SELECT pc
            FROM PlaceCategory pc
            JOIN FETCH pc.category c
            WHERE pc.place.id IN :placeIds
              AND c.kind = :kind
              AND c.active = true
            ORDER BY pc.place.id ASC, c.code ASC, pc.id ASC
            """)
    List<PlaceCategory> findAllByPlaceIdsAndCategoryKind(
            @Param("placeIds") List<Long> placeIds,
            @Param("kind") CategoryKind kind
    );
}
