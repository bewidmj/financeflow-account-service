package com.learning.financeflow.account.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.financeflow.account.model.Account;
import com.learning.financeflow.account.model.AccountType;
import com.learning.financeflow.account.repository.AccountLedgerEntryRepository;
import com.learning.financeflow.account.repository.AccountRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class CreditCardPaymentCommandProcessorTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountLedgerEntryRepository ledgerRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private CreditCardPaymentCommandProcessor processor;
    private final UUID userId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID paymentId = UUID.randomUUID();
    private Account account;

    @BeforeEach
    void setUp() {
        processor = new CreditCardPaymentCommandProcessor(
                accountRepository,
                ledgerRepository,
                jdbcTemplate,
                new ObjectMapper().findAndRegisterModules());
        account = new Account(
                accountId,
                userId,
                "Savings",
                AccountType.SAVINGS,
                "INR",
                new BigDecimal("1000.00"),
                Instant.now());
        doReturn(List.of())
                .when(jdbcTemplate)
                .query(anyString(), any(RowMapper.class), any(Object[].class));
    }

    @Test
    void debitsTheOwnedSourceAndWritesTheResultOutboxAtomically() {
        CreditCardPaymentCommand command = command(new BigDecimal("300.00"));
        doReturn(Optional.of(account)).when(accountRepository).findByIdForUpdate(accountId);

        processor.process(command);

        assertEquals(new BigDecimal("700.00"), account.getCurrentBalance());
        verify(ledgerRepository).save(any());
        verify(jdbcTemplate, org.mockito.Mockito.times(2))
                .update(anyString(), any(Object[].class));
    }

    @Test
    void recordsInsufficientFundsWithoutChangingTheAccountOrLedger() {
        CreditCardPaymentCommand command = command(new BigDecimal("1500.00"));
        doReturn(Optional.of(account)).when(accountRepository).findByIdForUpdate(accountId);

        processor.process(command);

        assertEquals(new BigDecimal("1000.00"), account.getCurrentBalance());
        verify(ledgerRepository, never()).save(any());
        verify(jdbcTemplate, org.mockito.Mockito.times(2))
                .update(anyString(), any(Object[].class));
    }

    @Test
    void recordsAnUnavailableSourceWithoutLeakingAccountOwnership() {
        CreditCardPaymentCommand command = command(new BigDecimal("300.00"));
        doReturn(Optional.empty()).when(accountRepository).findByIdForUpdate(accountId);

        processor.process(command);

        verifyNoInteractions(ledgerRepository);
        verify(jdbcTemplate, org.mockito.Mockito.times(2))
                .update(anyString(), any(Object[].class));
    }

    private CreditCardPaymentCommand command(BigDecimal amount) {
        return new CreditCardPaymentCommand(
                UUID.randomUUID(),
                paymentId,
                UUID.randomUUID(),
                userId,
                accountId,
                amount,
                "INR",
                UUID.randomUUID().toString(),
                Instant.now());
    }
}
