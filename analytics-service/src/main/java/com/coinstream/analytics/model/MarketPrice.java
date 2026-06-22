package com.coinstream.analytics.model;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketPrice(
    String symbol,
    BigDecimal price,
    Instant timestamp
) {}
