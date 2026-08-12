package com.gabojago.tourism.transit.service;

import com.gabojago.tourism.transit.domain.MetroStation;
import com.gabojago.tourism.transit.domain.MetroTrainStopTime;
import com.gabojago.tourism.transit.repository.MetroStationRepository;
import com.gabojago.tourism.transit.repository.MetroTrainStopTimeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 운행 정보 CSV를 현재 도시철도 역 그래프에 연결해 시간표로 적재한다. */
@Service
@RequiredArgsConstructor
public class MetroTimetableImportService {

    private final MetroStationRepository stationRepository;
    private final MetroTrainStopTimeRepository stopTimeRepository;
    private final MetroTimetableCsvParser parser = new MetroTimetableCsvParser();

    public ImportResult validate(Reader source) throws IOException {
        return mapped(parser.parse(source));
    }

    @Transactional
    public ImportResult replaceFrom(Reader source) throws IOException {
        MetroTimetableCsvParser.Timetable timetable = parser.parse(source);
        ImportResult result = mapped(timetable);
        List<MetroTrainStopTime> values = timetable.stops().stream()
                .map(stop -> MetroTrainStopTime.of(stop.sourceDate(), stop.dayType(), stop.lineNumber(), stop.trainNumber(),
                        stop.terminalName(), result.stationCodes().get(key(stop.lineNumber(), stop.stationName())),
                        stop.stopSequence(), stop.arrivalSeconds(), stop.departureSeconds()))
                .toList();
        stopTimeRepository.deleteAllInBatch();
        stopTimeRepository.saveAll(values);
        return result;
    }

    private ImportResult mapped(MetroTimetableCsvParser.Timetable timetable) {
        Map<String, String> stationCodes = stationRepository.findAll().stream()
                .collect(Collectors.toMap(station -> key(station.getLineNumber(), station.getName()), MetroStation::getStationCode,
                        (left, right) -> left));
        if (stationCodes.isEmpty()) throw new IllegalStateException("시간표를 적재하기 전에 정적 도시철도 그래프를 적재해야 합니다.");
        List<String> missing = timetable.stops().stream()
                .filter(stop -> !stationCodes.containsKey(key(stop.lineNumber(), stop.stationName())))
                .map(stop -> stop.lineNumber() + "호선 " + stop.stationName())
                .distinct().limit(10).toList();
        if (!missing.isEmpty()) throw new IllegalArgumentException("시간표 역을 그래프에 연결하지 못했습니다: " + String.join(", ", missing));
        return new ImportResult(timetable.stops().size(), timetable.stops().stream().map(MetroTimetableCsvParser.RawStopTime::trainNumber)
                .distinct().count(), Map.copyOf(stationCodes));
    }

    private String key(int lineNumber, String stationName) {
        String normalized = stationName.replaceAll("[^가-힣0-9A-Za-z]", "").toLowerCase();
        // 운행 정보 CSV는 2024년 역명 변경 전 명칭을 사용한다.
        if (normalized.equals("국제금융부산은행")) normalized = "국제금융센터부산은행";
        return lineNumber + ":" + normalized;
    }

    public record ImportResult(int stopTimeCount, long trainCount, Map<String, String> stationCodes) { }
}
