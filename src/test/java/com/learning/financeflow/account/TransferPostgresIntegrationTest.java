package com.learning.financeflow.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.learning.financeflow.account.api.AccountResponse;
import com.learning.financeflow.account.api.CreateAccountRequest;
import com.learning.financeflow.account.api.CreateTransferRequest;
import com.learning.financeflow.account.exception.AccountServiceException;
import com.learning.financeflow.account.repository.AccountLedgerEntryRepository;
import com.learning.financeflow.account.repository.AccountTransferRepository;
import com.learning.financeflow.account.service.AccountService;
import com.learning.financeflow.account.service.TransferService;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TransferPostgresIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountLedgerEntryRepository ledgerRepository;

    @Autowired
    private AccountTransferRepository transferRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void duplicateIdempotencyKeyCreatesOnlyOneTransferAndOnePairOfEntries() {
        UUID userId = UUID.randomUUID();
        AccountResponse source = createAccount(userId, "Source", "100.00");
        AccountResponse destination = createAccount(userId, "Destination", "0.00");
        CreateTransferRequest request = request(source, destination, "25.00");

        var first = transferService.create(userId, "same-request", request);
        var replay = transferService.create(userId, "same-request", request);

        assertEquals(first.getId(), replay.getId());
        assertEquals(new BigDecimal("75.00"), accountService
                .get(userId, source.id()).currentBalance());
        assertEquals(new BigDecimal("25.00"), accountService
                .get(userId, destination.id()).currentBalance());
        assertEquals(3, ledgerCount(source.id(), destination.id()));
        assertEquals(1, transferRepository
                .findAllByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(0, 10))
                .getTotalElements());
    }

    @Test
    void insufficientFundsLeaveNoTransferOrNewLedgerEntries() {
        UUID userId = UUID.randomUUID();
        AccountResponse source = createAccount(userId, "Source", "5.00");
        AccountResponse destination = createAccount(userId, "Destination", "0.00");

        assertThrows(
                AccountServiceException.class,
                () -> transferService.create(
                        userId,
                        "insufficient",
                        request(source, destination, "10.00")));

        assertEquals(new BigDecimal("5.00"), accountService.get(userId, source.id()).currentBalance());
        assertEquals(0, accountService.get(userId, destination.id()).currentBalance().compareTo(BigDecimal.ZERO));
        assertEquals(1, ledgerCount(source.id(), destination.id()));
        assertEquals(0, transferRepository
                .findAllByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(0, 10))
                .getTotalElements());
    }

    @Test
    void databaseFailureRollsBackTransferAndBothBalanceChanges() {
        UUID userId = UUID.randomUUID();
        AccountResponse source = createAccount(userId, "Source", "100.00");
        AccountResponse destination = createAccount(userId, "Destination", "0.00");
        jdbcTemplate.execute("""
                CREATE FUNCTION reject_test_credit() RETURNS trigger AS $$
                BEGIN
                    IF NEW.entry_type = 'CREDIT' THEN
                        RAISE EXCEPTION 'forced ledger failure';
                    END IF;
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER reject_test_credit_trigger
                BEFORE INSERT ON account_ledger_entries
                FOR EACH ROW EXECUTE FUNCTION reject_test_credit()
                """);

        try {
            assertThrows(
                    RuntimeException.class,
                    () -> transferService.create(
                            userId,
                            "rollback",
                            request(source, destination, "15.00")));
        } finally {
            jdbcTemplate.execute(
                    "DROP TRIGGER reject_test_credit_trigger ON account_ledger_entries");
            jdbcTemplate.execute("DROP FUNCTION reject_test_credit()");
        }

        assertEquals(new BigDecimal("100.00"), accountService.get(userId, source.id()).currentBalance());
        assertEquals(0, accountService.get(userId, destination.id()).currentBalance().compareTo(BigDecimal.ZERO));
        assertEquals(1, ledgerCount(source.id(), destination.id()));
        assertEquals(0, transferRepository
                .findAllByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(0, 10))
                .getTotalElements());
    }

    @Test
    void concurrentTransfersCannotOverdrawTheSource() throws Exception {
        UUID userId = UUID.randomUUID();
        AccountResponse source = createAccount(userId, "Source", "100.00");
        List<AccountResponse> destinations = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            destinations.add(createAccount(userId, "Destination " + i, "0.00"));
        }

        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < destinations.size(); i++) {
                int index = i;
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        transferService.create(
                                userId,
                                "parallel-" + index,
                                request(source, destinations.get(index), "20.00"));
                        return true;
                    } catch (AccountServiceException exception) {
                        return false;
                    }
                }));
            }
            if (!ready.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new AssertionError("Concurrent transfer workers did not become ready");
            }
            start.countDown();
            long completed = 0;
            for (Future<Boolean> result : results) {
                if (result.get(Duration.ofSeconds(20).toMillis(),
                        java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    completed++;
                }
            }

            assertEquals(5, completed);
            assertEquals(0, accountService.get(userId, source.id()).currentBalance().compareTo(BigDecimal.ZERO));
        } finally {
            executor.shutdownNow();
        }
    }

    private AccountResponse createAccount(UUID userId, String name, String openingBalance) {
        return accountService.create(
                userId,
                new CreateAccountRequest(
                        name,
                        com.learning.financeflow.account.model.AccountType.SAVINGS,
                        "INR",
                        new BigDecimal(openingBalance)));
    }

    private CreateTransferRequest request(
            AccountResponse source, AccountResponse destination, String amount) {
        return new CreateTransferRequest(
                source.id(), destination.id(), new BigDecimal(amount), null);
    }

    private long ledgerCount(UUID sourceId, UUID destinationId) {
        long sourceEntries = ledgerRepository
                .findAllByAccountIdOrderByCreatedAtDescIdDesc(sourceId, PageRequest.of(0, 100))
                .getTotalElements();
        long destinationEntries = ledgerRepository
                .findAllByAccountIdOrderByCreatedAtDescIdDesc(
                        destinationId, PageRequest.of(0, 100))
                .getTotalElements();
        return sourceEntries + destinationEntries;
    }
}
