package com.coinstream.analytics.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PriceAggregate {
    private double sum;
    private long count;
    private double min = Double.MAX_VALUE;
    private double max = Double.MIN_VALUE;
    private double open;
    private double close;

    public PriceAggregate add(double price) {
        if (count == 0) {
            this.open = price;
        }
        this.close = price;
        this.sum += price;
        this.count++;
        this.min = Math.min(this.min, price);
        this.max = Math.max(this.max, price);
        return this;
    }
}
