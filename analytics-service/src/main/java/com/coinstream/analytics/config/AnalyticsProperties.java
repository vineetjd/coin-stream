package com.coinstream.analytics.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe, immutable binding of the {@code analytics.*} configuration.
 * Replaces scattered {@code @Value} field injection (which forced
 * {@code ReflectionTestUtils} in tests) with a record that can be constructed
 * directly in a unit test.
 */
@ConfigurationProperties(prefix = "analytics")
public record AnalyticsProperties(
        String pricesTopic,
        String analyticsTopic,
        Window window
) {
    public record Window(Duration duration, Duration grace) {}
}
