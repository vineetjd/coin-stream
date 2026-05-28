package com.coinstream.analytics;

import com.coinstream.analytics.model.MarketPrice;

import com.coinstream.analytics.model.PriceAnalytics;
import com.coinstream.analytics.stream.AnalyticsTopology;
import tools.jackson.databind.json.JsonMapper;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JacksonJsonSerde;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class AnalyticsTopologyTest {

    private TopologyTestDriver testDriver;
    private TestInputTopic<String, MarketPrice> inputTopic;
    private TestOutputTopic<String, PriceAnalytics> outputTopic;
    private JsonMapper objectMapper;

    @BeforeEach
    void setup() {
        objectMapper = JsonMapper.builder().build();

        AnalyticsTopology topologyProvider = new AnalyticsTopology();
        org.springframework.test.util.ReflectionTestUtils.setField(topologyProvider, "pricesTopic", "market.prices");
        org.springframework.test.util.ReflectionTestUtils.setField(topologyProvider, "analyticsTopic", "market.analytics");
        org.springframework.test.util.ReflectionTestUtils.setField(topologyProvider, "windowDurationSec", 60L);
        org.springframework.test.util.ReflectionTestUtils.setField(topologyProvider, "windowGraceSec", 5L);

        StreamsBuilder builder = new StreamsBuilder();
        topologyProvider.buildAnalyticsTopology(builder, objectMapper);
        Topology topology = builder.build();

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, JacksonJsonSerde.class);
        props.put(org.springframework.kafka.support.serializer.JacksonJsonDeserializer.TRUSTED_PACKAGES, "*");

        testDriver = new TopologyTestDriver(topology, props);

        try (JacksonJsonSerde<MarketPrice> priceSerde = new JacksonJsonSerde<>(MarketPrice.class, objectMapper);
             JacksonJsonSerde<PriceAnalytics> analyticsSerde = new JacksonJsonSerde<>(PriceAnalytics.class, objectMapper)) {

            inputTopic = testDriver.createInputTopic("market.prices", Serdes.String().serializer(),
                    priceSerde.serializer());
            outputTopic = testDriver.createOutputTopic("market.analytics", Serdes.String().deserializer(),
                    analyticsSerde.deserializer());
        }
    }

    @AfterEach
    void teardown() {
        testDriver.close();
    }

    @Test
    void shouldCalculateMovingAverage() {
        // objects
        MarketPrice p1 = new MarketPrice(
                "BTC",
                new BigDecimal("100.00"),
                Instant.now()
        );

        MarketPrice p2 = new MarketPrice(
                "BTC",
                new BigDecimal("200.00"),
                Instant.now()
        );

        // producer
        inputTopic.pipeInput("BTC", p1);
        inputTopic.pipeInput("BTC", p2);

        assertThat(outputTopic.isEmpty()).isFalse();

        // assert value, ticker
        PriceAnalytics result1 = outputTopic.readValue();
        assertThat(result1.symbol()).isEqualTo("BTC");

        // assert average
        PriceAnalytics result2 = outputTopic.readValue();
        assertThat(result2.averagePrice()).isEqualTo(150.00);
    }
}
