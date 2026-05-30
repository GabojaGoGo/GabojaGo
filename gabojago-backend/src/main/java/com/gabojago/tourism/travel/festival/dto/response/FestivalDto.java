package com.gabojago.tourism.travel.festival.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FestivalDto {
    private Long id;
    private String title;
    private String location;
    private String startDate;
    private String endDate;
    private double latitude;
    private double longitude;
}
