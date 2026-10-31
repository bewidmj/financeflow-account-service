package com.learning.financeflow.account.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class CreditCardPaymentCommandListener {

    private final ObjectMapper objectMapper;
    private final CreditCardPaymentCommandProcessor processor;

    public CreditCardPaymentCommandListener(
            ObjectMapper objectMapper, CreditCardPaymentCommandProcessor processor) {
        this.objectMapper = objectMapper;
        this.processor = processor;
    }

    @KafkaListener(
            topics = "${app.kafka.credit-card-payment-command-topic}",
            containerFactory = "creditCardPaymentKafkaListenerContainerFactory")
    public void receive(String payload) {
        try {
            processor.process(objectMapper.readValue(payload, CreditCardPaymentCommand.class));
        } catch (JsonProcessingException exception) {
            throw new InvalidCreditCardPaymentCommandException(
                    "Invalid credit card payment command JSON", exception);
        }
    }
}
