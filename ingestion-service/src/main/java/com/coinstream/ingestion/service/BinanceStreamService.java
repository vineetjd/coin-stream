package com.coinstream.ingestion.service;

import com.coinstream.ingestion.model.MarketPrice;
import com.coinstream.ingestion.producer.MarketPriceProducer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

@Service
@Slf4j
@RequiredArgsConstructor
public class BinanceStreamService {

    private final MarketPriceProducer producer;
    private final ObjectMapper objectMapper;
    private WebSocket webSocket;
    private final OkHttpClient client = new OkHttpClient();

    private static final String BINANCE_WS_URL = "wss://stream.binance.com:9443/stream?streams=btcusdt@trade/ethusdt@trade/solusdt@trade";

    @PostConstruct
    public void connect() {
        connectWithRetry();
    }

    private void connectWithRetry() {
        Request request = new Request.Builder()
                .url(BINANCE_WS_URL)
                .build();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                log.info("Connected to Binance WebSocket");
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                try {
                    JsonNode node = objectMapper.readTree(text);
                    JsonNode data = node.get("data");

                    if (data != null) {
                        String symbol = data.get("s").asText().replace("USDT", ""); // BTCUSDT -> BTC
                        BigDecimal price = new BigDecimal(data.get("p").asText());
                        long timestamp = data.get("T").asLong();

                        MarketPrice marketPrice = MarketPrice.builder()
                                .symbol(symbol)
                                .price(price)
                                .timestamp(Instant.ofEpochMilli(timestamp))
                                .build();

                        producer.sendPrice(marketPrice);
                    }
                } catch (Exception e) {
                    log.error("Error parsing message: {}", e.getMessage());
                }
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                log.warn("Binance WebSocket closed: {}. Reconnecting in 5s...", reason);
                scheduleReconnect();
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                log.error("Binance WebSocket failure: {}. Reconnecting in 5s...", t.getMessage());
                scheduleReconnect();
            }
        });
    }

    private void scheduleReconnect() {
        try {
            // 5 sec delay
            Thread.sleep(5000);
            connectWithRetry();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @PreDestroy
    public void close() {
        if (webSocket != null) {
            webSocket.close(1000, "Shutdown");
        }
    }
}
