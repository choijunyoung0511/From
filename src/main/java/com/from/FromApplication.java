package com.from;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;


//서비스 진입점
@SpringBootApplication
@EnableScheduling
public class FromApplication {

    public static void main(String[] args) {
        SpringApplication.run(FromApplication.class, args);
    }
}