package com.coinstream.gateway.model;

public record PriceAnalytics(
    String symbol,
    Double averagePrice,
    Long sampleCount,
    Long windowStart,
    Long windowEnd,
    Double open,
    Double high,
    Double low,
    Double close
) {}
