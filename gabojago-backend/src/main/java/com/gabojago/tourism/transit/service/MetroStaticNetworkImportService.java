package com.gabojago.tourism.transit.service;

import com.gabojago.tourism.transit.domain.MetroEdge;
import com.gabojago.tourism.transit.domain.MetroStation;
import com.gabojago.tourism.transit.repository.MetroEdgeRepository;
import com.gabojago.tourism.transit.repository.MetroStationAccessPointRepository;
import com.gabojago.tourism.transit.repository.MetroStationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.Reader;
import java.util.List;

/** 검증된 CSV 전체를 한 트랜잭션으로 교체해 정적 도시철도 그래프를 구축한다. */
@Service
@RequiredArgsConstructor
public class MetroStaticNetworkImportService {

    private final MetroStationRepository stationRepository;
    private final MetroEdgeRepository edgeRepository;
    private final MetroStationAccessPointRepository accessPointRepository;
    private final BusanMetroStaticNetworkParser parser = new BusanMetroStaticNetworkParser();

    public ImportResult validate(Reader source) throws IOException {
        BusanMetroStaticNetworkParser.StaticNetwork network = parser.parse(source);
        return new ImportResult(network.stations().size(), network.edges().size());
    }

    @Transactional
    public ImportResult replaceFrom(Reader source) throws IOException {
        BusanMetroStaticNetworkParser.StaticNetwork network = parser.parse(source);
        List<MetroStation> stations = network.stations().stream()
                .map(row -> MetroStation.of(row.stationCode(), row.lineNumber(), row.name(), row.sequenceNo(), row.cumulativeDistanceMeters()))
                .toList();
        List<MetroEdge> edges = network.edges().stream()
                .map(edge -> MetroEdge.of(edge.fromStationCode(), edge.toStationCode(), edge.type(), edge.durationSeconds(), edge.distanceMeters()))
                .toList();
        edgeRepository.deleteAllInBatch();
        accessPointRepository.deleteAllInBatch();
        stationRepository.deleteAllInBatch();
        stationRepository.saveAll(stations);
        edgeRepository.saveAll(edges);
        return new ImportResult(stations.size(), edges.size());
    }

    public record ImportResult(int stationCount, int edgeCount) {
    }
}
