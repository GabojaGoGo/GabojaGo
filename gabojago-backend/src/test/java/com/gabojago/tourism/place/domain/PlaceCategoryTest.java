package com.gabojago.tourism.place.domain;

import com.gabojago.tourism.place.domain.enums.CategoryAssignmentType;
import com.gabojago.tourism.place.domain.enums.PlaceCategoryStatus;
import com.gabojago.tourism.place.domain.enums.PlaceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlaceCategoryTest {

    @Test
    void allowsMultipleDifferentCategoriesForOnePlace() {
        Place place = place(PlaceType.CAFE);
        Category brandCafe = category(PlaceType.CAFE);
        Category largeCafe = category(PlaceType.CAFE);

        PlaceCategory brandLink = PlaceCategory.of(
                place,
                brandCafe,
                PlaceCategoryStatus.INCLUDED,
                CategoryAssignmentType.IMPORTED
        );
        PlaceCategory largeLink = PlaceCategory.of(
                place,
                largeCafe,
                PlaceCategoryStatus.NEED_REVIEW,
                CategoryAssignmentType.MANUAL
        );

        assertThat(brandLink.getCategory()).isSameAs(brandCafe);
        assertThat(largeLink.getCategory()).isSameAs(largeCafe);
        assertThat(brandLink.getAssignmentType()).isEqualTo(CategoryAssignmentType.IMPORTED);
        assertThat(largeLink.getAssignmentType()).isEqualTo(CategoryAssignmentType.MANUAL);
    }

    @Test
    void rejectsCategoryFromDifferentPlaceType() {
        Place place = place(PlaceType.CAFE);
        Category category = category(PlaceType.RESTAURANT);

        assertThatThrownBy(() -> PlaceCategory.of(
                place,
                category,
                PlaceCategoryStatus.INCLUDED,
                CategoryAssignmentType.MANUAL
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PlaceType mismatch");
    }

    private Place place(PlaceType placeType) {
        Place place = mock(Place.class);
        when(place.getPlaceType()).thenReturn(placeType);
        return place;
    }

    private Category category(PlaceType placeType) {
        Category category = mock(Category.class);
        when(category.getPlaceType()).thenReturn(placeType);
        when(category.isActive()).thenReturn(true);
        return category;
    }
}
