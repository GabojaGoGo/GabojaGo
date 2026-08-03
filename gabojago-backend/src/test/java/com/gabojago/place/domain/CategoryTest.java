package com.gabojago.place.domain;

import com.gabojago.place.domain.enums.CategoryKind;
import com.gabojago.place.domain.enums.PlacePurposeCode;
import com.gabojago.place.domain.enums.PlaceSubtypeCode;
import com.gabojago.place.domain.enums.PlaceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryTest {

    @Test
    void createsSubtypeFromEnumDefinition() {
        Category category = Category.subtype(PlaceSubtypeCode.OUTLET);

        assertThat(category.getPlaceType()).isEqualTo(PlaceType.SHOP);
        assertThat(category.getKind()).isEqualTo(CategoryKind.SUBTYPE);
        assertThat(category.getCode()).isEqualTo("OUTLET");
        assertThat(category.getName()).isEqualTo("아울렛");
    }

    @Test
    void createsGlobalPurposeFromEnumDefinition() {
        Category category = Category.purpose(PlacePurposeCode.DATE);

        assertThat(category.getPlaceType()).isNull();
        assertThat(category.getKind()).isEqualTo(CategoryKind.PURPOSE);
        assertThat(category.getCode()).isEqualTo("DATE");
        assertThat(category.getName()).isEqualTo("데이트");
    }

    @Test
    void containsCurrentSixtyFourSubtypeDefinitions() {
        assertThat(PlaceSubtypeCode.values()).hasSize(64);
    }
}
