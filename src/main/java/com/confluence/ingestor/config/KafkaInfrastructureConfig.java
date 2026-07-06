package com.confluence.ingestor.config;

import com.confluence.ingestor.messaging.IngestionJobMessage;
import com.confluence.ingestor.messaging.IngestionTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(KafkaProperties.class)
@ConditionalOnProperty(name = "confluence.ingestor.kafka.enabled", havingValue = "true")
public class KafkaInfrastructureConfig {

    @Bean
    NewTopic manifestCrawlTopic(KafkaProperties kafkaProperties) {
        return TopicBuilder.name(kafkaProperties.resolveTopic(
                        com.confluence.ingestor.messaging.IngestionJobType.MANIFEST_CRAWL))
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic pageTransformTopic(KafkaProperties kafkaProperties) {
        return TopicBuilder.name(kafkaProperties.resolveTopic(
                        com.confluence.ingestor.messaging.IngestionJobType.PAGE_TRANSFORM))
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic chunkTopic(KafkaProperties kafkaProperties) {
        return TopicBuilder.name(kafkaProperties.resolveTopic(
                        com.confluence.ingestor.messaging.IngestionJobType.CHUNK))
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic vectorIngestTopic(KafkaProperties kafkaProperties) {
        return TopicBuilder.name(kafkaProperties.resolveTopic(
                        com.confluence.ingestor.messaging.IngestionJobType.VECTOR_INGEST))
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic manifestCrawlDlq() {
        return TopicBuilder.name(IngestionTopics.MANIFEST_CRAWL_DLQ).partitions(1).replicas(1).build();
    }

    @Bean
    ProducerFactory<String, IngestionJobMessage> ingestionJobProducerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.bootstrapServers());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    KafkaTemplate<String, IngestionJobMessage> ingestionJobKafkaTemplate(
            ProducerFactory<String, IngestionJobMessage> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    ConsumerFactory<String, IngestionJobMessage> ingestionJobConsumerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.bootstrapServers());
        config.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaProperties.consumerGroupId());
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "com.confluence.ingestor.*");
        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, IngestionJobMessage.class.getName());
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, IngestionJobMessage> ingestionJobListenerContainerFactory(
            ConsumerFactory<String, IngestionJobMessage> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, IngestionJobMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(1);
        factory.getContainerProperties().setAckMode(
                org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL);
        return factory;
    }
}
