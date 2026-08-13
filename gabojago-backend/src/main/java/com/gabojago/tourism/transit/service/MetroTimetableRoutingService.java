package com.gabojago.tourism.transit.service;

import com.gabojago.tourism.transit.domain.MetroStation;
import com.gabojago.tourism.transit.domain.MetroTrainStopTime;
import com.gabojago.tourism.transit.repository.MetroTrainStopTimeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;

/** 정적 시간표에서 특정 역·방향의 다음 열차 대기시간을 계산한다. */
@Service
@RequiredArgsConstructor
public class MetroTimetableRoutingService {

    private final MetroTrainStopTimeRepository stopTimeRepository;

    public int waitSeconds(MetroStation from, MetroStation to, LocalDateTime readyAt) {
        LocalDateTime cursor = readyAt;
        for (int dayOffset = 0; dayOffset <= 1; dayOffset++) {
            int minimum = dayOffset == 0 ? cursor.toLocalTime().toSecondOfDay() : 0;
            MetroTrainStopTime departure = stopTimeRepository.findNextDepartures(
                            dayType(cursor.getDayOfWeek()), from.getLineNumber(), from.getStationCode(), to.getStationCode(),
                            minimum, PageRequest.of(0, 1))
                    .stream().findFirst().orElse(null);
            if (departure != null) {
                LocalDateTime departureAt = cursor.toLocalDate().atStartOfDay().plusSeconds(departure.getDepartureSeconds());
                return Math.max(0, (int) java.time.Duration.between(readyAt, departureAt).getSeconds());
            }
            cursor = cursor.toLocalDate().plusDays(1).atStartOfDay();
        }
        // 시간표 누락 시에는 기존 기대 대기시간을 보수적으로 사용한다.
        return 240;
    }

    private String dayType(DayOfWeek day) {
        return switch (day) {
            case SATURDAY -> "토요일";
            case SUNDAY -> "일요일";
            default -> "평일";
        };
    }
}
