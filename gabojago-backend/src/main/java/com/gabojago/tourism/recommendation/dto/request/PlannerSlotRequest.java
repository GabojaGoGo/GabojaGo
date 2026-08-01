package com.gabojago.tourism.recommendation.dto.request;

import com.gabojago.tourism.recommendation.domain.RecommendationSlotType;
import java.util.List;

public record PlannerSlotRequest(int day, String timeLabel, RecommendationSlotType slotType, List<String> subtypeCodes) {}
