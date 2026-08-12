package com.gabojago.tourism.transit.repository;

import com.gabojago.tourism.transit.domain.MetroTrainStopTime;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MetroTrainStopTimeRepository extends JpaRepository<MetroTrainStopTime, Long> {

    @Query("""
            select departure from MetroTrainStopTime departure, MetroTrainStopTime destination
            where departure.sourceDate = destination.sourceDate
              and departure.dayType = destination.dayType
              and departure.lineNumber = destination.lineNumber
              and departure.trainNumber = destination.trainNumber
              and departure.stopSequence < destination.stopSequence
              and departure.dayType = :dayType
              and departure.lineNumber = :lineNumber
              and departure.stationCode = :fromStationCode
              and destination.stationCode = :toStationCode
              and departure.departureSeconds >= :minimumDepartureSeconds
            order by departure.departureSeconds
            """)
    List<MetroTrainStopTime> findNextDepartures(
            @Param("dayType") String dayType,
            @Param("lineNumber") int lineNumber,
            @Param("fromStationCode") String fromStationCode,
            @Param("toStationCode") String toStationCode,
            @Param("minimumDepartureSeconds") int minimumDepartureSeconds,
            Pageable pageable
    );
}
