package com.learning.financeflow.account.repository;

import com.learning.financeflow.account.model.AccountTransfer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountTransferRepository extends JpaRepository<AccountTransfer, UUID> {

    Optional<AccountTransfer> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    Optional<AccountTransfer> findByIdAndUserId(UUID id, UUID userId);

    Page<AccountTransfer> findAllByUserIdOrderByCreatedAtDescIdDesc(
            UUID userId, Pageable pageable);
}
