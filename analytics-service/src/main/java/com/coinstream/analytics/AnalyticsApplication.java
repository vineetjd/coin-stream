package com.coinstream.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.EnableKafkaStreams;

@SpringBootApplication
@EnableKafka
@EnableKafkaStreams
public class AnalyticsApplication {
    public static void main(String[] args) {
        SpringApplication.run(AnalyticsApplication.class, args);
    }

    @org.springframework.context.annotation.Bean
    public tools.jackson.databind.json.JsonMapper objectMapper() {
        return tools.jackson.databind.json.JsonMapper.builder().build();
    }
}
