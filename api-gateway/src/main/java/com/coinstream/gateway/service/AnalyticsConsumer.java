package com.coinstream.gateway.service;

import com.coinstream.analytics.model.PriceAnalytics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.lang.NonNull;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsConsumer {

    private final SimpMessagingTemplate messagingTemplate;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @KafkaListener(topics = "market.analytics", groupId = "gateway-analytics-group")
    public void consume(@NonNull String message) {
        try {
            PriceAnalytics analytics = objectMapper.readValue(message, PriceAnalytics.class);
            log.info("Consumed analytics update: {}", analytics);
            messagingTemplate.convertAndSend("/topic/analytics", analytics);
        } catch (Exception e) {
            log.error("Error parsing analytics JSON: {}", e.getMessage());
        }
    }
}
