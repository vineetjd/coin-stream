package com.coinstream.analytics.health;

import org.apache.kafka.streams.KafkaStreams;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.stereotype.Component;

/**
 * Reports the live Kafka Streams state to Actuator. Registered under the id
 * {@code kafkaStreams} and included in the readiness group, so a stopped/errored
 * stream surfaces as readiness DOWN instead of being masked by a healthy web
 * server. Without this, "container is Up" would not mean "actually processing".
 */
@Component("kafkaStreams")
public class KafkaStreamsHealthIndicator implements HealthIndicator {

    private final StreamsBuilderFactoryBean factoryBean;

    public KafkaStreamsHealthIndicator(StreamsBuilderFactoryBean factoryBean) {
        this.factoryBean = factoryBean;
    }

    @Override
    public Health health() {
        KafkaStreams streams = factoryBean.getKafkaStreams();
        if (streams == null) {
            return Health.down().withDetail("state", "NOT_INITIALIZED").build();
        }
        KafkaStreams.State state = streams.state();
        Health.Builder builder = state.isRunningOrRebalancing() ? Health.up() : Health.down();
        return builder.withDetail("state", state.name()).build();
    }
}
