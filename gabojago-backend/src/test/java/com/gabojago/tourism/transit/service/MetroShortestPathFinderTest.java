package com.gabojago.tourism.transit.service;

import com.gabojago.tourism.transit.domain.MetroEdge;
import com.gabojago.tourism.transit.domain.MetroEdgeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MetroShortestPathFinderTest {

    private final MetroShortestPathFinder finder = new MetroShortestPathFinder();

    @Test
    @DisplayName("시간은 짧고 환승은 적은 경로를 선택한다")
    void selectsFasterRouteWithFewerTransfers() {
        List<MetroEdge> edges = List.of(
                MetroEdge.of("A", "B", MetroEdgeType.RIDE, 120, 1_000),
                MetroEdge.of("B", "D", MetroEdgeType.RIDE, 120, 1_000),
                MetroEdge.of("A", "C", MetroEdgeType.RIDE, 100, 900),
                MetroEdge.of("C", "D", MetroEdgeType.TRANSFER, 140, 0)
        );

        MetroShortestPathFinder.Path result = finder.find("A", "D", edges);

        assertThat(result.durationSeconds()).isEqualTo(240);
        assertThat(result.transferCount()).isZero();
        assertThat(result.edges()).extracting(MetroEdge::getToStationCode).containsExactly("B", "D");
    }

    @Test
    @DisplayName("같은 역이면 이동 간선이 없다")
    void returnsNoEdgesForSameStation() {
        MetroShortestPathFinder.Path result = finder.find("A", "A", List.of());

        assertThat(result.durationSeconds()).isZero();
        assertThat(result.edges()).isEmpty();
    }
}
