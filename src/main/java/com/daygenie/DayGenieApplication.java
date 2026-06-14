package com.daygenie;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DayGenieApplication {
    public static void main(String[] args) {
        SpringApplication.run(DayGenieApplication.class, args);
    }
}
