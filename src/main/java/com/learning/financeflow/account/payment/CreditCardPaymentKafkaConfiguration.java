package com.learning.financeflow.account.payment;

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
public class CreditCardPaymentKafkaConfiguration {

    @Bean
    KafkaAdmin.NewTopics creditCardPaymentTopics(
            @Value("${app.kafka.credit-card-payment-command-topic}") String commandTopic,
            @Value("${app.kafka.credit-card-payment-command-dlt-topic}") String deadLetterTopic,
            @Value("${app.kafka.credit-card-payment-result-topic}") String resultTopic) {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(commandTopic).partitions(6).replicas(1).build(),
                TopicBuilder.name(deadLetterTopic).partitions(6).replicas(1).build(),
                TopicBuilder.name(resultTopic).partitions(6).replicas(1).build());
    }

    @Bean
    DefaultErrorHandler creditCardPaymentErrorHandler(
            KafkaTemplate<Object, Object> kafkaTemplate,
            @Value("${app.kafka.credit-card-payment-command-dlt-topic}") String deadLetterTopic) {
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
        errorHandler.addNotRetryableExceptions(InvalidCreditCardPaymentCommandException.class);
        return errorHandler;
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, String>
            creditCardPaymentKafkaListenerContainerFactory(
                    ConsumerFactory<String, String> consumerFactory,
                    DefaultErrorHandler creditCardPaymentErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(creditCardPaymentErrorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }
}
