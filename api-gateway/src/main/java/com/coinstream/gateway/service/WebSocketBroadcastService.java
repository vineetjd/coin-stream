package com.coinstream.gateway.service;

import com.coinstream.gateway.model.MarketPrice;
import com.coinstream.gateway.model.PriceAnalytics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class WebSocketBroadcastService {

    private static final Logger log = LoggerFactory.getLogger(WebSocketBroadcastService.class);

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketBroadcastService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void broadcastAnalytics(PriceAnalytics analytics) {
        log.debug("Broadcasting analytics update: {}", analytics.symbol());
        messagingTemplate.convertAndSend("/topic/analytics", analytics);
    }

    public void broadcastPrice(MarketPrice price) {
        log.debug("Broadcasting price update: {}", price.symbol());
        messagingTemplate.convertAndSend("/topic/prices", price);
    }
}
