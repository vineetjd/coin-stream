package com.coinstream.gateway.service;

import com.coinstream.ingestion.model.MarketPrice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.lang.NonNull;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketDataConsumer {

    private final SimpMessagingTemplate messagingTemplate;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @KafkaListener(topics = "market.prices", groupId = "gateway-group")
    public void consume(@NonNull String message) {
        try {
            MarketPrice price = objectMapper.readValue(message, MarketPrice.class);
            log.info("Consumed price update: {}", price);
            messagingTemplate.convertAndSend("/topic/prices", price);
        } catch (Exception e) {
            log.error("Error parsing market price JSON: {}", e.getMessage());
        }
    }
}
