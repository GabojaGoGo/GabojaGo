package com.gabojago.tourism.transit.service;

import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetroStationAccessPointCsvParserTest {

    private final MetroStationAccessPointCsvParser parser = new MetroStationAccessPointCsvParser();

    @Test
    void 출구번호가_없는_OSM_접근점도_읽는다() throws Exception {
        var rows = parser.parse(new StringReader("""
                station_code,exit_number,latitude,longitude,osm_type,osm_id
                101,,35.0951234,128.9991234,node,12345
                """));

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.stationCode()).isEqualTo("101");
            assertThat(row.exitNumber()).isNull();
            assertThat(row.osmId()).isEqualTo(12345L);
        });
    }

    @Test
    void 올바르지_않은_좌표는_거부한다() {
        assertThatThrownBy(() -> parser.parse(new StringReader("""
                station_code,exit_number,latitude,longitude,osm_type,osm_id
                101,1,91,128.999,node,12345
                """))).isInstanceOf(IllegalArgumentException.class);
    }
}
