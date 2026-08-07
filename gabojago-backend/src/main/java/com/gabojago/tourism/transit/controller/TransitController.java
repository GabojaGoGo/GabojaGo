package com.gabojago.tourism.transit.controller;

import com.gabojago.tourism.transit.service.TransitRoutingClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transit")
@RequiredArgsConstructor
public class TransitController {

    private final TransitRoutingClient transitRoutingClient;

    @GetMapping("/status")
    public TransitRoutingClient.TransitRoutingStatus status() {
        return transitRoutingClient.status();
    }
}
