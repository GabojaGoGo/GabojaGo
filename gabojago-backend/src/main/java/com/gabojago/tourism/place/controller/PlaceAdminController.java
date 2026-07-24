package com.gabojago.tourism.place.controller;

import com.gabojago.tourism.place.dto.request.PlaceAttributeUpsertRequest;
import com.gabojago.tourism.place.dto.response.MvpPlaceImportResponse;
import com.gabojago.tourism.place.dto.response.PlaceAdminDetailResponse;
import com.gabojago.tourism.place.service.MvpPlaceImportService;
import com.gabojago.tourism.place.service.PlaceAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @GetMapping("/places/{placeId}")
    public PlaceAdminDetailResponse getPlace(@PathVariable Long placeId) {
        return placeAdminService.getDetail(placeId);
    }

    @PutMapping("/places/{placeId}/attributes")
    public PlaceAdminDetailResponse upsertAttributes(@PathVariable Long placeId,
                                                     @Valid @RequestBody PlaceAttributeUpsertRequest request) {
        return placeAdminService.upsertAttributes(placeId, request);
    }
}
