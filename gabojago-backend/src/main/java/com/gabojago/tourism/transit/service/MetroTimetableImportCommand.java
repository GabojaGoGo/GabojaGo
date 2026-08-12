package com.gabojago.tourism.transit.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

/** 다운로드한 운행 정보 CSV를 검증하거나 시간표 테이블로 교체 적재하는 수동 명령. */
@Component
@Slf4j
@RequiredArgsConstructor
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(name = "transit.timetable-import.enabled", havingValue = "true")
public class MetroTimetableImportCommand implements ApplicationRunner {

    private final MetroTimetableImportService importService;

    @Value("${transit.timetable-import.mode:validate}")
    private String mode;
    @Value("${transit.timetable-import.path:}")
    private String sourcePath;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (sourcePath.isBlank()) throw new IllegalArgumentException("transit.timetable-import.path는 필수입니다.");
        Path path = Path.of(sourcePath);
        if (!Files.isRegularFile(path)) throw new IllegalArgumentException("시간표 CSV 파일을 찾을 수 없습니다: " + path);
        try (Reader source = Files.newBufferedReader(path, Charset.forName("MS949"))) {
            MetroTimetableImportService.ImportResult result = switch (mode) {
                case "validate" -> importService.validate(source);
                case "replace" -> importService.replaceFrom(source);
                default -> throw new IllegalArgumentException("지원하지 않는 시간표 적재 모드입니다: " + mode);
            };
            log.info("부산 도시철도 시간표 {} 완료: trains={}, stopTimes={}", mode, result.trainCount(), result.stopTimeCount());
        }
    }
}
