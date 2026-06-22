package com.coinstream.analytics.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PriceAggregateTest {

    @Test
    void firstPriceSeedsEveryOhlcField() {
        PriceAggregate agg = new PriceAggregate().add(100.0);

        assertThat(agg.count()).isEqualTo(1);
        assertThat(agg.sum()).isEqualTo(100.0);
        assertThat(agg.open()).isEqualTo(100.0);
        assertThat(agg.close()).isEqualTo(100.0);
        assertThat(agg.min()).isEqualTo(100.0);
        assertThat(agg.max()).isEqualTo(100.0);
    }

    @Test
    void zeroPriceIsTrackedAsMax_regressionForMinValueSentinel() {
        // Regression: max was seeded with Double.MIN_VALUE (~4.9e-324, the
        // smallest *positive* double), not the most-negative value. For any
        // price <= that sentinel (e.g. 0.0) max stayed stuck at the sentinel
        // instead of the real value. NEGATIVE_INFINITY is the correct seed.
        PriceAggregate agg = new PriceAggregate().add(0.0);

        assertThat(agg.max()).isEqualTo(0.0);
        assertThat(agg.min()).isEqualTo(0.0);
    }

    @Test
    void tracksOhlcAcrossMultiplePrices() {
        PriceAggregate agg = new PriceAggregate()
                .add(100.0)   // open
                .add(120.0)   // high
                .add(90.0)    // low
                .add(110.0);  // close

        assertThat(agg.count()).isEqualTo(4);
        assertThat(agg.open()).isEqualTo(100.0);
        assertThat(agg.close()).isEqualTo(110.0);
        assertThat(agg.max()).isEqualTo(120.0);
        assertThat(agg.min()).isEqualTo(90.0);
        assertThat(agg.sum()).isEqualTo(420.0);
    }

    @Test
    void defaultAggregateIsEmpty() {
        PriceAggregate empty = new PriceAggregate();

        assertThat(empty.count()).isZero();
        assertThat(empty.sum()).isZero();
        assertThat(empty.min()).isEqualTo(Double.POSITIVE_INFINITY);
        assertThat(empty.max()).isEqualTo(Double.NEGATIVE_INFINITY);
    }
}
