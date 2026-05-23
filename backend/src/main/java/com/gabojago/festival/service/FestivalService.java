package com.tripmate.backend.festival.service;

import com.tripmate.backend.common.aop.TrackExecutionTime;
import com.tripmate.backend.festival.dto.FestivalDto;
import com.tripmate.backend.infrastructure.tourapi.TourApiClient;
import com.tripmate.backend.infrastructure.tourapi.dto.TourApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@TrackExecutionTime
public class FestivalService {
    private static final String FESTIVAL_CONTENT_TYPE_ID = "15";
    private static final int FESTIVAL_RADIUS_METER = 20000;

    private final TourApiClient tourApiClient;

    // 주변 축제 정보를 조회하고, 프런트에서 쓰기 쉬운 DTO로 변환한다.
    public List<FestivalDto> getNearbyFestivals(double lat, double lng) {
        TourApiResponse response = tourApiClient.fetchNearby(lat, lng, FESTIVAL_CONTENT_TYPE_ID, FESTIVAL_RADIUS_METER);
        List<TourApiResponse.Item> items = extractItems(response);
        if (items.isEmpty()) {
            return Collections.emptyList();
        }

        return items.stream()
                .map(this::toFestivalDto)
                .collect(Collectors.toList());
    }

    // 외부 응답 구조가 비정상인 경우를 방어해 빈 목록으로 처리한다.
    private List<TourApiResponse.Item> extractItems(TourApiResponse response) {
        if (response == null
                || response.getResponse() == null
                || response.getResponse().getBody() == null
                || response.getResponse().getBody().getItems() == null
                || response.getResponse().getBody().getItems().getItem() == null) {
            return Collections.emptyList();
        }
        return response.getResponse().getBody().getItems().getItem();
    }

    private FestivalDto toFestivalDto(TourApiResponse.Item item) {
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
