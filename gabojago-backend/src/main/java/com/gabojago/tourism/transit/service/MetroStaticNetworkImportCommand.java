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

/** 웹 서버를 띄우지 않고 정적 도시철도 CSV를 검증·적재하는 수동 명령. */
@Component
@Slf4j
@RequiredArgsConstructor
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(name = "transit.static-import.enabled", havingValue = "true")
public class MetroStaticNetworkImportCommand implements ApplicationRunner {

    private final MetroStaticNetworkImportService importService;

    @Value("${transit.static-import.mode:validate}")
    private String mode;

    @Value("${transit.static-import.path:}")
    private String sourcePath;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (sourcePath.isBlank()) throw new IllegalArgumentException("transit.static-import.path는 필수입니다.");
        Path path = Path.of(sourcePath);
        if (!Files.isRegularFile(path)) throw new IllegalArgumentException("CSV 파일을 찾을 수 없습니다: " + path);
        try (Reader source = Files.newBufferedReader(path, Charset.forName("MS949"))) {
            MetroStaticNetworkImportService.ImportResult result = switch (mode) {
                case "validate" -> importService.validate(source);
                case "replace" -> importService.replaceFrom(source);
                default -> throw new IllegalArgumentException("지원하지 않는 적재 모드입니다: " + mode);
            };
            log.info("부산 도시철도 정적 그래프 {} 완료: stations={}, edges={}", mode, result.stationCount(), result.edgeCount());
        }
    }
}
