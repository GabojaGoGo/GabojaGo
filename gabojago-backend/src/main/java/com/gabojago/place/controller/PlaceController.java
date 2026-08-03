package com.gabojago.place.controller;

import com.gabojago.place.domain.enums.PlaceType;
import com.gabojago.place.dto.response.PlacePageResponse;
import com.gabojago.place.dto.response.PlaceResponse;
import com.gabojago.place.dto.response.PlaceSubtypeOptionResponse;
import com.gabojago.place.service.PlaceQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Place", description = "DB에 저장된 장소 기본 정보 조회")
@Validated
@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceQueryService placeQueryService;

    @Operation(
            summary = "PlaceType별 Place 목록 조회",
            description = "선택한 PlaceType에 해당하는 Place와 각 Place의 SUBTYPE 목록을 조회합니다."
    )
    @GetMapping
    public PlacePageResponse getPlaces(
            @RequestParam PlaceType placeType,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        // TODO: 분류 정책 확정 후 purposeCode(CategoryKind.PURPOSE) 필터를 추가한다.
        return placeQueryService.getPlaces(placeType, page, size);
    }

    @Operation(
            summary = "① PlaceType별 SUBTYPE 선택 목록 조회",
            description = "PlaceType을 선택하면 해당 대분류에서 사용할 수 있는 SUBTYPE 코드와 한글 이름을 반환합니다."
    )
    @GetMapping("/subtypes")
    public List<PlaceSubtypeOptionResponse> getSubtypeOptions(
            @RequestParam PlaceType placeType
    ) {
        return placeQueryService.getSubtypeOptions(placeType);
    }

    @Operation(
            summary = "② SUBTYPE별 Place 목록 조회",
            description = "위 SUBTYPE 선택 목록에서 확인한 코드를 입력해 해당 PlaceType의 장소를 조회합니다."
    )
    @GetMapping("/by-subtype")
    public PlacePageResponse getPlacesBySubtype(
            @RequestParam PlaceType placeType,
            @Parameter(description = "① 조회 결과에서 선택한 SUBTYPE 코드", example = "OUTLET")
            @RequestParam @NotBlank String subtypeCode,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return placeQueryService.getPlacesBySubtype(placeType, subtypeCode, page, size);
    }

    @Operation(
            summary = "Place 단건 조회",
            description = "Place ID로 장소 기본 정보와 연결된 SUBTYPE 목록을 조회합니다."
    )
    @GetMapping("/{placeId}")
    public PlaceResponse getPlace(@PathVariable Long placeId) {
        return placeQueryService.getPlace(placeId);
    }
}
