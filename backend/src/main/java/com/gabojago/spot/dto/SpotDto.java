package com.tripmate.backend.spot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpotDto {
    private Long id;
    private String name;
    private String address;
    private double latitude;
    private double longitude;
    private String imageUrl;
    private String congestion; // 📊 혼잡도 정보 (낮음, 보통, 높음)
    private String congestionSource;
    private String congestionBaseYmd;
}
