package com.gabojago.spot.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpotCongestionDto {
    private Long id;
    private String congestion;
    private String congestionSource;
    private String congestionBaseYmd;
}
