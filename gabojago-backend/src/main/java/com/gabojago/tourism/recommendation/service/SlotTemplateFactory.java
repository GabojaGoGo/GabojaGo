package com.gabojago.tourism.recommendation.service;

import com.gabojago.tourism.recommendation.domain.RecommendationSlot;
import com.gabojago.tourism.recommendation.domain.RecommendationSlotType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SlotTemplateFactory {

    public List<RecommendationSlot> build(String duration) {
        String normalized = duration == null || duration.isBlank() ? "1n2d" : duration;
        List<SlotSeed> seeds = switch (normalized) {
            case "day" -> List.of(
                    new SlotSeed(1, "morning", RecommendationSlotType.SIGHT),
                    new SlotSeed(1, "lunch", RecommendationSlotType.MEAL),
                    new SlotSeed(1, "afternoon", RecommendationSlotType.CAFE),
                    new SlotSeed(1, "evening", RecommendationSlotType.SIGHT)
            );
            default -> List.of(
                    new SlotSeed(1, "morning", RecommendationSlotType.SIGHT),
                    new SlotSeed(1, "lunch", RecommendationSlotType.MEAL),
                    new SlotSeed(1, "afternoon", RecommendationSlotType.CAFE),
                    new SlotSeed(1, "evening", RecommendationSlotType.SIGHT),
                    new SlotSeed(1, "night", RecommendationSlotType.LODGING),
                    new SlotSeed(2, "morning", RecommendationSlotType.CAFE),
                    new SlotSeed(2, "late_morning", RecommendationSlotType.SIGHT),
                    new SlotSeed(2, "lunch", RecommendationSlotType.MEAL),
                    new SlotSeed(2, "afternoon", RecommendationSlotType.SIGHT)
            );
        };

        List<RecommendationSlot> slots = new ArrayList<>();
        for (int i = 0; i < seeds.size(); i++) {
            SlotSeed seed = seeds.get(i);
            slots.add(new RecommendationSlot(i + 1, seed.day(), seed.timeLabel(), seed.slotType(), List.of()));
        }
        return slots;
    }

    private record SlotSeed(
            int day,
            String timeLabel,
            RecommendationSlotType slotType
    ) {
    }
}
