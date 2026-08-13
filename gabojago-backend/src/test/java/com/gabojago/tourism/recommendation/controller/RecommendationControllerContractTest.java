package com.gabojago.tourism.recommendation.controller;

import com.gabojago.global.security.filter.JwtAuthenticationFilter;
import com.gabojago.global.security.handler.CustomAccessDeniedHandler;
import com.gabojago.global.security.handler.CustomAuthenticationEntryPoint;
import com.gabojago.tourism.recommendation.dto.response.RoutePreviewResponse;
import com.gabojago.tourism.recommendation.service.RecommendationService;
import com.gabojago.tourism.recommendation.service.RoutePreviewService;
import com.gabojago.tourism.recommendation.service.SlotSuggestionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RecommendationController.class)
@AutoConfigureMockMvc(addFilters = false)
class RecommendationControllerContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RecommendationService recommendationService;

    @MockBean
    private SlotSuggestionService slotSuggestionService;

    @MockBean
    private RoutePreviewService routePreviewService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private CustomAuthenticationEntryPoint customAuthenticationEntryPoint;

    @MockBean
    private CustomAccessDeniedHandler customAccessDeniedHandler;

    @MockBean
    private JpaMetamodelMappingContext jpaMappingContext;

    @Test
    @DisplayName("편집된 코스의 일자별 OSRM 경로를 반환한다")
    void returnsDailyOsrmPathsForEditedCourse() throws Exception {
        when(routePreviewService.preview(any())).thenReturn(new RoutePreviewResponse(List.of(
                new RoutePreviewResponse.RoutePath(1, List.of(
                        new RoutePreviewResponse.RoutePoint(BigDecimal.valueOf(35.1), BigDecimal.valueOf(129.1)),
                        new RoutePreviewResponse.RoutePoint(BigDecimal.valueOf(35.2), BigDecimal.valueOf(129.2))
                ))
        )));

        mockMvc.perform(post("/api/recommendations/routes/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"travelMode":"CAR","days":[{"day":1,"placeIds":[101,205]}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routePaths[0].day").value(1))
                .andExpect(jsonPath("$.routePaths[0].points[1].lat").value(35.2))
                .andExpect(jsonPath("$.routePaths[0].points[1].lng").value(129.2));
    }
}
