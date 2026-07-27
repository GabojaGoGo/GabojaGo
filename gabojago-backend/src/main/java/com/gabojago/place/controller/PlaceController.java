package com.gabojago.place.controller;

import com.gabojago.place.dto.response.PlacePageResponse;
import com.gabojago.place.dto.response.PlaceResponse;
import com.gabojago.place.service.PlaceQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Place", description = "DB에 저장된 장소 기본 정보 조회")
@Validated
@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceQueryService placeQueryService;

    @Operation(
            summary = "Place 목록 조회",
            description = "DB에 저장된 Place 기본 정보를 ID 오름차순으로 조회합니다."
    )
    @GetMapping
    public PlacePageResponse getPlaces(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        // TODO: 분류 정책 확정 후 placeType과 purposeCode(CategoryKind.PURPOSE) 필터를 추가한다.
        return placeQueryService.getPlaces(page, size);
    }

    @Operation(summary = "Place 단건 조회", description = "Place ID로 DB의 장소 기본 정보를 조회합니다.")
    @GetMapping("/{placeId}")
    public PlaceResponse getPlace(@PathVariable Long placeId) {
        return placeQueryService.getPlace(placeId);
    }
}
