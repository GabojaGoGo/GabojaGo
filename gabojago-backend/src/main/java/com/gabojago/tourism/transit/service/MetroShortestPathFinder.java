package com.gabojago.tourism.transit.service;

import com.gabojago.tourism.transit.domain.MetroEdge;
import com.gabojago.tourism.transit.domain.MetroEdgeType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/** 시간표가 없는 정적 도시철도 그래프에서 운행·환승 시간을 최소화한다. */
public final class MetroShortestPathFinder {

    public Path find(String fromStationCode, String toStationCode, List<MetroEdge> edges) {
        if (fromStationCode.equals(toStationCode)) return new Path(0, 0, List.of());
        Map<String, List<MetroEdge>> adjacent = new HashMap<>();
        for (MetroEdge edge : edges) {
            adjacent.computeIfAbsent(edge.getFromStationCode(), ignored -> new ArrayList<>()).add(edge);
        }

        Map<String, Cost> best = new HashMap<>();
        Map<String, MetroEdge> previous = new HashMap<>();
        PriorityQueue<QueueEntry> queue = new PriorityQueue<>(Comparator.comparing(QueueEntry::cost));
        Cost start = new Cost(0, 0);
        best.put(fromStationCode, start);
        queue.add(new QueueEntry(fromStationCode, start));

        while (!queue.isEmpty()) {
            QueueEntry current = queue.poll();
            if (!current.cost.equals(best.get(current.stationCode))) continue;
            if (current.stationCode.equals(toStationCode)) break;
            for (MetroEdge edge : adjacent.getOrDefault(current.stationCode, List.of())) {
                Cost next = current.cost.add(edge);
                Cost known = best.get(edge.getToStationCode());
                if (known == null || next.compareTo(known) < 0) {
                    best.put(edge.getToStationCode(), next);
                    previous.put(edge.getToStationCode(), edge);
                    queue.add(new QueueEntry(edge.getToStationCode(), next));
                }
            }
        }

        Cost result = best.get(toStationCode);
        if (result == null) return null;
        List<MetroEdge> path = new ArrayList<>();
        String cursor = toStationCode;
        while (!cursor.equals(fromStationCode)) {
            MetroEdge edge = previous.get(cursor);
            if (edge == null) return null;
            path.add(edge);
            cursor = edge.getFromStationCode();
        }
        java.util.Collections.reverse(path);
        return new Path(result.durationSeconds, result.transferCount, List.copyOf(path));
    }

    public record Path(int durationSeconds, int transferCount, List<MetroEdge> edges) {
        public int distanceMeters() {
            return edges.stream().mapToInt(MetroEdge::getDistanceMeters).sum();
        }
    }

    private record QueueEntry(String stationCode, Cost cost) {
    }

    private record Cost(int durationSeconds, int transferCount) implements Comparable<Cost> {
        private Cost add(MetroEdge edge) {
            return new Cost(durationSeconds + edge.getDurationSeconds(),
                    transferCount + (edge.getEdgeType() == MetroEdgeType.TRANSFER ? 1 : 0));
        }

        @Override
        public int compareTo(Cost other) {
            int duration = Integer.compare(durationSeconds, other.durationSeconds);
            return duration != 0 ? duration : Integer.compare(transferCount, other.transferCount);
        }
    }
}
