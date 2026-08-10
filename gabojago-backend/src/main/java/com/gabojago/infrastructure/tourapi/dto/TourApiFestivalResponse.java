package com.gabojago.infrastructure.tourapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;

import java.io.IOException;
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
        @JsonDeserialize(using = EmptyStringAsNullItemsDeserializer.class)
        private Items items;
        private int totalCount;
    }

    /** TourAPI는 결과가 없을 때 items를 객체 대신 빈 문자열로 내려준다. */
    public static class EmptyStringAsNullItemsDeserializer extends JsonDeserializer<Items> {
        @Override
        public Items deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            JsonNode node = parser.getCodec().readTree(parser);
            if (node == null || node.isNull() || (node.isTextual() && node.asText().isBlank())) {
                return null;
            }
            return parser.getCodec().treeToValue(node, Items.class);
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Items {
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
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
