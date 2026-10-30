package com.learning.financeflow.account.event;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountTransactionInboxRepository
        extends JpaRepository<AccountTransactionInbox, UUID> {
}
