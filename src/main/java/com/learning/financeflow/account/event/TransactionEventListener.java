package com.learning.financeflow.account.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class TransactionEventListener {

    private static final Logger logger = LoggerFactory.getLogger(TransactionEventListener.class);

    private final ObjectMapper objectMapper;
    private final TransactionEventProcessingService processingService;
    private final MeterRegistry meterRegistry;

    public TransactionEventListener(
            ObjectMapper objectMapper,
            TransactionEventProcessingService processingService,
            MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.processingService = processingService;
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(
            topics = "${app.kafka.transaction-topic}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "transactionKafkaListenerContainerFactory")
    public void consume(ConsumerRecord<String, String> record) {
        TransactionEvent event;
        try {
            event = objectMapper.readValue(record.value(), TransactionEvent.class);
        } catch (JsonProcessingException exception) {
            throw new InvalidTransactionEventException("Unable to deserialize transaction event", exception);
        }
        boolean processed;
        try {
            processed = processingService.process(event);
        } catch (RuntimeException exception) {
            meterRegistry.counter("account.transaction.events.failures").increment();
            throw exception;
        }
        if (processed) {
            meterRegistry.counter("account.transaction.events.processed").increment();
            logger.info("Processed transaction event eventId={}, transactionId={}, version={}",
                    event.eventId(), event.transactionId(), event.transactionVersion());
        } else {
            meterRegistry.counter("account.transaction.events.duplicates").increment();
            logger.info("Ignored duplicate transaction event eventId={}", event.eventId());
        }
    }
}
