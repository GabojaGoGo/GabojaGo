package com.gabojago.tourism.transit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 두 도시철도 역 노드 사이의 방향성 이동 간선. */
@Entity
@Table(name = "metro_edges", uniqueConstraints = @UniqueConstraint(
        name = "uk_metro_edge", columnNames = {"from_station_code", "to_station_code", "edge_type"}
), indexes = @Index(name = "idx_metro_edge_from", columnList = "from_station_code"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MetroEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_station_code", nullable = false, length = 16)
    private String fromStationCode;

    @Column(name = "to_station_code", nullable = false, length = 16)
    private String toStationCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "edge_type", nullable = false, length = 16)
    private MetroEdgeType edgeType;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @Column(name = "distance_meters", nullable = false)
    private int distanceMeters;

    public static MetroEdge of(String fromStationCode, String toStationCode, MetroEdgeType edgeType,
                               int durationSeconds, int distanceMeters) {
        MetroEdge edge = new MetroEdge();
        edge.fromStationCode = fromStationCode;
        edge.toStationCode = toStationCode;
        edge.edgeType = edgeType;
        edge.durationSeconds = durationSeconds;
        edge.distanceMeters = distanceMeters;
        return edge;
    }
}
