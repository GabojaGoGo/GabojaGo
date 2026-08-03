package com.gabojago.tourism.planner.controller;

import com.gabojago.tourism.planner.dto.request.PlannerSlotOptionsRequest;
import com.gabojago.tourism.planner.dto.response.PlannerSlotOptionsResponse;
import com.gabojago.tourism.planner.service.InteractivePlannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/planner")
@RequiredArgsConstructor
public class PlannerController {

    private final InteractivePlannerService interactivePlannerService;

    @PostMapping("/slot-options")
    public PlannerSlotOptionsResponse slotOptions(@RequestBody PlannerSlotOptionsRequest request) {
        return interactivePlannerService.slotOptions(request);
    }

    @PostMapping("/next-options")
    public PlannerSlotOptionsResponse nextOptions(@RequestBody PlannerSlotOptionsRequest request) {
        return interactivePlannerService.nextOptions(request);
    }
}
