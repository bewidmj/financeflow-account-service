package com.learning.financeflow.account.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.learning.financeflow.account.model.Account;
import com.learning.financeflow.account.model.AccountType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:account-repository;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
class AccountRepositoryTest {

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void accountCanOnlyBeFoundThroughMatchingOwner() {
        UUID ownerId = UUID.randomUUID();
        Account account = new Account(
                UUID.randomUUID(),
                ownerId,
                "Daily",
                AccountType.CASH,
                "INR",
                BigDecimal.ZERO,
                Instant.now());
        accountRepository.saveAndFlush(account);

        assertTrue(accountRepository.findByIdAndUserId(account.getId(), ownerId).isPresent());
        assertTrue(accountRepository.findByIdAndUserId(account.getId(), UUID.randomUUID()).isEmpty());
    }

    @Test
    void ordersAccountsByCreationTimeDescending() {
        UUID ownerId = UUID.randomUUID();
        Account earlier = new Account(
                UUID.randomUUID(),
                ownerId,
                "Earlier",
                AccountType.SAVINGS,
                "INR",
                BigDecimal.ZERO,
                Instant.parse("2026-01-01T00:00:00Z"));
        Account later = new Account(
                UUID.randomUUID(),
                ownerId,
                "Later",
                AccountType.CURRENT,
                "INR",
                BigDecimal.ZERO,
                Instant.parse("2026-01-02T00:00:00Z"));
        accountRepository.saveAllAndFlush(java.util.List.of(earlier, later));

        assertEquals(
                later.getId(),
                accountRepository.findAllByUserIdOrderByCreatedAtDesc(ownerId).getFirst().getId());
    }
}
