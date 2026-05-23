package com.tripmate.backend.infrastructure.region;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
public class RegionCodeMapper {

    private final ObjectMapper objectMapper;
    private final Map<String, String> areaCodeMap = new HashMap<>();
    private final Map<String, String> sigunguCodeMap = new HashMap<>();

    public RegionCodeMapper() {
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    void load() {
        try (InputStream inputStream = new ClassPathResource("tourapi-datalab-region-mapping.json").getInputStream()) {
            MappingRoot root = objectMapper.readValue(inputStream, MappingRoot.class);
            if (root.getAreaMappings() != null) {
                root.getAreaMappings().forEach(entry -> areaCodeMap.put(entry.getTourAreaCode(), entry.getDatalabAreaCode()));
            }
            if (root.getSigunguMappings() != null) {
                root.getSigunguMappings().forEach(entry ->
                        sigunguCodeMap.put(sigunguKey(entry.getTourAreaCode(), entry.getTourSigunguCode()), entry.getDatalabSigunguCode()));
            }
            log.info("Loaded region mappings: area={}, sigungu={}", areaCodeMap.size(), sigunguCodeMap.size());
        } catch (Exception e) {
            log.warn("Failed to load region mappings: {}", e.getMessage());
        }
    }

    public Optional<String> mapAreaCode(String tourAreaCode) {
        return Optional.ofNullable(areaCodeMap.get(tourAreaCode));
    }

    public Optional<String> mapSigunguCode(String tourAreaCode, String tourSigunguCode) {
        return Optional.ofNullable(sigunguCodeMap.get(sigunguKey(tourAreaCode, tourSigunguCode)));
    }

    private String sigunguKey(String tourAreaCode, String tourSigunguCode) {
        return tourAreaCode + ":" + tourSigunguCode;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class MappingRoot {
        private List<AreaMapping> areaMappings;
        private List<SigunguMapping> sigunguMappings;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class AreaMapping {
        private String tourAreaCode;
        private String datalabAreaCode;
        private String name;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SigunguMapping {
        private String tourAreaCode;
        private String tourSigunguCode;
        private String datalabSigunguCode;
        private String name;
    }
}
