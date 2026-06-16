package com.coinstream.gateway.service;

import com.coinstream.gateway.model.PriceAnalytics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;

import org.springframework.stereotype.Service;

@Service
public class AnalyticsConsumer {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsConsumer.class);

    private final WebSocketBroadcastService broadcastService;
    private final tools.jackson.databind.json.JsonMapper objectMapper;

    public AnalyticsConsumer(WebSocketBroadcastService broadcastService, tools.jackson.databind.json.JsonMapper objectMapper) {
        this.broadcastService = broadcastService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${kafka.topic.analytics}", groupId = "gateway-analytics-group")
    public void consume(String message) {
        try {
            PriceAnalytics analytics = objectMapper.readValue(message, PriceAnalytics.class);
            log.info("Consumed analytics update: {}", analytics);
            broadcastService.broadcastAnalytics(analytics);
        } catch (Exception e) {
            log.error("Error parsing analytics JSON: {}", e.getMessage());
        }
    }
}
