package com.gabojago.festival.controller;

import com.gabojago.global.aop.TrackExecutionTime;
import com.gabojago.festival.dto.response.FestivalDto;
import com.gabojago.festival.service.FestivalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/festivals")
@RequiredArgsConstructor
@TrackExecutionTime
public class FestivalController {

    private final FestivalService festivalService;

    @GetMapping
    public List<FestivalDto> getNearbyFestivals(@RequestParam double lat, @RequestParam double lng) {
        return festivalService.getNearbyFestivals(lat, lng);
    }

}
