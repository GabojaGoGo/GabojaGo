package com.gabojago.tourism.transit.service;

import com.gabojago.tourism.transit.domain.MetroEdge;
import com.gabojago.tourism.transit.domain.MetroEdgeType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MetroShortestPathFinderTest {

    private final MetroShortestPathFinder finder = new MetroShortestPathFinder();

    @Test
    void 시간은_짧고_환승은_적은_경로를_선택한다() {
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
    void 같은_역이면_이동_간선이_없다() {
        MetroShortestPathFinder.Path result = finder.find("A", "A", List.of());

        assertThat(result.durationSeconds()).isZero();
        assertThat(result.edges()).isEmpty();
    }
}
