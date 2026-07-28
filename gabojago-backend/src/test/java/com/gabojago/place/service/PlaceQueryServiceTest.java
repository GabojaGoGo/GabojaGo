package com.gabojago.place.service;

import com.gabojago.place.domain.Category;
import com.gabojago.place.domain.Place;
import com.gabojago.place.domain.PlaceCategory;
import com.gabojago.place.domain.Region;
import com.gabojago.place.domain.enums.CategoryKind;
import com.gabojago.place.domain.enums.PlaceCategoryStatus;
import com.gabojago.place.domain.enums.PlaceType;
import com.gabojago.place.dto.response.PlacePageResponse;
import com.gabojago.place.repository.PlaceCategoryRepository;
import com.gabojago.place.repository.CategoryRepository;
import com.gabojago.place.repository.PlaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaceQueryServiceTest {

    private final PlaceRepository placeRepository = mock(PlaceRepository.class);
    private final PlaceCategoryRepository placeCategoryRepository =
            mock(PlaceCategoryRepository.class);
    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private final PlaceQueryService placeQueryService =
            new PlaceQueryService(
                    placeRepository,
                    placeCategoryRepository,
                    categoryRepository
            );

    @Test
    void getsPlacesBySubtypeAndIncludesAllPlaceSubtypes() {
        Place place = place(1L);
        PlaceCategory outlet = placeCategory(
                place,
                category(10L, "OUTLET", "아울렛"),
                PlaceCategoryStatus.INCLUDED
        );
        PlaceCategory brand = placeCategory(
                place,
                category(11L, "BRAND_SHOP", "브랜드 매장"),
                PlaceCategoryStatus.NEED_REVIEW
        );
        PageRequest pageable = PageRequest.of(
                0,
                20,
                Sort.by(Sort.Direction.ASC, "id")
        );
        when(placeRepository.findAllByCategoryKindAndCode(
                CategoryKind.SUBTYPE,
                PlaceType.SHOP,
                "OUTLET",
                pageable
        )).thenReturn(new PageImpl<>(List.of(place), pageable, 1));
        when(placeCategoryRepository.findAllByPlaceIdsAndCategoryKind(
                List.of(1L),
                CategoryKind.SUBTYPE
        )).thenReturn(List.of(brand, outlet));

        PlacePageResponse response = placeQueryService.getPlacesBySubtype(
                PlaceType.SHOP,
                "OUTLET",
                0,
                20
        );

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().subtypes())
                .extracting("code")
                .containsExactly("BRAND_SHOP", "OUTLET");
        assertThat(response.content().getFirst().subtypes().getFirst().status())
                .isEqualTo(PlaceCategoryStatus.NEED_REVIEW);
        verify(placeRepository).findAllByCategoryKindAndCode(
                CategoryKind.SUBTYPE,
                PlaceType.SHOP,
                "OUTLET",
                pageable
        );
    }

    @Test
    void keepsPlaceTypeQueryAndIncludesPlaceSubtypes() {
        Place place = place(1L);
        PlaceCategory outlet = placeCategory(
                place,
                category(10L, "OUTLET", "아울렛"),
                PlaceCategoryStatus.INCLUDED
        );
        PageRequest pageable = PageRequest.of(
                0,
                20,
                Sort.by(Sort.Direction.ASC, "id")
        );
        when(placeRepository.findAllByPlaceType(PlaceType.SHOP, pageable))
                .thenReturn(new PageImpl<>(List.of(place), pageable, 1));
        when(placeCategoryRepository.findAllByPlaceIdsAndCategoryKind(
                List.of(1L),
                CategoryKind.SUBTYPE
        )).thenReturn(List.of(outlet));

        PlacePageResponse response = placeQueryService.getPlaces(PlaceType.SHOP, 0, 20);

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content().getFirst().subtypes())
                .extracting("code")
                .containsExactly("OUTLET");
        verify(placeRepository).findAllByPlaceType(PlaceType.SHOP, pageable);
    }

    @Test
    void getsActiveSubtypeOptionsForSelectedPlaceType() {
        Category outlet = category(10L, "OUTLET", "아울렛");
        Category convenienceStore = category(
                11L,
                "CONVENIENCE_STORE",
                "편의점"
        );
        when(categoryRepository.findAllByPlaceTypeAndKindOrderByNameAsc(
                PlaceType.SHOP,
                CategoryKind.SUBTYPE
        )).thenReturn(List.of(outlet, convenienceStore));

        var response = placeQueryService.getSubtypeOptions(PlaceType.SHOP);

        assertThat(response)
                .extracting("code", "name")
                .containsExactly(
                        tuple("OUTLET", "아울렛"),
                        tuple("CONVENIENCE_STORE", "편의점")
                );
    }

    private Place place(Long id) {
        Place place = mock(Place.class);
        Region region = mock(Region.class);
        when(place.getId()).thenReturn(id);
        when(place.getRegion()).thenReturn(region);
        when(region.getId()).thenReturn(1L);
        return place;
    }

    private Category category(Long id, String code, String name) {
        Category category = mock(Category.class);
        when(category.getId()).thenReturn(id);
        when(category.getCode()).thenReturn(code);
        when(category.getName()).thenReturn(name);
        return category;
    }

    private PlaceCategory placeCategory(
            Place place,
            Category category,
            PlaceCategoryStatus status
    ) {
        PlaceCategory placeCategory = mock(PlaceCategory.class);
        when(placeCategory.getPlace()).thenReturn(place);
        when(placeCategory.getCategory()).thenReturn(category);
        when(placeCategory.getStatus()).thenReturn(status);
        return placeCategory;
    }
}
