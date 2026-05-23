package com.tripmate.backend.infrastructure.tourapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class CongestionItemDto {
    private Long id;
    private String code;
    private String name;
    private Integer daywkDivCd;
    private String daywkDivNm;
    private String touDivCd;
    private String touDivNm;
    private Double touNum;
    private String baseYmd;
}
