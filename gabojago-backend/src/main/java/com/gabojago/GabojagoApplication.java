package com.gabojago;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
public class GabojagoApplication {
    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(GabojagoApplication.class, args);
        if (Boolean.TRUE.equals(context.getEnvironment().getProperty("transit.static-import.enabled", Boolean.class))
                || Boolean.TRUE.equals(context.getEnvironment().getProperty("transit.access-point-import.enabled", Boolean.class))
                || Boolean.TRUE.equals(context.getEnvironment().getProperty("transit.station-coordinate-import.enabled", Boolean.class))
                || Boolean.TRUE.equals(context.getEnvironment().getProperty("transit.timetable-import.enabled", Boolean.class))) {
            context.close();
        }
    }
}
