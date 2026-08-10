package com.gabojago.tourism.travel.festival.service;

import com.gabojago.global.aop.TrackExecutionTime;
import com.gabojago.infrastructure.tourapi.TourApiFestivalClient;
import com.gabojago.infrastructure.tourapi.dto.TourApiFestivalResponse;
import com.gabojago.tourism.travel.festival.dto.response.FestivalDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@TrackExecutionTime
public class FestivalService {

    private static final int FESTIVAL_RADIUS_METER = 20000;
    private static final int FESTIVAL_NUM_OF_ROWS = 10;

    private final TourApiFestivalClient tourApiFestivalClient;

    // 주변 축제 정보를 조회하고, 프런트에서 쓰기 쉬운 DTO로 변환한다.
    public List<FestivalDto> getNearbyFestivals(double lat, double lng) {
        return tourApiFestivalClient
                .fetchNearbyFestivals(lat, lng, FESTIVAL_RADIUS_METER, FESTIVAL_NUM_OF_ROWS)
                .stream()
                .map(this::toFestivalDto)
                .toList();
    }

    private FestivalDto toFestivalDto(TourApiFestivalResponse.Item item) {
        return new FestivalDto(
                Long.parseLong(item.getContentid()),
                item.getTitle(),
                item.getAddr1(),
                formatDate(item.getEventstartdate()),
                formatDate(item.getEventenddate()),
                item.getMapy(),
                item.getMapx()
        );
    }

    // TourAPI 날짜 포맷(yyyyMMdd)을 화면 표시용(yyyy-MM-dd)으로 정규화한다.
    private String formatDate(String date) {
        if (date == null || date.length() != 8) {
            return date;
        }
        return String.format("%s-%s-%s", date.substring(0, 4), date.substring(4, 6), date.substring(6, 8));
    }
}
