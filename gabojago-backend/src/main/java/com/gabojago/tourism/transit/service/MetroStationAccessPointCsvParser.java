package com.gabojago.tourism.transit.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** OSM에서 검수한 역 출입구 CSV를 읽는다. 형식: station_code,exit_number,latitude,longitude,osm_type,osm_id */
final class MetroStationAccessPointCsvParser {

    List<Row> parse(Reader source) throws IOException {
        BufferedReader reader = source instanceof BufferedReader buffered ? buffered : new BufferedReader(source);
        String header = reader.readLine();
        if (header == null || !header.replace("\uFEFF", "").equals("station_code,exit_number,latitude,longitude,osm_type,osm_id")) {
            throw new IllegalArgumentException("출입구 CSV 헤더는 station_code,exit_number,latitude,longitude,osm_type,osm_id 여야 합니다.");
        }
        List<Row> rows = new ArrayList<>();
        String line;
        int lineNumber = 1;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (line.isBlank()) continue;
            String[] columns = line.split(",", -1);
            if (columns.length != 6) throw new IllegalArgumentException(lineNumber + "행의 열 개수가 올바르지 않습니다.");
            String stationCode = columns[0].trim();
            String exitNumber = columns[1].trim();
            BigDecimal latitude = decimal(columns[2], lineNumber, "latitude");
            BigDecimal longitude = decimal(columns[3], lineNumber, "longitude");
            String osmType = columns[4].trim();
            long osmId;
            try { osmId = Long.parseLong(columns[5].trim()); }
            catch (NumberFormatException e) { throw new IllegalArgumentException(lineNumber + "행의 osm_id가 올바르지 않습니다."); }
            if (stationCode.isBlank() || osmType.isBlank() || osmId <= 0 || latitude.abs().compareTo(BigDecimal.valueOf(90)) > 0
                    || longitude.abs().compareTo(BigDecimal.valueOf(180)) > 0) {
                throw new IllegalArgumentException(lineNumber + "행의 출입구 값이 올바르지 않습니다.");
            }
            rows.add(new Row(stationCode, exitNumber.isBlank() ? null : exitNumber, latitude, longitude, osmType, osmId));
        }
        if (rows.isEmpty()) throw new IllegalArgumentException("출입구 CSV에 데이터가 없습니다.");
        return List.copyOf(rows);
    }

    private BigDecimal decimal(String value, int lineNumber, String name) {
        try { return new BigDecimal(value.trim()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException(lineNumber + "행의 " + name + "가 올바르지 않습니다."); }
    }

    record Row(String stationCode, String exitNumber, BigDecimal latitude, BigDecimal longitude, String osmType, long osmId) {
    }
}
