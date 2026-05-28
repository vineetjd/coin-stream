package com.coinstream.gateway.service;

import com.coinstream.gateway.model.MarketPrice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;

import org.springframework.stereotype.Service;

@Service
public class MarketDataConsumer {

    private static final Logger log = LoggerFactory.getLogger(MarketDataConsumer.class);

    private final WebSocketBroadcastService broadcastService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public MarketDataConsumer(WebSocketBroadcastService broadcastService, com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.broadcastService = broadcastService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${kafka.topic.prices}", groupId = "gateway-group")
    public void consume(String message) {
        try {
            MarketPrice price = objectMapper.readValue(message, MarketPrice.class);
            log.info("Consumed price update: {}", price);
            broadcastService.broadcastPrice(price);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Error parsing market price JSON: {}", e.getMessage());
        }
    }
}
