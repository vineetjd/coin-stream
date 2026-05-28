package com.coinstream.analytics.stream;

import com.coinstream.analytics.model.MarketPrice;
import com.coinstream.analytics.model.PriceAggregate;
import com.coinstream.analytics.model.PriceAnalytics;
import tools.jackson.databind.json.JsonMapper;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerde;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class AnalyticsTopology {

        private static final Logger log = LoggerFactory.getLogger(AnalyticsTopology.class);

        @Value("${kafka.topic.prices}")
        private String pricesTopic;

        @Value("${kafka.topic.analytics}")
        private String analyticsTopic;

        @Value("${analytics.window.duration.seconds}")
        private long windowDurationSec;

        @Value("${analytics.window.grace.seconds}")
        private long windowGraceSec;

        @Bean
        public KStream<String, MarketPrice> buildAnalyticsTopology(StreamsBuilder builder, JsonMapper objectMapper) {

                // serde factory
                JacksonJsonSerde<MarketPrice> priceSerde = createSerde(MarketPrice.class, objectMapper);
                JacksonJsonSerde<PriceAnalytics> analyticsSerde = createSerde(PriceAnalytics.class, objectMapper);
                JacksonJsonSerde<PriceAggregate> aggregateSerde = createSerde(PriceAggregate.class, objectMapper);

                // consume market.prices
                KStream<String, MarketPrice> stream = builder.stream(pricesTopic,
                                Consumed.with(Serdes.String(), priceSerde));

                // aggregation
                stream
                                .map((key, value) -> new KeyValue<>(value.symbol(), value.price().doubleValue()))
                                .groupByKey(Grouped.with(Serdes.String(), Serdes.Double()))
                                .windowedBy(TimeWindows.ofSizeAndGrace(Duration.ofSeconds(windowDurationSec), Duration.ofSeconds(windowGraceSec)))
                                .aggregate(
                                                PriceAggregate::new,
                                                (key, price, agg) -> agg.add(price),
                                                Materialized.with(Serdes.String(), aggregateSerde))
                                .toStream()
                                .map((windowedKey, agg) -> {
                                        double avg = agg.sum() / agg.count();
                                        PriceAnalytics result = new PriceAnalytics(
                                                        windowedKey.key(),
                                                        avg,
                                                        agg.count(),
                                                        windowedKey.window().start(),
                                                        windowedKey.window().end(),
                                                        agg.open(),
                                                        agg.max(),
                                                        agg.min(),
                                                        agg.close()
                                        );
                                        log.info("Calculated Candle: {}", result);
                                        return new KeyValue<>(windowedKey.key(), result);
                                })
                                // produce market.analytics
                                .to(analyticsTopic, Produced.with(Serdes.String(), analyticsSerde));

                return stream;
        }

        private <T> JacksonJsonSerde<T> createSerde(Class<T> type, JsonMapper mapper) {
                JacksonJsonSerializer<T> serializer = new JacksonJsonSerializer<>(mapper);
                JacksonJsonDeserializer<T> deserializer = new JacksonJsonDeserializer<>(type, mapper);
                // ignore headers
                deserializer.setUseTypeHeaders(false);
                deserializer.addTrustedPackages("*");
                return new JacksonJsonSerde<>(serializer, deserializer);
        }
}
