package com.learning.financeflow.account.repository;

import com.learning.financeflow.account.model.AccountLedgerEntry;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountLedgerEntryRepository extends JpaRepository<AccountLedgerEntry, UUID> {

    Page<AccountLedgerEntry> findAllByAccountIdOrderByCreatedAtDescIdDesc(
            UUID accountId, Pageable pageable);
}
