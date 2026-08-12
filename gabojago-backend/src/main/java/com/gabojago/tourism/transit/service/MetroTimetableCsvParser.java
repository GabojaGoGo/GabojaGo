package com.gabojago.tourism.transit.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 부산교통공사 운행 정보 CSV의 열차별 정차 시각을 펼친다. */
final class MetroTimetableCsvParser {

    Timetable parse(Reader source) throws IOException {
        try (BufferedReader reader = new BufferedReader(source)) {
            String header = reader.readLine();
            if (header == null || !header.replace("\uFEFF", "").startsWith("열차번호,노선번호,노선명")) {
                throw new IllegalArgumentException("부산 도시철도 운행 정보 CSV 헤더 형식이 올바르지 않습니다.");
            }
            List<RawStopTime> stops = new ArrayList<>();
            String line;
            int rowNumber = 1;
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                if (!line.isBlank()) stops.addAll(parseRow(line, rowNumber));
            }
            if (stops.isEmpty()) throw new IllegalArgumentException("시간표 정차 시각이 없습니다.");
            return new Timetable(List.copyOf(stops));
        }
    }

    private List<RawStopTime> parseRow(String row, int rowNumber) {
        String[] fields = row.split(",", -1);
        if (fields.length != 13) throw new IllegalArgumentException("운행 정보 CSV 열 개수가 올바르지 않습니다: " + rowNumber);
        String trainNumber = fields[0].trim();
        int lineNumber = lineNumber(fields[1].trim(), rowNumber);
        String terminalName = fields[4].trim();
        String dayType = fields[6].trim();
        String[] stations = fields[7].split("\\+", -1);
        String[] arrivals = fields[8].split("\\+", -1);
        String[] departures = fields[9].split("\\+", -1);
        if (trainNumber.isBlank() || terminalName.isBlank() || dayType.isBlank()
                || arrivals.length < stations.length || departures.length != stations.length) {
            throw new IllegalArgumentException("운행 정보 정차역·시각 수가 일치하지 않습니다: " + rowNumber);
        }
        LocalDate sourceDate = LocalDate.parse(fields[12].trim());
        List<RawStopTime> values = new ArrayList<>();
        int previousTime = -1;
        for (int index = 0; index < stations.length; index++) {
            String stationName = valueAfterDash(stations[index], rowNumber);
            int arrival = normalizedTime(timeSeconds(valueAfterDash(arrivals[index], rowNumber), rowNumber), previousTime);
            String departureText = valueAfterDash(departures[index], rowNumber);
            int departure = departureText.equals(":") ? arrival
                    : normalizedTime(timeSeconds(departureText, rowNumber), arrival);
            values.add(new RawStopTime(sourceDate, dayType, lineNumber, trainNumber, terminalName,
                    stationName, index + 1, arrival, departure));
            previousTime = departure;
        }
        return values;
    }

    private int lineNumber(String value, int rowNumber) {
        if (value.isBlank() || !Character.isDigit(value.charAt(value.length() - 1))) {
            throw new IllegalArgumentException("노선번호가 올바르지 않습니다: " + rowNumber);
        }
        return Character.digit(value.charAt(value.length() - 1), 10);
    }

    private String valueAfterDash(String value, int rowNumber) {
        int divider = value.indexOf('-');
        if (divider < 0 || divider == value.length() - 1) {
            throw new IllegalArgumentException("정차역 또는 시각 형식이 올바르지 않습니다: " + rowNumber);
        }
        return value.substring(divider + 1).trim();
    }

    private int timeSeconds(String value, int rowNumber) {
        String[] parts = value.split(":", -1);
        if (parts.length != 2) throw new IllegalArgumentException("시각 형식이 올바르지 않습니다: " + rowNumber);
        try {
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) throw new NumberFormatException();
            return hour * 3600 + minute * 60;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("시각 형식이 올바르지 않습니다: " + rowNumber);
        }
    }

    private int normalizedTime(int time, int previousTime) {
        int normalized = time;
        while (previousTime >= 0 && normalized < previousTime) normalized += 24 * 60 * 60;
        return normalized;
    }

    record Timetable(List<RawStopTime> stops) { }
    record RawStopTime(LocalDate sourceDate, String dayType, int lineNumber, String trainNumber,
                       String terminalName, String stationName, int stopSequence,
                       int arrivalSeconds, int departureSeconds) { }
}
