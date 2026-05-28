package com.coinstream.ingestion.port.out;

import com.coinstream.ingestion.model.MarketPrice;

public interface PricePublisherPort {
    void sendPrice(MarketPrice marketPrice);
}
