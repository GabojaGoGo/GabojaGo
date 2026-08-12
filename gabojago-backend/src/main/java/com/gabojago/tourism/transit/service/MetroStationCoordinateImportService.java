package com.gabojago.tourism.transit.service;

import com.gabojago.tourism.transit.domain.MetroStation;
import com.gabojago.tourism.transit.repository.MetroStationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 공식 역사정보의 114개 역 중심 좌표를 현재 정적 그래프에 반영한다. */
@Service
@RequiredArgsConstructor
public class MetroStationCoordinateImportService {

    private final MetroStationRepository stationRepository;
    private final OfficialStationCoordinateCsvParser parser = new OfficialStationCoordinateCsvParser();

    public int validate(Reader source) throws IOException {
        List<OfficialStationCoordinateCsvParser.Row> rows = parser.parse(source);
        validateStationCodes(rows);
        return rows.size();
    }

    @Transactional
    public int replaceFrom(Reader source) throws IOException {
        List<OfficialStationCoordinateCsvParser.Row> rows = parser.parse(source);
        Map<String, MetroStation> stations = validateStationCodes(rows);
        rows.forEach(row -> stations.get(row.stationCode()).updateCoordinates(row.latitude(), row.longitude()));
        return rows.size();
    }

    private Map<String, MetroStation> validateStationCodes(List<OfficialStationCoordinateCsvParser.Row> rows) {
        Map<String, OfficialStationCoordinateCsvParser.Row> byCode = rows.stream().collect(Collectors.toMap(
                OfficialStationCoordinateCsvParser.Row::stationCode, Function.identity(),
                (left, right) -> { throw new IllegalArgumentException("공식 역사정보에 중복 역번호가 있습니다: " + left.stationCode()); }
        ));
        Map<String, MetroStation> stations = stationRepository.findAll().stream()
                .collect(Collectors.toMap(MetroStation::getStationCode, Function.identity()));
        if (stations.isEmpty() || stations.size() != byCode.size() || !stations.keySet().equals(byCode.keySet())) {
            throw new IllegalArgumentException("공식 역사정보의 역번호 집합이 현재 도시철도 그래프와 일치하지 않습니다.");
        }
        return stations;
    }
}
