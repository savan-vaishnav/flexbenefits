package com.flexbenefits.event;

import com.flexbenefits.entity.Enrollment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Publishes enrollment status change events to Kafka.
 * Claims-service subscribes to these events to auto-reject claims when enrollments are cancelled.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EnrollmentEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public static final String TOPIC = "enrollment-events";

    public void publishStatusChange(Enrollment enrollment) {
        EnrollmentEvent event = new EnrollmentEvent(
                enrollment.getId(),
                enrollment.getTenant().getId(),
                enrollment.getEmployee().getId(),
                enrollment.getBenefitPlan().getId(),
                enrollment.getStatus().name(),
                LocalDateTime.now()
        );

        kafkaTemplate.send(TOPIC, enrollment.getId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Published enrollment event: {} status={} to topic={}",
                                event.enrollmentId(), event.status(), TOPIC);
                    } else {
                        log.error("Failed to publish enrollment event: {} - {}",
                                event.enrollmentId(), ex.getMessage());
                    }
                });
    }
}


