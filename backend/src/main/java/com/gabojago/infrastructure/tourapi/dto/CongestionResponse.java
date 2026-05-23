package com.tripmate.backend.infrastructure.tourapi.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.util.List;

@Data
public class CongestionResponse {
    private Response response;

    @Data
    public static class Response {
        private Body body;
    }

    @Data
    public static class Body {
        private Items items;
    }

    @Data
    public static class Items {
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        private List<Item> item;
    }

    @Data
    public static class Item {
        private String signguCode;
        private String signguNm;
        private String touDivCd;
        private String touDivNm;
        private double touNum;
    }
}
