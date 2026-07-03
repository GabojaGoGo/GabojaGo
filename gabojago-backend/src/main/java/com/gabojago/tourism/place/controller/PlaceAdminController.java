package com.gabojago.tourism.place.controller;

import com.gabojago.tourism.place.domain.enums.CurationStatus;
import com.gabojago.tourism.place.domain.enums.PlaceAttributeCode;
import com.gabojago.tourism.place.domain.enums.PlaceType;
import com.gabojago.tourism.place.domain.enums.SuitabilityTargetType;
import com.gabojago.tourism.place.dto.request.PlaceAttributeUpsertRequest;
import com.gabojago.tourism.place.dto.request.PlaceReviewRequest;
import com.gabojago.tourism.place.dto.request.PlaceSuitabilityUpsertRequest;
import com.gabojago.tourism.place.dto.response.PlaceAdminDetailResponse;
import com.gabojago.tourism.place.dto.response.PlaceAdminSummaryResponse;
import com.gabojago.tourism.place.dto.response.MvpPlaceImportResponse;
import com.gabojago.tourism.place.service.MvpPlaceImportService;
import com.gabojago.tourism.place.service.PlaceAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/place-admin")
@RequiredArgsConstructor
public class PlaceAdminController {

    private final MvpPlaceImportService mvpPlaceImportService;
    private final PlaceAdminService placeAdminService;

    @PostMapping("/import/busan-changwon")
    public MvpPlaceImportResponse importBusanAndChangwon() {
        return mvpPlaceImportService.importBusanAndChangwon();
    }

    @GetMapping("/places")
    public Page<PlaceAdminSummaryResponse> getPlaces(
            @RequestParam(required = false) String regionKey,
            @RequestParam(required = false) PlaceType primaryType,
            @RequestParam(required = false) CurationStatus curationStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        return placeAdminService.search(
                regionKey,
                primaryType,
                curationStatus,
                PageRequest.of(Math.max(page, 0), safeSize, Sort.by("id").ascending())
        );
    }

    @GetMapping("/places/{placeId}")
    public PlaceAdminDetailResponse getPlace(@PathVariable Long placeId) {
        return placeAdminService.getDetail(placeId);
    }

    @PatchMapping("/places/{placeId}/review")
    public PlaceAdminDetailResponse reviewPlace(
            @PathVariable Long placeId,
            @Valid @RequestBody PlaceReviewRequest request
    ) {
        return placeAdminService.review(placeId, request);
    }

    @PutMapping("/places/{placeId}/attributes/{attributeCode}")
    public PlaceAdminDetailResponse upsertAttribute(
            @PathVariable Long placeId,
            @PathVariable PlaceAttributeCode attributeCode,
            @Valid @RequestBody PlaceAttributeUpsertRequest request
    ) {
        return placeAdminService.upsertAttribute(placeId, attributeCode, request);
    }

    @PutMapping("/places/{placeId}/suitabilities/{targetType}/{targetCode}")
    public PlaceAdminDetailResponse upsertSuitability(
            @PathVariable Long placeId,
            @PathVariable SuitabilityTargetType targetType,
            @PathVariable String targetCode,
            @Valid @RequestBody PlaceSuitabilityUpsertRequest request
    ) {
        return placeAdminService.upsertSuitability(placeId, targetType, targetCode, request);
    }
}
