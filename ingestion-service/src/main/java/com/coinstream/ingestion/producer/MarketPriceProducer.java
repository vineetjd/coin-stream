package com.coinstream.ingestion.producer;

import com.coinstream.ingestion.model.MarketPrice;
import com.coinstream.ingestion.port.out.PricePublisherPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class MarketPriceProducer implements PricePublisherPort {

    private static final Logger log = LoggerFactory.getLogger(MarketPriceProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @Value("${kafka.topic.prices}")
    private String topic;

    public MarketPriceProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override

    public void sendPrice(MarketPrice marketPrice) {
        log.info("Sending price: {}", marketPrice);
        kafkaTemplate.send(topic, marketPrice.symbol(), marketPrice);
    }
}
