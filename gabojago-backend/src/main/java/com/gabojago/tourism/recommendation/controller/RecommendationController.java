package com.gabojago.tourism.recommendation.controller;

import com.gabojago.tourism.recommendation.dto.request.RouteRecommendationRequest;
import com.gabojago.tourism.recommendation.dto.request.RoutePreviewRequest;
import com.gabojago.tourism.recommendation.dto.request.SlotSuggestionRequest;
import com.gabojago.tourism.recommendation.dto.response.RouteRecommendationResponse;
import com.gabojago.tourism.recommendation.dto.response.RoutePreviewResponse;
import com.gabojago.tourism.recommendation.dto.response.SlotSuggestionResponse;
import com.gabojago.tourism.recommendation.service.RecommendationService;
import com.gabojago.tourism.recommendation.service.RoutePreviewService;
import com.gabojago.tourism.recommendation.service.SlotSuggestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
@Tag(name = "Recommendations", description = "여행 코스 추천·교체·경로 미리보기 API")
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final SlotSuggestionService slotSuggestionService;
    private final RoutePreviewService routePreviewService;

    @PostMapping("/routes")
    public RouteRecommendationResponse recommendRoutes(
            @RequestBody(required = false) RouteRecommendationRequest request
    ) {
        return recommendationService.recommend(request);
    }

    @PostMapping("/slots/suggestions")
    public SlotSuggestionResponse suggestSlot(
            @RequestBody SlotSuggestionRequest request
    ) {
        return slotSuggestionService.suggest(request);
    }

    @Operation(
            summary = "편집된 코스의 OSRM 경로 미리보기",
            description = "장소 교체 후 일자별 장소 순서로 OSRM 도로 geometry를 다시 계산합니다. 자동차와 도보만 지원합니다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @io.swagger.v3.oas.annotations.media.Content(
                    schema = @Schema(implementation = RoutePreviewRequest.class),
                    examples = @ExampleObject(value = """
                            {"travelMode":"CAR","days":[{"day":1,"placeIds":[101,205,309]},{"day":2,"placeIds":[404,505]}]}
                            """))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "일자별 OSRM geometry 반환"),
                    @ApiResponse(responseCode = "422", description = "잘못된 장소·이동수단·좌표")
            }
    )
    @PostMapping("/routes/preview")
    public RoutePreviewResponse previewRoutes(@org.springframework.web.bind.annotation.RequestBody RoutePreviewRequest request) {
        return routePreviewService.preview(request);
    }
}
