package com.gabojago.infrastructure.tourapi;

import com.gabojago.global.aop.TrackExecutionTime;
import com.gabojago.infrastructure.tourapi.dto.TourApiFestivalResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Collections;
import java.util.List;

/**
 * 축제 조회 전용 TourAPI 클라이언트.
 *
 * 장소 데이터는 Python 수집기가 미리 MySQL에 적재하므로 런타임에 호출하지 않는다.
 * 축제는 개최 기간이 계속 바뀌어 사전 적재하면 금방 낡기 때문에 조회 시점에 직접 호출한다.
 * 외부 API 장애가 화면 전체를 막지 않도록 실패는 빈 목록으로 처리한다.
 */
@Component
@Slf4j
@TrackExecutionTime
public class TourApiFestivalClient {

    private static final String FESTIVAL_CONTENT_TYPE_ID = "15";
    private static final int MAX_NUM_OF_ROWS = 1000;

    private final RestClient restClient;
    private final String baseUrl;
    private final String serviceKey;

    public TourApiFestivalClient(
            @Value("${tour-api.base-url}") String baseUrl,
            @Value("${tour-api.service-key}") String serviceKey) {
        this.restClient = RestClient.create();
        this.baseUrl = baseUrl;
        this.serviceKey = serviceKey;
    }

    public List<TourApiFestivalResponse.Item> fetchNearbyFestivals(
            double lat,
            double lng,
            int radiusMeter,
            int numOfRows
    ) {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl + "/locationBasedList2")
                .queryParam("serviceKey", serviceKey)
                .queryParam("numOfRows", Math.min(numOfRows, MAX_NUM_OF_ROWS))
                .queryParam("pageNo", 1)
                .queryParam("MobileOS", "ETC")
                .queryParam("MobileApp", "GabojaGO")
                .queryParam("_type", "json")
                .queryParam("mapX", lng)
                .queryParam("mapY", lat)
                .queryParam("radius", radiusMeter)
                .queryParam("contentTypeId", FESTIVAL_CONTENT_TYPE_ID)
                .build(true).toUri();

        try {
            TourApiFestivalResponse response = restClient.get()
                    .uri(uri)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(TourApiFestivalResponse.class);
            return extractItems(response);
        } catch (Exception e) {
            log.warn("TourAPI locationBasedList2 festival fetch failed lat={} lng={}: {}",
                    lat, lng, e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 결과가 없으면 items가 빈 문자열로 내려오는 등 응답 구조가 일정하지 않아 단계마다 방어한다. */
    static List<TourApiFestivalResponse.Item> extractItems(TourApiFestivalResponse response) {
        if (response == null
                || response.getResponse() == null
                || response.getResponse().getBody() == null
                || response.getResponse().getBody().getItems() == null
                || response.getResponse().getBody().getItems().getItem() == null) {
            return Collections.emptyList();
        }
        return response.getResponse().getBody().getItems().getItem();
    }
}
