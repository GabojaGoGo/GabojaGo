package com.gabojago.infrastructure.tourapi.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TourApiResponse {
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
        private int numOfRows;
        private int pageNo;
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
        private String firstimage;
        private double mapx;
        private double mapy;
        private String areacode;
        private String sigungucode;
        @JsonProperty("lDongRegnCd")
        private String lDongRegnCd;
        @JsonProperty("lDongSignguCd")
        private String lDongSignguCd;
        private String eventstartdate;
        private String eventenddate;
    }
}
