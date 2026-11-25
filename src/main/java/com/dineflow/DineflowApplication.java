package com.dineflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class DineflowApplication {
    public static void main(String[] args) {
        // Mọi giờ trong hệ thống tính bằng UTC (giống APP_TIMEZONE=UTC); giờ quán xử lý trong Times
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(DineflowApplication.class, args);
    }
}
