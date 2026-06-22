package com.coinstream.analytics.model;

public record PriceAggregate(
    double sum,
    long count,
    double min,
    double max,
    double open,
    double close
) {
    public PriceAggregate() {
        this(0.0, 0L, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0, 0.0);
    }

    public PriceAggregate add(double price) {
        return new PriceAggregate(
            this.sum + price,
            this.count + 1,
            Math.min(this.min, price),
            Math.max(this.max, price),
            this.count == 0 ? price : this.open,
            price
        );
    }
}
