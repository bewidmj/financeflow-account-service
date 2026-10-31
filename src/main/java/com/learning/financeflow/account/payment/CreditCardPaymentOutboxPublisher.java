package com.learning.financeflow.account.payment;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "app.credit-card-payments",
        name = "outbox-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class CreditCardPaymentOutboxPublisher {

    private static final Logger logger =
            LoggerFactory.getLogger(CreditCardPaymentOutboxPublisher.class);
    private static final int BATCH_SIZE = 50;

    private final CreditCardPaymentOutboxClaimer claimer;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String resultTopic;

    public CreditCardPaymentOutboxPublisher(
            CreditCardPaymentOutboxClaimer claimer,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${app.kafka.credit-card-payment-result-topic}")
                    String resultTopic) {
        this.claimer = claimer;
        this.kafkaTemplate = kafkaTemplate;
        this.resultTopic = resultTopic;
    }

    @Scheduled(fixedDelayString = "${app.credit-card-payments.outbox-poll-interval:1000}")
    public void publishPending() {
        List<ClaimedPaymentResult> results = claimer.claim(BATCH_SIZE);
        for (ClaimedPaymentResult result : results) {
            try {
                kafkaTemplate.send(
                                resultTopic,
                                result.paymentId().toString(),
                                result.payload())
                        .get(30, TimeUnit.SECONDS);
                if (!claimer.markPublished(result.id(), result.claimToken(), Instant.now())) {
                    logger.warn(
                            "Payment result acknowledgment could not be recorded for paymentId={}",
                            result.paymentId());
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                claimer.markFailed(result, exception.getMessage());
                return;
            } catch (ExecutionException | TimeoutException | RuntimeException exception) {
                claimer.markFailed(result, exception.getMessage());
                logger.error(
                        "Payment result publish failed for paymentId={}, attempt={}",
                        result.paymentId(),
                        result.attemptCount(),
                        exception);
            }
        }
    }
}
