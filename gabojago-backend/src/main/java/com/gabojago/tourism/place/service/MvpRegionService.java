package com.gabojago.tourism.place.service;

import com.gabojago.tourism.place.domain.Region;
import com.gabojago.tourism.place.repository.RegionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MvpRegionService {

    private final RegionRepository regionRepository;

    @Transactional
    public List<ImportRegion> prepareBusanAndChangwon() {
        Region busan = upsertArea("부산광역시", "6", "26");
        Region gyeongnam = upsertArea("경상남도", "36", "48");
        Region changwon = upsertSigungu(gyeongnam, "창원시", "36", "16", "125");

        return List.of(
                new ImportRegion(busan, "6", null),
                new ImportRegion(changwon, "36", "16")
        );
    }

    private Region upsertArea(String name, String tourAreaCode, String datalabCode) {
        String regionKey = "TOUR:" + tourAreaCode;
        Region region = regionRepository.findByRegionKey(regionKey)
                .orElseGet(() -> Region.area(name, tourAreaCode, datalabCode));
        region.updateReferenceData(null, name, tourAreaCode, null, datalabCode);
        return regionRepository.save(region);
    }

    private Region upsertSigungu(
            Region parent,
            String name,
            String tourAreaCode,
            String tourSigunguCode,
            String datalabCode
    ) {
        String regionKey = "TOUR:" + tourAreaCode + ":" + tourSigunguCode;
        Region region = regionRepository.findByRegionKey(regionKey)
                .orElseGet(() -> Region.sigungu(
                        parent,
                        name,
                        tourAreaCode,
                        tourSigunguCode,
                        datalabCode
                ));
        region.updateReferenceData(parent, name, tourAreaCode, tourSigunguCode, datalabCode);
        return regionRepository.save(region);
    }

    public record ImportRegion(
            Region region,
            String tourAreaCode,
            String tourSigunguCode
    ) {
    }
}
