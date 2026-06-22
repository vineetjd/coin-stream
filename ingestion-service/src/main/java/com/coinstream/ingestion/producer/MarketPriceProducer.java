package com.coinstream.ingestion.producer;

import com.coinstream.ingestion.config.IngestionProperties;
import com.coinstream.ingestion.model.MarketPrice;
import com.coinstream.ingestion.port.out.PricePublisherPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class MarketPriceProducer implements PricePublisherPort {

    private static final Logger log = LoggerFactory.getLogger(MarketPriceProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final Counter sendFailures;
    private final String topic;

    public MarketPriceProducer(KafkaTemplate<String, Object> kafkaTemplate,
                               MeterRegistry meterRegistry,
                               IngestionProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = properties.pricesTopic();
        this.sendFailures = Counter.builder("coinstream.producer.send.failures")
                .description("Market price records that Kafka never acknowledged")
                .register(meterRegistry);
    }

    @Override
    public void sendPrice(MarketPrice marketPrice) {
        log.debug("Sending price: {}", marketPrice);
        try {
            // Async path: broker reachable but the send/ack ultimately fails
            // (e.g. delivery.timeout). The callback runs on the producer I/O
            // thread, so keep it cheap.
            kafkaTemplate.send(topic, marketPrice.symbol(), marketPrice)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            sendFailures.increment();
                            log.error("Async failure publishing price for {} to topic {}",
                                    marketPrice.symbol(), topic, ex);
                        }
                    });
        } catch (Exception ex) {
            // Synchronous path: send() can throw before returning a future —
            // producer construction (e.g. unresolvable broker), serialization,
            // or buffer-full / metadata timeout. These bypass whenComplete, so
            // count and log them here instead of letting them leak to the caller.
            sendFailures.increment();
            log.error("Synchronous failure publishing price for {} to topic {}",
                    marketPrice.symbol(), topic, ex);
        }
    }
}
