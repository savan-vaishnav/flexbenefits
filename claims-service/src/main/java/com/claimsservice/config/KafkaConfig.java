package com.claimsservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka consumer configuration with dead-letter topic support.
 * Failed messages are retried 3 times, then sent to a dead-letter topic (topic.DLT).
 */
@Configuration
@Slf4j
public class KafkaConfig {

    @Bean
    @ConditionalOnBean(KafkaTemplate.class)
    public CommonErrorHandler errorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        // Publish failed messages to dead-letter topic: enrollment-events.DLT
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, exception) -> {
                    log.error("Sending message to DLT. Topic: {}, Key: {}, Error: {}",
                            record.topic(), record.key(), exception.getMessage());
                    return new org.apache.kafka.common.TopicPartition(
                            record.topic() + ".DLT", record.partition());
                });

        // Retry 3 times with 1 second interval before sending to DLT
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
    }
}


