package com.gabojago.spot.controller;

import com.gabojago.global.aop.TrackExecutionTime;
import com.gabojago.spot.dto.response.SpotCongestionDto;
import com.gabojago.spot.dto.response.SpotDto;
import com.gabojago.spot.service.SpotService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/spots")
@RequiredArgsConstructor
@TrackExecutionTime
public class SpotController {

    private final SpotService spotService;

    @GetMapping
    public List<SpotDto> getNearbySpots(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "10") int limit) {
        return spotService.getNearbySpots(lat, lng, limit);
    }

    @GetMapping("/congestion")
    public List<SpotCongestionDto> getNearbySpotCongestions(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "10") int limit) {
        return spotService.getNearbySpotCongestions(lat, lng, limit);
    }
}
