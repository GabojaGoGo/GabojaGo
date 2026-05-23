package com.tripmate.backend.infrastructure.tourapi.service;
import com.tripmate.backend.common.aop.TrackExecutionTime;
import com.tripmate.backend.infrastructure.tourapi.domain.AreaDailyTotal;
import com.tripmate.backend.infrastructure.tourapi.domain.AreaDailyTotalRepository;
import com.tripmate.backend.infrastructure.tourapi.domain.SigunguDailyTotal;
import com.tripmate.backend.infrastructure.tourapi.domain.SigunguDailyTotalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
@Service
@RequiredArgsConstructor
@TrackExecutionTime
public class CongestionDailyQueryService {
    private final SigunguDailyTotalRepository sigunguDailyTotalRepository;
    private final AreaDailyTotalRepository areaDailyTotalRepository;
    @Value("${congestion.sync.zone:Asia/Seoul}")
    private String zoneId;
    public DailySnapshot findTodaySnapshot() {
        String targetBaseYmd = LocalDate.now(ZoneId.of(zoneId))
                .minusYears(1)
                .format(DateTimeFormatter.BASIC_ISO_DATE);
        List<SigunguDailyTotal> sigunguTotals = sigunguDailyTotalRepository.findAllByBaseYmd(targetBaseYmd);
        List<AreaDailyTotal> areaTotals = areaDailyTotalRepository.findAllByBaseYmd(targetBaseYmd);
        return new DailySnapshot(
                targetBaseYmd,
                toSigunguMap(sigunguTotals),
                toAreaMap(areaTotals)
        );
    }
    private Map<String, Double> toSigunguMap(List<SigunguDailyTotal> totals) {
        Map<String, Double> result = new LinkedHashMap<>();
        for (SigunguDailyTotal total : totals) {
            result.put(total.getSignguCode(), total.getTotalTouNum());
        }
        return result;
    }
    private Map<String, Double> toAreaMap(List<AreaDailyTotal> totals) {
        Map<String, Double> result = new LinkedHashMap<>();
        for (AreaDailyTotal total : totals) {
            result.put(total.getAreaCode(), total.getTotalTouNum());
        }
        return result;
    }
    public record DailySnapshot(
            String baseYmd,
            Map<String, Double> sigunguTotals,
            Map<String, Double> areaTotals
    ) {
        public boolean hasData() {
            return !sigunguTotals.isEmpty() || !areaTotals.isEmpty();
        }
    }
}
