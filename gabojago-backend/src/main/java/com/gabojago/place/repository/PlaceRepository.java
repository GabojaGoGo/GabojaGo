package com.gabojago.place.repository;

import com.gabojago.place.domain.Place;
import com.gabojago.place.domain.enums.CategoryKind;
import com.gabojago.place.domain.enums.PlaceDataSourceType;
import com.gabojago.place.domain.enums.PlaceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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

    @EntityGraph(attributePaths = "region")
    @Query(
            value = """
                    SELECT p
                    FROM Place p
                    WHERE p.placeType = :placeType
                      AND EXISTS (
                        SELECT pc.id
                        FROM PlaceCategory pc
                        WHERE pc.place = p
                          AND pc.category.kind = :kind
                          AND pc.category.code = :categoryCode
                    )
                    """,
            countQuery = """
                    SELECT COUNT(p.id)
                    FROM Place p
                    WHERE p.placeType = :placeType
                      AND EXISTS (
                        SELECT pc.id
                        FROM PlaceCategory pc
                        WHERE pc.place = p
                          AND pc.category.kind = :kind
                          AND pc.category.code = :categoryCode
                    )
                    """
    )
    Page<Place> findAllByCategoryKindAndCode(
            @Param("kind") CategoryKind kind,
            @Param("placeType") PlaceType placeType,
            @Param("categoryCode") String categoryCode,
            Pageable pageable
    );

    List<Place> findAllByRegion_IdAndPlaceTypeIn(Long regionId, List<PlaceType> placeTypes);

    @Query("""
            SELECT p FROM Place p
            WHERE p.region.id = :regionId AND p.placeType IN :placeTypes
              AND EXISTS (SELECT pc.id FROM PlaceCategory pc
                          WHERE pc.place = p AND pc.status = com.gabojago.place.domain.enums.PlaceCategoryStatus.INCLUDED
                            AND pc.category.kind = com.gabojago.place.domain.enums.CategoryKind.SUBTYPE
                            AND pc.category.code IN :subtypeCodes)
            """)
    List<Place> findAllByRegionAndTypeAndSubtypeCodes(
            @Param("regionId") Long regionId,
            @Param("placeTypes") List<PlaceType> placeTypes,
            @Param("subtypeCodes") List<String> subtypeCodes
    );
}
