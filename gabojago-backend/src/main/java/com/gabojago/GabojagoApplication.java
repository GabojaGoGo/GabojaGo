package com.gabojago.backend;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication
@EnableScheduling
public class GabojagoApplication {
    public static void main(String[] args) {
        SpringApplication.run(GabojagoApplication.class, args);
    }
}
