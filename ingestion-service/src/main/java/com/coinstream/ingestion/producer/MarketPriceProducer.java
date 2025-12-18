package com.coinstream.ingestion.producer;

import com.coinstream.ingestion.model.MarketPrice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketPriceProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String TOPIC = "market.prices";

    @SuppressWarnings("null")
    public void sendPrice(MarketPrice marketPrice) {
        log.info("Sending price: {}", marketPrice);
        kafkaTemplate.send(TOPIC, marketPrice.getSymbol(), marketPrice);
    }
}
