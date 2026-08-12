package com.gabojago.tourism.transit.controller;

import com.gabojago.tourism.transit.service.TransitRoutingClient;
import com.gabojago.tourism.transit.dto.request.TransitRouteRequest;
import com.gabojago.tourism.transit.dto.response.TransitRouteResponse;
import com.gabojago.tourism.transit.service.BusanMetroRoutingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transit")
@RequiredArgsConstructor
public class TransitController {

    private final TransitRoutingClient transitRoutingClient;
    private final BusanMetroRoutingService busanMetroRoutingService;

    @GetMapping("/status")
    public TransitRoutingClient.TransitRoutingStatus status() {
        return transitRoutingClient.status();
    }

    @Operation(
            summary = "도보·부산 도시철도 결합 경로",
            description = "출발·도착 좌표에서 OSRM 직접 도보와 도보-도시철도-도보 경로를 비교합니다. "
                    + "시간표 적재 전에는 최초 탑승 대기시간 4분을 사용하며, 역 출입구 좌표가 적재된 뒤에만 사용할 수 있습니다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @Schema(implementation = TransitRouteRequest.class),
                            examples = @ExampleObject(value = "{\"fromLat\":35.1579,\"fromLng\":129.0595,\"toLat\":35.1632,\"toLng\":129.1636}"))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "직접 도보 또는 도시철도 결합 경로"),
                    @ApiResponse(responseCode = "422", description = "잘못된 좌표 또는 미적재 역 출입구 좌표"),
                    @ApiResponse(responseCode = "503", description = "OSRM 도보 라우팅 실패")
            }
    )
    @org.springframework.web.bind.annotation.PostMapping("/routes")
    public TransitRouteResponse route(@org.springframework.web.bind.annotation.RequestBody TransitRouteRequest request) {
        return busanMetroRoutingService.route(request);
    }
}
