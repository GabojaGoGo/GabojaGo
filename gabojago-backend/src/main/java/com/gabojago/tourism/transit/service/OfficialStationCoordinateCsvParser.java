package com.gabojago.tourism.transit.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 부산교통공사 도시철도역사정보 TSV(UTF-16LE)의 역번호·위도·경도를 읽는다. */
final class OfficialStationCoordinateCsvParser {

    List<Row> parse(Reader source) throws IOException {
        BufferedReader reader = source instanceof BufferedReader buffered ? buffered : new BufferedReader(source);
        String headerLine = reader.readLine();
        if (headerLine == null) throw new IllegalArgumentException("공식 역사정보 파일이 비어 있습니다.");
        String[] headers = headerLine.replace("\uFEFF", "").split("\\t", -1);
        Map<String, Integer> indexes = indexes(headers);
        int stationCodeIndex = required(indexes, "역번호");
        int latitudeIndex = required(indexes, "역위도");
        int longitudeIndex = required(indexes, "역경도");
        List<Row> rows = new ArrayList<>();
        String line;
        int lineNumber = 1;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (line.isBlank()) continue;
            String[] columns = line.split("\\t", -1);
            if (columns.length != headers.length) throw new IllegalArgumentException(lineNumber + "행의 열 개수가 올바르지 않습니다.");
            String stationCode = columns[stationCodeIndex].trim();
            BigDecimal latitude = decimal(columns[latitudeIndex], lineNumber, "역위도");
            BigDecimal longitude = decimal(columns[longitudeIndex], lineNumber, "역경도");
            if (stationCode.isBlank() || latitude.abs().compareTo(BigDecimal.valueOf(90)) > 0
                    || longitude.abs().compareTo(BigDecimal.valueOf(180)) > 0) {
                throw new IllegalArgumentException(lineNumber + "행의 역번호 또는 좌표가 올바르지 않습니다.");
            }
            rows.add(new Row(stationCode, latitude, longitude));
        }
        if (rows.isEmpty()) throw new IllegalArgumentException("공식 역사정보에 좌표 데이터가 없습니다.");
        return List.copyOf(rows);
    }

    private Map<String, Integer> indexes(String[] headers) {
        Map<String, Integer> indexes = new HashMap<>();
        for (int index = 0; index < headers.length; index++) indexes.put(headers[index].trim(), index);
        return indexes;
    }

    private int required(Map<String, Integer> indexes, String header) {
        Integer index = indexes.get(header);
        if (index == null) throw new IllegalArgumentException("공식 역사정보에 " + header + " 컬럼이 없습니다.");
        return index;
    }

    private BigDecimal decimal(String value, int lineNumber, String name) {
        try { return new BigDecimal(value.trim()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException(lineNumber + "행의 " + name + "가 올바르지 않습니다."); }
    }

    record Row(String stationCode, BigDecimal latitude, BigDecimal longitude) {
    }
}
