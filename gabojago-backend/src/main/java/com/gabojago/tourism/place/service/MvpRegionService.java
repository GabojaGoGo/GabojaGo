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
        Region busan = upsertArea("busan", "부산광역시", "6", "26");
        Region gyeongnam = upsertArea("gyeongnam", "경상남도", "36", "48");
        Region changwon = upsertSigungu(gyeongnam, "gyeongnam.changwon", "창원시", "36", "16", "125");

        return List.of(
                new ImportRegion(busan.getId(), busan.getName(), "6", null),
                new ImportRegion(changwon.getId(), changwon.getName(), "36", "16")
        );
    }

    private Region upsertArea(String regionKey, String name, String tourAreaCode, String datalabCode) {
        Region region = regionRepository.findByRegionKey(regionKey)
                .orElseGet(() -> Region.area(regionKey, name));
        region.updateName(null, name);
        region.assignTourApiMapping(tourAreaCode, null);
        region.assignDatalabCode(datalabCode);
        return regionRepository.save(region);
    }

    private Region upsertSigungu(
            Region parent,
            String regionKey,
            String name,
            String tourAreaCode,
            String tourSigunguCode,
            String datalabCode
    ) {
        Region region = regionRepository.findByRegionKey(regionKey)
                .orElseGet(() -> Region.sigungu(parent, regionKey, name));
        region.updateName(parent, name);
        region.assignTourApiMapping(tourAreaCode, tourSigunguCode);
        region.assignDatalabCode(datalabCode);
        return regionRepository.save(region);
    }

    public record ImportRegion(
            Long regionId,
            String regionName,
            String tourAreaCode,
            String tourSigunguCode
    ) {
    }
}
