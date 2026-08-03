package com.gabojago.tourism.planner.controller;

import com.gabojago.tourism.planner.dto.request.PlannerSlotOptionsRequest;
import com.gabojago.tourism.planner.dto.response.PlannerSlotOptionsResponse;
import com.gabojago.tourism.planner.service.InteractivePlannerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/planner")
@RequiredArgsConstructor
@Tag(name = "Interactive Planner", description = "편집 중인 일정의 빈 슬롯에 장소 후보를 추천합니다. "
        + "후속 미확정 슬롯 최대 두 개까지 OSRM 실제 도로 거리로 미리 평가합니다.")
public class PlannerController {

    private final InteractivePlannerService interactivePlannerService;

    @PostMapping("/slot-options")
    @Operation(
            summary = "지정 슬롯의 장소 후보 추천",
            description = "targetSlotOrder의 빈 슬롯에 후보 최대 5개를 반환합니다. 확정된 앞뒤 장소와 "
                    + "이후 미확정 슬롯 최대 두 개를 함께 평가하며, scoreBreakdown의 lookAheadApplied가 1이면 "
                    + "후속 일정 연결성까지 점수에 반영됐음을 뜻합니다. 대중교통은 아직 지원하지 않습니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "후보 추천 성공"),
            @ApiResponse(responseCode = "404", description = "regionKey에 해당하는 지역이 없음", content = @Content),
            @ApiResponse(responseCode = "422", description = "빈 슬롯 없음, 잘못된 시간 형식, 후보 부족 또는 미지원 이동수단", content = @Content)
    })
    public PlannerSlotOptionsResponse slotOptions(@RequestBody PlannerSlotOptionsRequest request) {
        return interactivePlannerService.slotOptions(request);
    }

    @PostMapping("/next-options")
    @Operation(
            summary = "첫 번째 빈 슬롯의 장소 후보 추천",
            description = "slot-options와 동일한 추천 규칙을 사용하되, targetSlotOrder 없이 order가 가장 작은 "
                    + "미확정 슬롯을 자동 선택합니다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "편집 중인 슬롯 목록. selectedPlaceId가 있으면 이미 확정된 장소입니다.",
                    content = @Content(examples = @ExampleObject(value = """
                            {
                              "regionKey": "busan",
                              "travelMode": "CAR",
                              "departureAt": "2026-08-10T10:00:00",
                              "slots": [
                                {"order": 1, "day": 1, "time": "10:00", "slotType": "SIGHT", "selectedPlaceId": 15407},
                                {"order": 2, "day": 1, "time": "13:00", "slotType": "MEAL", "subtypeCodes": ["KOREAN"]},
                                {"order": 3, "day": 1, "time": "15:00", "slotType": "CAFE"}
                              ]
                            }
                            """))
            )
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "첫 번째 빈 슬롯 후보 추천 성공"),
            @ApiResponse(responseCode = "404", description = "regionKey에 해당하는 지역이 없음", content = @Content),
            @ApiResponse(responseCode = "422", description = "추천할 빈 슬롯 없음, 후보 부족 또는 유효하지 않은 요청", content = @Content)
    })
    public PlannerSlotOptionsResponse nextOptions(@RequestBody PlannerSlotOptionsRequest request) {
        return interactivePlannerService.nextOptions(request);
    }
}
