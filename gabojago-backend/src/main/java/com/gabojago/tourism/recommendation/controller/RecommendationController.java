package com.gabojago.tourism.recommendation.controller;

import com.gabojago.tourism.recommendation.dto.request.RouteRecommendationRequest;
import com.gabojago.tourism.recommendation.dto.request.SlotSuggestionRequest;
import com.gabojago.tourism.recommendation.dto.response.RouteRecommendationResponse;
import com.gabojago.tourism.recommendation.dto.response.SlotSuggestionResponse;
import com.gabojago.tourism.recommendation.service.RecommendationService;
import com.gabojago.tourism.recommendation.service.SlotSuggestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final SlotSuggestionService slotSuggestionService;

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
}
