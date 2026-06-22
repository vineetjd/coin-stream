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
    private final tools.jackson.databind.json.JsonMapper objectMapper;

    public MarketDataConsumer(WebSocketBroadcastService broadcastService, tools.jackson.databind.json.JsonMapper objectMapper) {
        this.broadcastService = broadcastService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${kafka.topic.prices}", groupId = "gateway-group")
    public void consume(String message) {
        // No try/catch: let a parse failure propagate so the container's
        // DefaultErrorHandler retries it and routes it to market.prices.dlt.
        MarketPrice price = objectMapper.readValue(message, MarketPrice.class);
        log.debug("Consumed price update: {}", price);
        broadcastService.broadcastPrice(price);
    }
}
