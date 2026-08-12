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

import java.time.LocalDate;

/** 한 운행편이 특정 역에 도착·출발하는 정적 시간표 행. */
@Entity
@Table(name = "metro_train_stop_times", uniqueConstraints = @UniqueConstraint(
        name = "uk_metro_train_stop_time",
        columnNames = {"source_date", "day_type", "line_number", "train_number", "station_code"}
), indexes = {
        @Index(name = "idx_metro_stop_time_lookup", columnList = "day_type,line_number,station_code,departure_seconds"),
        @Index(name = "idx_metro_stop_time_trip", columnList = "source_date,day_type,line_number,train_number,stop_sequence")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MetroTrainStopTime {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_date", nullable = false)
    private LocalDate sourceDate;
    @Column(name = "day_type", nullable = false, length = 16)
    private String dayType;
    @Column(name = "line_number", nullable = false)
    private int lineNumber;
    @Column(name = "train_number", nullable = false, length = 16)
    private String trainNumber;
    @Column(name = "terminal_name", nullable = false, length = 100)
    private String terminalName;
    @Column(name = "station_code", nullable = false, length = 16)
    private String stationCode;
    @Column(name = "stop_sequence", nullable = false)
    private int stopSequence;
    @Column(name = "arrival_seconds", nullable = false)
    private int arrivalSeconds;
    @Column(name = "departure_seconds", nullable = false)
    private int departureSeconds;

    public static MetroTrainStopTime of(LocalDate sourceDate, String dayType, int lineNumber, String trainNumber,
                                        String terminalName, String stationCode, int stopSequence,
                                        int arrivalSeconds, int departureSeconds) {
        MetroTrainStopTime value = new MetroTrainStopTime();
        value.sourceDate = sourceDate;
        value.dayType = dayType;
        value.lineNumber = lineNumber;
        value.trainNumber = trainNumber;
        value.terminalName = terminalName;
        value.stationCode = stationCode;
        value.stopSequence = stopSequence;
        value.arrivalSeconds = arrivalSeconds;
        value.departureSeconds = departureSeconds;
        return value;
    }
}
