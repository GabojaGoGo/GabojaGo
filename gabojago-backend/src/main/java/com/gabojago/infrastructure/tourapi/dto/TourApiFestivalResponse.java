package com.gabojago.infrastructure.tourapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/** locationBasedList2 응답 중 축제 조회에 필요한 필드만 매핑한다. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TourApiFestivalResponse {

    private Response response;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Response {
        private Header header;
        private Body body;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Header {
        private String resultCode;
        private String resultMsg;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Body {
        private Items items;
        private int totalCount;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Items {
        private List<Item> item;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private String contentid;
        private String title;
        private String addr1;
        private double mapx;
        private double mapy;
        private String eventstartdate;
        private String eventenddate;
    }
}
