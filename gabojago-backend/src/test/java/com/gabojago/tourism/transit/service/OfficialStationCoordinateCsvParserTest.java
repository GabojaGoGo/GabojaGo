package com.gabojago.tourism.transit.service;

import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;

class OfficialStationCoordinateCsvParserTest {

    private final OfficialStationCoordinateCsvParser parser = new OfficialStationCoordinateCsvParser();

    @Test
    void 공식_TSV에서_역번호와_중심_좌표를_읽는다() throws Exception {
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
