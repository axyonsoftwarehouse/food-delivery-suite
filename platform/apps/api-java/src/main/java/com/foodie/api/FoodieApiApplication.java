package com.foodie.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FoodieApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(FoodieApiApplication.class, args);
    }
}
