package com.gabojago.tourism.transit.service;

import com.gabojago.tourism.transit.domain.MetroEdgeType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 부산교통공사 역간 거리·소요시간 CSV를 역 노드와 방향 간선으로 변환한다. */
final class BusanMetroStaticNetworkParser {

    static final int DEFAULT_TRANSFER_SECONDS = 300;

    StaticNetwork parse(Reader source) throws IOException {
        try (BufferedReader reader = new BufferedReader(source)) {
            String header = reader.readLine();
            if (header == null || !header.replace("\uFEFF", "").startsWith("연번,호선,역번호,역명")) {
                throw new IllegalArgumentException("부산 도시철도 CSV 헤더 형식이 올바르지 않습니다.");
            }
            List<StationRow> stations = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) stations.add(StationRow.parse(line));
            }
            return build(stations);
        }
    }

    private StaticNetwork build(List<StationRow> stations) {
        List<StationRow> ordered = stations.stream()
                .sorted(Comparator.comparingInt(StationRow::lineNumber).thenComparingInt(StationRow::sequenceNo))
                .toList();
        List<Edge> edges = new ArrayList<>();
        Map<Integer, StationRow> previousByLine = new HashMap<>();
        for (StationRow station : ordered) {
            StationRow previous = previousByLine.put(station.lineNumber(), station);
            if (previous == null) continue;
            if (station.sequenceNo() != previous.sequenceNo() + 1) {
                throw new IllegalArgumentException("호선 내 역 순서가 연속적이지 않습니다: " + station.lineNumber());
            }
            addBidirectional(edges, previous.stationCode(), station.stationCode(), MetroEdgeType.RIDE,
                    station.travelSecondsFromPrevious(), station.distanceMetersFromPrevious());
        }
        ordered.stream()
                .collect(java.util.stream.Collectors.groupingBy(StationRow::name))
                .values().stream()
                .filter(sameName -> sameName.size() > 1)
                .forEach(sameName -> connectTransfers(sameName, edges));
        return new StaticNetwork(ordered, List.copyOf(edges));
    }

    private void connectTransfers(List<StationRow> stations, List<Edge> edges) {
        for (int from = 0; from < stations.size(); from++) {
            for (int to = from + 1; to < stations.size(); to++) {
                addBidirectional(edges, stations.get(from).stationCode(), stations.get(to).stationCode(),
                        MetroEdgeType.TRANSFER, DEFAULT_TRANSFER_SECONDS, 0);
            }
        }
    }

    private void addBidirectional(List<Edge> edges, String from, String to, MetroEdgeType type, int seconds, int meters) {
        edges.add(new Edge(from, to, type, seconds, meters));
        edges.add(new Edge(to, from, type, seconds, meters));
    }

    record StaticNetwork(List<StationRow> stations, List<Edge> edges) {
    }

    record Edge(String fromStationCode, String toStationCode, MetroEdgeType type, int durationSeconds, int distanceMeters) {
    }

    record StationRow(int sequenceNo, int lineNumber, String stationCode, String name,
                      int travelSecondsFromPrevious, int distanceMetersFromPrevious, int cumulativeDistanceMeters) {
        static StationRow parse(String line) {
            String[] fields = line.split(",", -1);
            if (fields.length != 7) throw new IllegalArgumentException("CSV 열 개수가 올바르지 않습니다: " + line);
            return new StationRow(
                    Integer.parseInt(fields[0].trim()), Integer.parseInt(fields[1].trim()), fields[2].trim(), fields[3].trim(),
                    parseDuration(fields[4].trim()), kilometersToMeters(fields[5].trim()), kilometersToMeters(fields[6].trim())
            );
        }

        private static int parseDuration(String value) {
            String[] parts = value.split(":");
            if (parts.length != 2) throw new IllegalArgumentException("소요시간 형식이 올바르지 않습니다: " + value);
            return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        }

        private static int kilometersToMeters(String value) {
            return (int) Math.round(Double.parseDouble(value) * 1_000);
        }
    }
}
