package com.coinstream.ingestion.adapter.in;

import com.coinstream.ingestion.model.MarketPrice;
import com.coinstream.ingestion.service.MarketPriceService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class BinanceWebSocketAdapter {

    private static final Logger log = LoggerFactory.getLogger(BinanceWebSocketAdapter.class);

    private final MarketPriceService marketPriceService;
    private final JsonMapper objectMapper;
    private final OkHttpClient client;
    private final ScheduledExecutorService scheduler;
    
    private WebSocket webSocket;

    public BinanceWebSocketAdapter(MarketPriceService marketPriceService, JsonMapper objectMapper, OkHttpClient client, ScheduledExecutorService scheduler) {
        this.marketPriceService = marketPriceService;
        this.objectMapper = objectMapper;
        this.client = client;
        this.scheduler = scheduler;
    }

    @Value("${binance.ws.url}")
    private String binanceWsUrl;

    @Value("${binance.ws.reconnect.delay.seconds}")
    private long reconnectDelaySec;

    @PostConstruct
    public void connect() {
        connectWithRetry();
    }

    private void connectWithRetry() {
        Request request = new Request.Builder()
                .url(binanceWsUrl)
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
                        String symbol = data.get("s").asString().replace("USDT", ""); // BTCUSDT -> BTC
                        BigDecimal price = new BigDecimal(data.get("p").asString());
                        long timestamp = data.get("T").asLong();

                        MarketPrice marketPrice = new MarketPrice(
                                symbol,
                                price,
                                Instant.ofEpochMilli(timestamp)
                        );

                        marketPriceService.processPrice(marketPrice);
                    }
                } catch (Exception e) {
                    log.error("Error parsing message: {}", e.getMessage());
                }
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                log.warn("Binance WebSocket closed: {}. Reconnecting in {}s...", reason, reconnectDelaySec);
                scheduleReconnect();
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                log.error("Binance WebSocket failure: {}. Reconnecting in {}s...", t.getMessage(), reconnectDelaySec);
                scheduleReconnect();
            }
        });
    }

    private void scheduleReconnect() {
        scheduler.schedule(this::connectWithRetry, reconnectDelaySec, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void close() {
        if (webSocket != null) {
            webSocket.close(1000, "Shutdown");
        }
        scheduler.shutdownNow();
    }
}
