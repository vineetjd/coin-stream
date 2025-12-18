package com.coinstream.analytics.stream;

import com.coinstream.analytics.model.MarketPrice;
import com.coinstream.analytics.model.PriceAggregate;
import com.coinstream.analytics.model.PriceAnalytics;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerde;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.time.Duration;

@Configuration
@Slf4j
public class AnalyticsTopology {

        @Bean
        public KStream<String, MarketPrice> kStream(StreamsBuilder builder, ObjectMapper objectMapper) {

                // serde factory
                JsonSerde<MarketPrice> priceSerde = createSerde(MarketPrice.class, objectMapper);
                JsonSerde<PriceAnalytics> analyticsSerde = createSerde(PriceAnalytics.class, objectMapper);
                JsonSerde<PriceAggregate> aggregateSerde = createSerde(PriceAggregate.class, objectMapper);

                // consume market.prices
                KStream<String, MarketPrice> stream = builder.stream("market.prices",
                                Consumed.with(Serdes.String(), priceSerde));

                // 1 min aggregation
                stream
                                .map((key, value) -> new KeyValue<>(value.getSymbol(), value.getPrice().doubleValue()))
                                .groupByKey(Grouped.with(Serdes.String(), Serdes.Double()))
                                .windowedBy(TimeWindows.ofSizeAndGrace(Duration.ofSeconds(60), Duration.ofSeconds(5)))
                                .aggregate(
                                                PriceAggregate::new,
                                                (key, price, agg) -> agg.add(price),
                                                Materialized.with(Serdes.String(), aggregateSerde))
                                .toStream()
                                .map((windowedKey, agg) -> {
                                        double avg = agg.getSum() / agg.getCount();
                                        PriceAnalytics result = PriceAnalytics.builder()
                                                        .symbol(windowedKey.key())
                                                        .averagePrice(avg)
                                                        .sampleCount(agg.getCount())
                                                        .windowStart(windowedKey.window().start())
                                                        .windowEnd(windowedKey.window().end())
                                                        // ohlc
                                                        .open(agg.getOpen())
                                                        .high(agg.getMax())
                                                        .low(agg.getMin())
                                                        .close(agg.getClose())
                                                        .build();
                                        log.info("Calculated Candle: {}", result);
                                        return new KeyValue<>(windowedKey.key(), result);
                                })
                                // produce market.analytics
                                .to("market.analytics", Produced.with(Serdes.String(), analyticsSerde));

                return stream;
        }

        private <T> JsonSerde<T> createSerde(Class<T> type, ObjectMapper mapper) {
                JsonSerializer<T> serializer = new JsonSerializer<>(mapper);
                JsonDeserializer<T> deserializer = new JsonDeserializer<>(type, mapper);
                // ignore headers
                deserializer.setUseTypeHeaders(false);
                deserializer.addTrustedPackages("*");
                return new JsonSerde<>(serializer, deserializer);
        }
}
