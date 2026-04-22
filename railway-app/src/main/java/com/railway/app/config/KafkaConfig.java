package com.railway.app.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Value("${app.kafka.topics.booking-events}")
    private String bookingEventsTopic;

    @Value("${app.kafka.topics.payment-events}")
    private String paymentEventsTopic;

    @Value("${app.kafka.topics.notification-events}")
    private String notificationEventsTopic;

    @Value("${app.kafka.topics.train-events}")
    private String trainEventsTopic;

    @Bean
    public NewTopic bookingEventsTopic() {
        return TopicBuilder.name(bookingEventsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic paymentEventsTopic() {
        return TopicBuilder.name(paymentEventsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic notificationEventsTopic() {
        return TopicBuilder.name(notificationEventsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic bookingEventsRetryTopic() {
        return TopicBuilder.name(bookingEventsTopic + ".retry")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic bookingEventsDltTopic() {
        return TopicBuilder.name(bookingEventsTopic + ".dlt")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic paymentEventsDltTopic() {
        return TopicBuilder.name(paymentEventsTopic + ".dlt")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic trainEventsTopic() {
        return TopicBuilder.name(trainEventsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
