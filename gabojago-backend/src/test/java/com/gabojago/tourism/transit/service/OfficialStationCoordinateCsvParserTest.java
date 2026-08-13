package com.gabojago.tourism.transit.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;

class OfficialStationCoordinateCsvParserTest {

    private final OfficialStationCoordinateCsvParser parser = new OfficialStationCoordinateCsvParser();

    @Test
    @DisplayName("공식 TSV에서 역번호와 중심 좌표를 읽는다")
    void readsStationCodeAndCoordinatesFromOfficialTsv() throws Exception {
        var rows = parser.parse(new StringReader("""
                역번호\t역사명\t역위도\t역경도
                101\t신평역\t35.095179\t128.960564
                """));

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.stationCode()).isEqualTo("101");
            assertThat(row.latitude()).hasToString("35.095179");
            assertThat(row.longitude()).hasToString("128.960564");
        });
    }
}
