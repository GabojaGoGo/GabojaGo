package com.gabojago.tourism.transit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 도시철도 역과 연결되는 실제 보행 접근점. 출구 번호가 없더라도 OSM 출입구 좌표는 저장한다. */
@Entity
@Table(name = "metro_station_access_points", uniqueConstraints = {
        @UniqueConstraint(name = "uk_metro_access_osm", columnNames = {"osm_type", "osm_id"})
}, indexes = {
        @Index(name = "idx_metro_access_station", columnList = "station_code"),
        @Index(name = "idx_metro_access_coordinate", columnList = "latitude,longitude")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MetroStationAccessPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "station_code", nullable = false, length = 16)
    private String stationCode;

    @Column(name = "exit_number", length = 32)
    private String exitNumber;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "osm_type", nullable = false, length = 16)
    private String osmType;

    @Column(name = "osm_id", nullable = false)
    private long osmId;

    public static MetroStationAccessPoint of(String stationCode, String exitNumber, BigDecimal latitude,
                                             BigDecimal longitude, String osmType, long osmId) {
        MetroStationAccessPoint accessPoint = new MetroStationAccessPoint();
        accessPoint.stationCode = stationCode;
        accessPoint.exitNumber = exitNumber;
        accessPoint.latitude = latitude;
        accessPoint.longitude = longitude;
        accessPoint.osmType = osmType;
        accessPoint.osmId = osmId;
        return accessPoint;
    }
}
