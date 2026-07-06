package com.confluence.ingestor.messaging;

import com.confluence.ingestor.api.dto.IngestionRequest;
import com.confluence.ingestor.config.KafkaProperties;
import com.confluence.ingestor.port.JobPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka adapter for {@link JobPublisher} — partition key is {@code parentPageId}.
 */
@Component
@ConditionalOnProperty(name = "confluence.ingestor.kafka.enabled", havingValue = "true")
public class KafkaJobPublisher implements JobPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaJobPublisher.class);

    private final KafkaTemplate<String, IngestionJobMessage> kafkaTemplate;
    private final KafkaProperties kafkaProperties;

    public KafkaJobPublisher(
            KafkaTemplate<String, IngestionJobMessage> kafkaTemplate, KafkaProperties kafkaProperties) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaProperties = kafkaProperties;
    }

    @Override
    public void publish(IngestionJobType jobType, IngestionRequest request) {
        IngestionJobMessage message = IngestionJobMessage.of(jobType, request);
        String topic = kafkaProperties.resolveTopic(jobType);
        log.info(
                "Publishing Kafka job type={} parentPageId={} topic={} messageId={}",
                jobType,
                request.parentPageId(),
                topic,
                message.messageId());
        kafkaTemplate.send(topic, request.parentPageId(), message);
    }
}
