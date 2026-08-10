package com.gabojago.tourism.route.controller;

import com.gabojago.tourism.recommendation.dto.request.RouteRecommendationRequest;
import com.gabojago.tourism.recommendation.dto.response.RouteRecommendationResponse;
import com.gabojago.tourism.route.service.SavedRouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 최종 확정한 개인 코스는 로그인 사용자에게만 저장한다. */
@RestController
@RequestMapping("/api/me/routes")
@RequiredArgsConstructor
@Tag(name = "Saved Routes", description = "로그인 사용자의 플래너 코스 생성·저장 API")
public class SavedRouteController {

    private final SavedRouteService savedRouteService;

    @Operation(
            summary = "플래너 코스 생성 및 저장",
            description = "selectedPlaceId가 있는 슬롯은 사용자가 확정한 장소로 고정합니다. 비어 있는 슬롯만 추천한 뒤, 최종 경로와 장소·이동 스냅샷을 로그인 사용자 계정에 저장합니다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @Schema(implementation = RouteRecommendationRequest.class),
                            examples = @ExampleObject(value = """
                                    {"regionKey":"busan","duration":"1n2d","travelMode":"CAR","departureAt":"2026-08-08T10:00:00","slots":[{"day":1,"timeLabel":"10:00","slotType":"SIGHT","selectedPlaceId":101},{"day":1,"timeLabel":"13:00","slotType":"MEAL"}]}
                                    """)))
    )
    @ApiResponse(responseCode = "200", description = "생성·저장된 코스")
    @ApiResponse(responseCode = "401", description = "로그인 필요")
    @PostMapping
    public RouteRecommendationResponse create(
            @AuthenticationPrincipal String userId,
            @RequestBody RouteRecommendationRequest request
    ) {
        return savedRouteService.create(userId, request);
    }
}
