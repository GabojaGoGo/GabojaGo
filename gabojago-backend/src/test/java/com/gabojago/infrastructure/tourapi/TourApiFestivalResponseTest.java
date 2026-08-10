package com.gabojago.infrastructure.tourapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gabojago.infrastructure.tourapi.dto.TourApiFestivalResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TourApiFestivalResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 배열_응답의_축제를_모두_읽는다() throws Exception {
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
    void 단건_객체_응답을_하나의_목록으로_읽는다() throws Exception {
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
    void item이_없는_응답은_빈_목록으로_처리한다() throws Exception {
        TourApiFestivalResponse response = read("""
                {"response":{"body":{"items":""}}}
                """);

        assertThat(TourApiFestivalClient.extractItems(response)).isEmpty();
    }

    private TourApiFestivalResponse read(String body) throws Exception {
        return objectMapper.readValue(body, TourApiFestivalResponse.class);
    }
}
