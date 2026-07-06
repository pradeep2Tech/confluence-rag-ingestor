package com.confluence.ingestor.messaging;

import com.confluence.ingestor.api.dto.IngestionRequest;
import com.confluence.ingestor.support.TestAiConfiguration;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"ingestion.manifest.crawl"})
@Import(TestAiConfiguration.class)
@DirtiesContext
class KafkaJobPublisherIntegrationTest {

    @DynamicPropertySource
    static void kafkaEnabled(DynamicPropertyRegistry registry) {
        registry.add("confluence.ingestor.kafka.enabled", () -> "true");
        registry.add("confluence.ingestor.kafka.bootstrap-servers", () -> System.getProperty("spring.embedded.kafka.brokers"));
        registry.add("confluence.ingestor.kafka.consumer-group-id", () -> "test-ingestor");
    }

    @Autowired
    private KafkaJobPublisher kafkaJobPublisher;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Test
    void publishesManifestCrawlJobToKafka() {
        IngestionRequest request = new IngestionRequest(
                "https://confluence.example.com",
                "12345",
                "pat",
                true,
                null,
                null,
                null,
                null,
                null,
                null);

        kafkaJobPublisher.publish(IngestionJobType.MANIFEST_CRAWL, request);

        Map<String, Object> consumerProps = new HashMap<>(KafkaTestUtils.consumerProps("test-group", "true", embeddedKafka));
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "com.confluence.ingestor.*");
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, IngestionJobMessage.class.getName());

        try (Consumer<String, IngestionJobMessage> consumer =
                new DefaultKafkaConsumerFactory<String, IngestionJobMessage>(consumerProps).createConsumer()) {
            consumer.subscribe(java.util.List.of("ingestion.manifest.crawl"));
            ConsumerRecords<String, IngestionJobMessage> records =
                    KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));

            assertThat(records.count()).isEqualTo(1);
            var record = records.iterator().next();
            assertThat(record.key()).isEqualTo("12345");
            assertThat(record.value().jobType()).isEqualTo(IngestionJobType.MANIFEST_CRAWL);
            assertThat(record.value().parentPageId()).isEqualTo("12345");
            assertThat(record.value().request().baseUrl()).isEqualTo("https://confluence.example.com");
            assertThat(record.value().request().parentPageId()).isEqualTo("12345");
            assertThat(record.value().request().pat()).isEqualTo("pat");
            assertThat(record.value().request().shouldForceRebuildManifest()).isTrue();
        }
    }
}
