package com.seatlock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SeatLock {
    public static void main(String[] args) {
        SpringApplication.run(SeatLock.class, args);
    }
}
