package com.gabojago.tourism.travel.spot.controller;

import com.gabojago.global.aop.TrackExecutionTime;
import com.gabojago.tourism.travel.spot.dto.response.SpotCongestionDto;
import com.gabojago.tourism.travel.spot.dto.response.SpotDto;
import com.gabojago.tourism.travel.spot.service.SpotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    @GetMapping(value = "/raw", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getNearbySpotsRaw(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "10") int limit) {
        String raw = spotService.getNearbySpotsRaw(lat, lng, limit);
        return ResponseEntity.ok(raw == null || raw.isBlank() ? "{}" : raw);
    }

    @GetMapping("/congestion")
    public List<SpotCongestionDto> getNearbySpotCongestions(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "10") int limit) {
        return spotService.getNearbySpotCongestions(lat, lng, limit);
    }
}
