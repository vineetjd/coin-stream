package com.coinstream.analytics;

import com.coinstream.analytics.model.MarketPrice;
import com.coinstream.analytics.model.PriceAggregate;
import com.coinstream.analytics.model.PriceAnalytics;
import com.coinstream.analytics.stream.AnalyticsTopology;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JsonSerde;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class AnalyticsTopologyTest {

    private TopologyTestDriver testDriver;
    private TestInputTopic<String, MarketPrice> inputTopic;
    private TestOutputTopic<String, PriceAnalytics> outputTopic;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        AnalyticsTopology topologyProvider = new AnalyticsTopology();
        StreamsBuilder builder = new StreamsBuilder();
        topologyProvider.kStream(builder, objectMapper);
        Topology topology = builder.build();

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, JsonSerde.class);
        props.put(org.springframework.kafka.support.serializer.JsonDeserializer.TRUSTED_PACKAGES, "*");

        testDriver = new TopologyTestDriver(topology, props);

        JsonSerde<MarketPrice> priceSerde = new JsonSerde<>(MarketPrice.class, objectMapper);
        JsonSerde<PriceAnalytics> analyticsSerde = new JsonSerde<>(PriceAnalytics.class, objectMapper);

        inputTopic = testDriver.createInputTopic("market.prices", Serdes.String().serializer(),
                priceSerde.serializer());
        outputTopic = testDriver.createOutputTopic("market.analytics", Serdes.String().deserializer(),
                analyticsSerde.deserializer());
    }

    @AfterEach
    void teardown() {
        testDriver.close();
    }

    @Test
    void shouldCalculateMovingAverage() {
        // objects
        MarketPrice p1 = MarketPrice.builder()
                .symbol("BTC")
                .price(new BigDecimal("100.00"))
                .timestamp(Instant.now())
                .build();

        MarketPrice p2 = MarketPrice.builder()
                .symbol("BTC")
                .price(new BigDecimal("200.00"))
                .timestamp(Instant.now())
                .build();

        // producer
        inputTopic.pipeInput("BTC", p1);
        inputTopic.pipeInput("BTC", p2);

        assertThat(outputTopic.isEmpty()).isFalse();

        // assert value, ticker
        PriceAnalytics result1 = outputTopic.readValue();
        assertThat(result1.getSymbol()).isEqualTo("BTC");

        // assert average
        PriceAnalytics result2 = outputTopic.readValue();
        assertThat(result2.getAveragePrice()).isEqualTo(150.00);
    }
}
