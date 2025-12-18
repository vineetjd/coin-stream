package com.coinstream.analytics.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceAnalytics {
    private String symbol;
    private Double averagePrice;
    private Long sampleCount;
    private Long windowStart;
    private Long windowEnd;

    // ohlc
    private Double open;
    private Double high;
    private Double low;
    private Double close;
}
