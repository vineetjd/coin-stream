package com.coinstream.ingestion.service;

import com.coinstream.ingestion.model.MarketPrice;
import com.coinstream.ingestion.port.out.PricePublisherPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MarketPriceService {

    private static final Logger log = LoggerFactory.getLogger(MarketPriceService.class);

    private final PricePublisherPort publisher;

    public MarketPriceService(PricePublisherPort publisher) {
        this.publisher = publisher;
    }

    public void processPrice(MarketPrice marketPrice) {
        // Any validation or business logic goes here
        log.debug("Processing market price: {}", marketPrice);
        publisher.sendPrice(marketPrice);
    }
}
