package com.tripmate.backend.infrastructure.tourapi.service;
import com.tripmate.backend.common.aop.TrackExecutionTime;
import com.tripmate.backend.infrastructure.tourapi.TourApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.ZoneId;
@Component
@RequiredArgsConstructor
@Slf4j
@TrackExecutionTime
public class CongestionSyncScheduler {
    private final TourApiClient tourApiClient;
    @Value("${congestion.sync.zone:Asia/Seoul}")
    private String zoneId;
    @Scheduled(cron = "${congestion.sync.cron:0 50 23 * * *}", zone = "${congestion.sync.zone:Asia/Seoul}")
    public void preloadTomorrowCongestion() {
        ZoneId zone = ZoneId.of(zoneId);
        LocalDate targetDate = LocalDate.now(zone).plusDays(1).minusYears(1);
        log.info("Starting scheduled congestion sync for target date {}", targetDate);
        tourApiClient.fetchAndStoreCongestionForDate(targetDate);
    }
}
