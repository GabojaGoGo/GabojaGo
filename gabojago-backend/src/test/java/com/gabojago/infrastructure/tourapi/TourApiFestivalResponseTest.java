package com.gabojago.infrastructure.tourapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gabojago.infrastructure.tourapi.dto.TourApiFestivalResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TourApiFestivalResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("배열 응답의 축제를 모두 읽는다")
    void readsAllFestivalsFromArrayResponse() throws Exception {
        TourApiFestivalResponse response = read("""
                {"response":{"body":{"items":{"item":[
                  {"contentid":"1","title":"축제 A"},
                  {"contentid":"2","title":"축제 B"}
                ]}}}}
                """);

        List<TourApiFestivalResponse.Item> items = TourApiFestivalClient.extractItems(response);

        assertThat(items).extracting(TourApiFestivalResponse.Item::getContentid)
                .containsExactly("1", "2");
    }

    @Test
    @DisplayName("단건 객체 응답을 하나의 목록으로 읽는다")
    void readsSingleObjectResponseAsList() throws Exception {
        TourApiFestivalResponse response = read("""
                {"response":{"body":{"items":{"item":
                  {"contentid":"1","title":"축제 A"}
                }}}}
                """);

        List<TourApiFestivalResponse.Item> items = TourApiFestivalClient.extractItems(response);

        assertThat(items).singleElement()
                .extracting(TourApiFestivalResponse.Item::getTitle)
                .isEqualTo("축제 A");
    }

    @Test
    @DisplayName("item이 없는 응답은 빈 목록으로 처리한다")
    void returnsEmptyListWhenResponseHasNoItems() throws Exception {
        TourApiFestivalResponse response = read("""
                {"response":{"body":{"items":""}}}
                """);

        assertThat(TourApiFestivalClient.extractItems(response)).isEmpty();
    }

    private TourApiFestivalResponse read(String body) throws Exception {
        return objectMapper.readValue(body, TourApiFestivalResponse.class);
    }
}
