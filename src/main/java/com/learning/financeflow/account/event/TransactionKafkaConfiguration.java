package com.learning.financeflow.account.event;

import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Configuration
public class TransactionKafkaConfiguration {

    @Bean
    KafkaAdmin.NewTopics transactionTopics(
            @Value("${app.kafka.transaction-topic}") String topic,
            @Value("${app.kafka.transaction-dlt-topic}") String deadLetterTopic) {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(topic).partitions(6).replicas(1).build(),
                TopicBuilder.name(deadLetterTopic).partitions(6).replicas(1).build());
    }

    @Bean
    DefaultErrorHandler transactionEventErrorHandler(
            KafkaTemplate<Object, Object> kafkaTemplate,
            @Value("${app.kafka.transaction-dlt-topic}") String deadLetterTopic) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(deadLetterTopic, record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        recoverer.setWaitForSendResultTimeout(java.time.Duration.ofSeconds(10));
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(5);
        backOff.setInitialInterval(1000);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(30_000);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(
                InvalidTransactionEventException.class,
                AccountEventRejectedException.class);
        return errorHandler;
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, String> transactionKafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            DefaultErrorHandler transactionEventErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(transactionEventErrorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }
}
