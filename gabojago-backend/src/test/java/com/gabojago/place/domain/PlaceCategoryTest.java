package com.gabojago.place.domain;

import com.gabojago.place.domain.enums.CategoryKind;
import com.gabojago.place.domain.enums.PlaceCategoryStatus;
import com.gabojago.place.domain.enums.PlaceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlaceCategoryTest {

    @Test
    void allowsMultipleDifferentCategoriesForOnePlace() {
        Place place = place(PlaceType.CAFE);
        Category brandCafe = subtypeCategory(PlaceType.CAFE);
        Category largeCafe = subtypeCategory(PlaceType.CAFE);

        PlaceCategory brandLink = PlaceCategory.of(
                place,
                brandCafe,
                PlaceCategoryStatus.INCLUDED
        );
        PlaceCategory largeLink = PlaceCategory.of(
                place,
                largeCafe,
                PlaceCategoryStatus.NEED_REVIEW
        );

        assertThat(brandLink.getCategory()).isSameAs(brandCafe);
        assertThat(largeLink.getCategory()).isSameAs(largeCafe);
        assertThat(brandLink.getStatus()).isEqualTo(PlaceCategoryStatus.INCLUDED);
        assertThat(largeLink.getStatus()).isEqualTo(PlaceCategoryStatus.NEED_REVIEW);
    }

    @Test
    void rejectsCategoryFromDifferentPlaceType() {
        Place place = place(PlaceType.CAFE);
        Category category = subtypeCategory(PlaceType.RESTAURANT);

        assertThatThrownBy(() -> PlaceCategory.of(
                place,
                category,
                PlaceCategoryStatus.INCLUDED
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PlaceType mismatch");
    }

    @Test
    void allowsGlobalPurposeForDifferentPlaceTypes() {
        Category date = purposeCategory();

        PlaceCategory cafeDate = PlaceCategory.of(
                place(PlaceType.CAFE),
                date,
                PlaceCategoryStatus.INCLUDED
        );
        PlaceCategory restaurantDate = PlaceCategory.of(
                place(PlaceType.RESTAURANT),
                date,
                PlaceCategoryStatus.INCLUDED
        );

        assertThat(cafeDate.getCategory()).isSameAs(date);
        assertThat(restaurantDate.getCategory()).isSameAs(date);
    }

    @Test
    void rejectsPurposeWithPlaceType() {
        Place place = place(PlaceType.CAFE);
        Category category = mock(Category.class);
        when(category.getKind()).thenReturn(CategoryKind.PURPOSE);
        when(category.getPlaceType()).thenReturn(PlaceType.CAFE);

        assertThatThrownBy(() -> PlaceCategory.of(
                place,
                category,
                PlaceCategoryStatus.INCLUDED
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not have PlaceType");
    }

    private Place place(PlaceType placeType) {
        Place place = mock(Place.class);
        when(place.getPlaceType()).thenReturn(placeType);
        return place;
    }

    private Category subtypeCategory(PlaceType placeType) {
        Category category = mock(Category.class);
        when(category.getKind()).thenReturn(CategoryKind.SUBTYPE);
        when(category.getPlaceType()).thenReturn(placeType);
        return category;
    }

    private Category purposeCategory() {
        Category category = mock(Category.class);
        when(category.getKind()).thenReturn(CategoryKind.PURPOSE);
        when(category.getPlaceType()).thenReturn(null);
        return category;
    }
}
