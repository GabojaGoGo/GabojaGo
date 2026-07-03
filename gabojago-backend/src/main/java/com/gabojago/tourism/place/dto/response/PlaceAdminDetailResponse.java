package com.gabojago.tourism.place.dto.response;

import com.gabojago.tourism.place.domain.enums.AttributeSourceType;
import com.gabojago.tourism.place.domain.enums.ParkingFeeType;
import com.gabojago.tourism.place.domain.enums.PlaceAttributeCode;
import com.gabojago.tourism.place.domain.enums.SuitabilitySourceType;
import com.gabojago.tourism.place.domain.enums.SuitabilityTargetType;

import java.math.BigDecimal;
import java.util.List;

/** 알고리즘이 한 장소에 대해 읽게 될 전체 기준 데이터. */
public record PlaceAdminDetailResponse(
        PlaceAdminSummaryResponse place,
        String operatingHoursJson,
        List<AttributeValue> attributes,
        List<SuitabilityValue> suitabilities,
        ParkingValue parking,
        List<RelatedParkingValue> recommendedParking
) {
    public record AttributeValue(
            PlaceAttributeCode code,
            BigDecimal score,
            BigDecimal confidence,
            AttributeSourceType sourceType,
            String evidenceJson
    ) {
    }

    public record SuitabilityValue(
            SuitabilityTargetType targetType,
            String targetCode,
            BigDecimal score,
            BigDecimal confidence,
            SuitabilitySourceType sourceType,
            String ruleVersion,
            String evidenceJson
    ) {
    }

    public record ParkingValue(
            Integer capacityTotal,
            ParkingFeeType feeType,
            Integer baseFee,
            Integer baseMinutes,
            Integer extraFee,
            Integer extraMinutes,
            BigDecimal heightLimit
    ) {
    }

    public record RelatedParkingValue(
            Long placeId,
            String name,
            Integer distanceMeters,
            Integer walkingMinutes
    ) {
    }
}
