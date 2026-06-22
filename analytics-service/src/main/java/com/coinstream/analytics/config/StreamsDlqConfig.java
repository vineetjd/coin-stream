package com.coinstream.analytics.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration;
import org.springframework.kafka.config.KafkaStreamsConfiguration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.streams.RecoveringDeserializationExceptionHandler;
import org.springframework.kafka.support.serializer.JacksonJsonSerde;

/**
 * Poison-pill immunity for the Kafka Streams app. A record that cannot be
 * deserialized is routed to {@code <topic>.dlt} and the stream thread continues,
 * instead of the default {@code LogAndFailExceptionHandler} killing the thread
 * and crash-looping the container forever on the same bad record.
 *
 * <p>The deserialization handler + recoverer must be set on the actual
 * {@link KafkaStreamsConfiguration}. Setting the handler via
 * {@code spring.kafka.streams.properties.*} was silently dropped by Boot, and a
 * {@code StreamsBuilderFactoryBeanConfigurer} mutated a copy that never reached
 * the live config — so we own {@code defaultKafkaStreamsConfig} outright here.</p>
 */
@Configuration
public class StreamsDlqConfig {

    /** Raw byte[] template: the failed record is undeserializable, so we ship the original bytes. */
    @Bean
    public KafkaTemplate<byte[], byte[]> dltKafkaTemplate(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    @Bean
    public DeadLetterPublishingRecoverer streamsDlqRecoverer(KafkaTemplate<byte[], byte[]> dltKafkaTemplate) {
        return new DeadLetterPublishingRecoverer(dltKafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".dlt", record.partition()));
    }

    @Bean(name = KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_CONFIG_BEAN_NAME)
    public KafkaStreamsConfiguration defaultKafkaStreamsConfig(
            @Value("${spring.kafka.streams.application-id}") String applicationId,
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            DeadLetterPublishingRecoverer streamsDlqRecoverer) {

        Map<String, Object> props = new HashMap<>();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, applicationId);
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class.getName());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, JacksonJsonSerde.class.getName());

        // Dev: emit results immediately
        props.put("cache.max.bytes.buffering", 0);
        props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 100);

        // JSON serde helpers (for the default value serde)
        props.put("spring.json.trusted.packages", "*");
        props.put("spring.json.use.type.headers", false);

        // Poison-pill immunity: route undeserializable records to <topic>.dlt and continue.
        props.put(StreamsConfig.DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG,
                RecoveringDeserializationExceptionHandler.class);
        props.put(RecoveringDeserializationExceptionHandler.KSTREAM_DESERIALIZATION_RECOVERER,
                streamsDlqRecoverer);

        return new KafkaStreamsConfiguration(props);
    }
}
