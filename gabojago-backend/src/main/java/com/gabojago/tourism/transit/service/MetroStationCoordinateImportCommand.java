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

/** 다운로드한 부산교통공사 역사정보 TSV를 검증하거나 역 중심 좌표로 적재하는 수동 명령. */
@Component
@Slf4j
@RequiredArgsConstructor
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(name = "transit.station-coordinate-import.enabled", havingValue = "true")
public class MetroStationCoordinateImportCommand implements ApplicationRunner {

    private final MetroStationCoordinateImportService importService;

    @Value("${transit.station-coordinate-import.mode:validate}")
    private String mode;

    @Value("${transit.station-coordinate-import.path:}")
    private String sourcePath;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (sourcePath.isBlank()) throw new IllegalArgumentException("transit.station-coordinate-import.path는 필수입니다.");
        Path path = Path.of(sourcePath);
        if (!Files.isRegularFile(path)) throw new IllegalArgumentException("공식 역사정보 파일을 찾을 수 없습니다: " + path);
        try (Reader source = Files.newBufferedReader(path, Charset.forName("UTF-16LE"))) {
            int count = switch (mode) {
                case "validate" -> importService.validate(source);
                case "replace" -> importService.replaceFrom(source);
                default -> throw new IllegalArgumentException("지원하지 않는 역 좌표 적재 모드입니다: " + mode);
            };
            log.info("부산 도시철도 공식 역 중심 좌표 {} 완료: stations={}", mode, count);
        }
    }
}
