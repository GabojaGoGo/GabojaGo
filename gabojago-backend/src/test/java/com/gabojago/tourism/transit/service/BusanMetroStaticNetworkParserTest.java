package com.gabojago.tourism.transit.service;

import com.gabojago.tourism.transit.domain.MetroEdgeType;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;

class BusanMetroStaticNetworkParserTest {

    private final BusanMetroStaticNetworkParser parser = new BusanMetroStaticNetworkParser();

    @Test
    void 인접역은_양방향_탑승_간선으로_생성한다() throws Exception {
        BusanMetroStaticNetworkParser.StaticNetwork network = parser.parse(new StringReader("""
                연번,호선,역번호,역명,소요시간(분),역간거리(km),호선별누계(km)
                1,1,101,신평,00:00,0,0
                2,1,102,하단,02:15,1.5,1.5
                """));

        assertThat(network.edges()).containsExactlyInAnyOrder(
                new BusanMetroStaticNetworkParser.Edge("101", "102", MetroEdgeType.RIDE, 135, 1500),
                new BusanMetroStaticNetworkParser.Edge("102", "101", MetroEdgeType.RIDE, 135, 1500)
        );
    }

    @Test
    void 같은_이름의_서로_다른_호선은_환승_간선으로_연결한다() throws Exception {
        BusanMetroStaticNetworkParser.StaticNetwork network = parser.parse(new StringReader("""
                연번,호선,역번호,역명,소요시간(분),역간거리(km),호선별누계(km)
                1,1,119,서면,00:00,0,0
                1,2,219,서면,00:00,0,0
                """));

        assertThat(network.edges()).containsExactlyInAnyOrder(
                new BusanMetroStaticNetworkParser.Edge("119", "219", MetroEdgeType.TRANSFER, 300, 0),
                new BusanMetroStaticNetworkParser.Edge("219", "119", MetroEdgeType.TRANSFER, 300, 0)
        );
    }
}
