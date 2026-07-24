package com.gabojago.tourism.place.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import com.gabojago.tourism.place.domain.enums.AttributeKey;

import java.util.List;

/**
 * 장소 세분화 필드 값 입력. 키와 값 모두 AttributeKey에 정의된 어휘만 허용되며,
 * 저장 시 해당 장소의 카테고리 분류가 즉시 다시 계산된다.
 */
public record PlaceAttributeUpsertRequest(
        @NotEmpty @Valid List<AttributeEntry> attributes
) {

    public record AttributeEntry(
            @NotNull AttributeKey key,
            @NotBlank String value
    ) {
    }
}
