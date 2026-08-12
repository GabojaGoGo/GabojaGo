package com.gabojago.tourism.transit.service;

import com.gabojago.tourism.transit.domain.MetroStationAccessPoint;
import com.gabojago.tourism.transit.repository.MetroStationAccessPointRepository;
import com.gabojago.tourism.transit.repository.MetroStationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Set;

/** 출입구 원본을 먼저 검증하고, 전체 통과 시에만 기존 출입구 목록을 교체한다. */
@Service
@RequiredArgsConstructor
public class MetroStationAccessPointImportService {

    private final MetroStationRepository stationRepository;
    private final MetroStationAccessPointRepository accessPointRepository;
    private final MetroStationAccessPointCsvParser parser = new MetroStationAccessPointCsvParser();

    public int validate(Reader source) throws IOException {
        List<MetroStationAccessPointCsvParser.Row> rows = parser.parse(source);
        validateStationCodes(rows);
        return rows.size();
    }

    @Transactional
    public int replaceFrom(Reader source) throws IOException {
        List<MetroStationAccessPointCsvParser.Row> rows = parser.parse(source);
        validateStationCodes(rows);
        List<MetroStationAccessPoint> accessPoints = rows.stream().map(row -> MetroStationAccessPoint.of(
                row.stationCode(), row.exitNumber(), row.latitude(), row.longitude(), row.osmType(), row.osmId())).toList();
        accessPointRepository.deleteAllInBatch();
        accessPointRepository.saveAll(accessPoints);
        return accessPoints.size();
    }

    private void validateStationCodes(List<MetroStationAccessPointCsvParser.Row> rows) {
        Set<String> codes = rows.stream().map(MetroStationAccessPointCsvParser.Row::stationCode).collect(java.util.stream.Collectors.toSet());
        long stationCount = stationRepository.count();
        if (stationCount == 0 || stationCount != codes.size() || stationCount != stationRepository.findAllById(codes).size()) {
            throw new IllegalArgumentException("출입구 CSV는 현재 도시철도 모든 역을 정확히 한 번 이상 포함해야 합니다.");
        }
    }
}
