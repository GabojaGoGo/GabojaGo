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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** OSM 출입구 매핑 CSV를 검증하거나 DB에 전체 교체 적재하는 수동 명령. */
@Component
@Slf4j
@RequiredArgsConstructor
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(name = "transit.access-point-import.enabled", havingValue = "true")
public class MetroStationAccessPointImportCommand implements ApplicationRunner {

    private final MetroStationAccessPointImportService importService;

    @Value("${transit.access-point-import.mode:validate}")
    private String mode;

    @Value("${transit.access-point-import.path:}")
    private String sourcePath;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (sourcePath.isBlank()) throw new IllegalArgumentException("transit.access-point-import.path는 필수입니다.");
        Path path = Path.of(sourcePath);
        if (!Files.isRegularFile(path)) throw new IllegalArgumentException("출입구 CSV 파일을 찾을 수 없습니다: " + path);
        try (Reader source = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            int count = switch (mode) {
                case "validate" -> importService.validate(source);
                case "replace" -> importService.replaceFrom(source);
                default -> throw new IllegalArgumentException("지원하지 않는 출입구 적재 모드입니다: " + mode);
            };
            log.info("부산 도시철도 출입구 {} 완료: accessPoints={}", mode, count);
        }
    }
}
