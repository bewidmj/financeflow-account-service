package com.learning.financeflow.account.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.learning.financeflow.account.api.CreateTransferRequest;
import com.learning.financeflow.account.exception.AccountServiceException;
import com.learning.financeflow.account.model.AccountTransfer;
import com.learning.financeflow.account.repository.AccountTransferRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private AccountTransferRepository transferRepository;

    @Mock
    private TransferTransactionExecutor transactionExecutor;

    @InjectMocks
    private TransferService transferService;

    @Test
    void idempotentRetryReturnsPreviouslyCompletedTransfer() {
        UUID userId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        CreateTransferRequest request = new CreateTransferRequest(
                sourceId, destinationId, new BigDecimal("10.00"), null);
        AccountTransfer previous = transfer(userId, sourceId, destinationId, "10.00");
        when(transferRepository.findByUserIdAndIdempotencyKey(userId, "retry"))
                .thenReturn(Optional.of(previous));

        AccountTransfer result = transferService.create(userId, "retry", request);

        assertEquals(previous.getId(), result.getId());
        verify(transactionExecutor, never()).execute(userId, "retry", request);
    }

    @Test
    void idempotencyKeyCannotBeReusedForDifferentTransfer() {
        UUID userId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        CreateTransferRequest request = new CreateTransferRequest(
                sourceId, destinationId, new BigDecimal("11.00"), null);
        AccountTransfer previous = transfer(userId, sourceId, destinationId, "10.00");
        when(transferRepository.findByUserIdAndIdempotencyKey(userId, "retry"))
                .thenReturn(Optional.of(previous));

        AccountServiceException exception = assertThrows(
                AccountServiceException.class,
                () -> transferService.create(userId, "retry", request));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        verify(transactionExecutor, never()).execute(userId, "retry", request);
    }

    private AccountTransfer transfer(
            UUID userId, UUID sourceId, UUID destinationId, String amount) {
        return new AccountTransfer(
                UUID.randomUUID(),
                userId,
                sourceId,
                destinationId,
                new BigDecimal(amount),
                "INR",
                "retry",
                null,
                Instant.now());
    }
}
