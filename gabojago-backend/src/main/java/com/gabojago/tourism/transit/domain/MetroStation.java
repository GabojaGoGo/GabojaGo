package com.gabojago.tourism.transit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 부산 도시철도에서 호선별로 식별되는 역 노드. */
@Entity
@Table(name = "metro_stations", indexes = {
        @Index(name = "idx_metro_station_line_sequence", columnList = "line_number,sequence_no"),
        @Index(name = "idx_metro_station_name", columnList = "name")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MetroStation {

    @Id
    @Column(name = "station_code", length = 16)
    private String stationCode;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column(name = "cumulative_distance_meters", nullable = false)
    private int cumulativeDistanceMeters;

    /** 역 대표 좌표. 출입구 단위 좌표를 확보하기 전에는 역 중심 좌표를 사용한다. */
    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    public static MetroStation of(String stationCode, int lineNumber, String name, int sequenceNo, int cumulativeDistanceMeters) {
        MetroStation station = new MetroStation();
        station.stationCode = stationCode;
        station.lineNumber = lineNumber;
        station.name = name;
        station.sequenceNo = sequenceNo;
        station.cumulativeDistanceMeters = cumulativeDistanceMeters;
        return station;
    }

    public void updateCoordinates(BigDecimal latitude, BigDecimal longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
