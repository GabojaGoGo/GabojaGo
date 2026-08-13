package com.gabojago.tourism.recommendation.service;

import com.gabojago.place.domain.Place;
import com.gabojago.tourism.recommendation.domain.RecommendationSlot;
import com.gabojago.tourism.recommendation.domain.RecommendationSlotType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SlotCandidateScoringServiceTest {

    private final SlotCandidateScoringService service = new SlotCandidateScoringService(new StubPreferenceScoreProvider());

    @Test
    @DisplayName("lookAhead가 있으면 즉시 동선과 이후 동선을 분리해 결합한다")
    void combinesImmediateAndLaterRoutesWhenLookAheadExists() {
        SlotCandidateScoringService.CandidateScore score = service.score(
                mock(Place.class), slot(), "", 1_000, 9_000
        );

        assertThat(score.breakdown())
                .containsEntry("distance", 90.9)
                .containsEntry("lookAhead", 52.6)
                .containsEntry("lookAheadApplied", 1.0);
        assertThat(score.totalScore()).isEqualTo(79.4);
    }

    @Test
    @DisplayName("lookAhead가 없으면 기존 즉시 동선 점수를 그대로 사용한다")
    void usesImmediateRouteScoreWhenLookAheadIsMissing() {
        SlotCandidateScoringService.CandidateScore score = service.score(mock(Place.class), slot(), "", 1_000);

        assertThat(score.totalScore()).isEqualTo(90.9);
        assertThat(score.breakdown()).containsEntry("lookAheadApplied", 0.0);
    }

    private RecommendationSlot slot() {
        return new RecommendationSlot(1, 1, "10:00", RecommendationSlotType.SIGHT, List.of());
    }
}
