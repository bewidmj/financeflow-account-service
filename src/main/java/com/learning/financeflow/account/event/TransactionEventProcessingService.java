package com.learning.financeflow.account.event;

import com.learning.financeflow.account.exception.AccountServiceException;
import com.learning.financeflow.account.model.Account;
import com.learning.financeflow.account.model.AccountLedgerEntry;
import com.learning.financeflow.account.model.LedgerEntryType;
import com.learning.financeflow.account.repository.AccountLedgerEntryRepository;
import com.learning.financeflow.account.repository.AccountRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionEventProcessingService {

    private static final String CURRENCY = "INR";

    private final AccountRepository accountRepository;
    private final AccountLedgerEntryRepository ledgerRepository;
    private final AccountTransactionInboxRepository inboxRepository;
    private final AccountTransactionStateRepository stateRepository;

    public TransactionEventProcessingService(
            AccountRepository accountRepository,
            AccountLedgerEntryRepository ledgerRepository,
            AccountTransactionInboxRepository inboxRepository,
            AccountTransactionStateRepository stateRepository) {
        this.accountRepository = accountRepository;
        this.ledgerRepository = ledgerRepository;
        this.inboxRepository = inboxRepository;
        this.stateRepository = stateRepository;
    }

    @Transactional
    public boolean process(TransactionEvent event) {
        validate(event);
        if (inboxRepository.existsById(event.eventId())) {
            return false;
        }

        AccountTransactionState state = stateRepository
                .findByIdForUpdate(event.transactionId())
                .orElse(null);
        if (inboxRepository.existsById(event.eventId())) {
            return false;
        }
        validateSequence(event, state);

        TransactionSnapshot prior = state == null ? null : state.toSnapshot();
        if (event.before() != null && !sameSnapshot(prior, event.before())) {
            throw new AccountEventRejectedException(
                    "Transaction event previous state does not match the account projection");
        }
        if (state != null && !state.getUserId().equals(event.userId())) {
            throw new AccountEventRejectedException(
                    "Transaction owner changed between event versions");
        }

        Map<UUID, Account> lockedAccounts = lockAccounts(event, prior);
        Instant now = Instant.now();
        int nextEntrySequence = 0;
        if (prior != null) {
            nextEntrySequence = appendLedgerChange(
                    event, prior, true, lockedAccounts, now, nextEntrySequence);
        }
        if (event.after() != null) {
            appendLedgerChange(
                    event, event.after(), false, lockedAccounts, now, nextEntrySequence);
        }

        if (state == null) {
            state = new AccountTransactionState(
                    event.transactionId(),
                    event.userId(),
                    event.transactionVersion(),
                    event.after() == null ? event.before() : event.after(),
                    event.after() == null);
        } else {
            state.advance(
                    event.transactionVersion(),
                    event.after(),
                    event.after() == null);
        }
        stateRepository.save(state);
        inboxRepository.save(new AccountTransactionInbox(
                event.eventId(),
                event.transactionId(),
                event.transactionVersion(),
                event.eventType(),
                now));
        return true;
    }

    private void validate(TransactionEvent event) {
        if (event == null
                || event.eventId() == null
                || event.transactionId() == null
                || event.transactionId() < 1
                || event.transactionVersion() < 1
                || event.userId() == null
                || event.eventType() == null
                || event.eventVersion() != 1
                || event.occurredAt() == null
                || event.correlationId() == null
                || event.correlationId().isBlank()) {
            throw new InvalidTransactionEventException("Transaction event is missing required fields");
        }
        switch (event.eventType()) {
            case "TransactionCreated" -> {
                if (event.transactionVersion() != 1
                        || event.before() != null
                        || event.after() == null) {
                    throw new InvalidTransactionEventException(
                            "TransactionCreated event has an invalid shape");
                }
            }
            case "TransactionUpdated" -> {
                if (event.transactionVersion() < 2
                        || event.before() == null
                        || event.after() == null) {
                    throw new InvalidTransactionEventException(
                            "TransactionUpdated event has an invalid shape");
                }
            }
            case "TransactionDeleted" -> {
                if (event.transactionVersion() < 2
                        || event.before() == null
                        || event.after() != null) {
                    throw new InvalidTransactionEventException(
                            "TransactionDeleted event has an invalid shape");
                }
            }
            default -> throw new InvalidTransactionEventException(
                    "Unsupported transaction event type");
        }
        validateSnapshot(event.before());
        validateSnapshot(event.after());
    }

    private void validateSnapshot(TransactionSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        if (snapshot.transactionType() == null
                || snapshot.amount() == null
                || snapshot.amount().signum() <= 0
                || snapshot.amount().scale() > 2
                || snapshot.currency() == null
                || !CURRENCY.equals(snapshot.currency())
                || snapshot.category() == null
                || snapshot.category().isBlank()
                || snapshot.category().length() > 100
                || (snapshot.description() != null && snapshot.description().length() > 500)
                || snapshot.transactionDate() == null) {
            throw new InvalidTransactionEventException(
                    "Transaction event contains an invalid transaction snapshot");
        }
    }

    private void validateSequence(TransactionEvent event, AccountTransactionState state) {
        if (state == null) {
            if (event.transactionVersion() != 1
                    || !"TransactionCreated".equals(event.eventType())) {
                throw new TransactionVersionGapException(
                        "Transaction event arrived before its creation event");
            }
            return;
        }
        if (state.getTransactionVersion() >= event.transactionVersion()) {
            throw new AccountEventRejectedException(
                    "Transaction event version is stale and has no matching inbox record");
        }
        if (state.isDeleted()) {
            throw new AccountEventRejectedException(
                    "A deleted transaction cannot be changed again");
        }
        if (event.transactionVersion() != state.getTransactionVersion() + 1) {
            throw new TransactionVersionGapException(
                    "Transaction event version is not the next expected version");
        }
    }

    private Map<UUID, Account> lockAccounts(
            TransactionEvent event, TransactionSnapshot prior) {
        List<UUID> accountIds = new ArrayList<>();
        addAccountId(accountIds, prior);
        addAccountId(accountIds, event.after());
        accountIds.sort(Comparator.comparing(UUID::toString));

        Map<UUID, Account> accounts = new HashMap<>();
        for (UUID accountId : accountIds) {
            Account account = accountRepository.findByIdForUpdate(accountId)
                    .orElseThrow(() -> new AccountEventRejectedException(
                            "Transaction event references a missing account"));
            if (!account.getUserId().equals(event.userId())) {
                throw new AccountEventRejectedException(
                        "Transaction event account is not owned by its user");
            }
            if (!"ACTIVE".equals(account.getStatus().name())) {
                throw new AccountEventRejectedException(
                        "Transaction event references a closed account");
            }
            if (!CURRENCY.equals(account.getCurrency())) {
                throw new AccountEventRejectedException(
                        "Finance transactions currently support INR accounts only");
            }
            accounts.put(accountId, account);
        }
        return accounts;
    }

    private void addAccountId(List<UUID> accountIds, TransactionSnapshot snapshot) {
        if (snapshot != null && snapshot.accountId() != null
                && !accountIds.contains(snapshot.accountId())) {
            accountIds.add(snapshot.accountId());
        }
    }

    private int appendLedgerChange(
            TransactionEvent event,
            TransactionSnapshot snapshot,
            boolean reversal,
            Map<UUID, Account> accounts,
            Instant now,
            int entrySequence) {
        if (snapshot.accountId() == null) {
            return entrySequence;
        }
        Account account = accounts.get(snapshot.accountId());
        boolean credit = snapshot.transactionType() == TransactionType.INCOME;
        if (reversal) {
            credit = !credit;
        }
        BigDecimal amount = snapshot.amount();
        try {
            if (credit) {
                account.credit(amount, now);
            } else {
                account.debit(amount, now);
            }
        } catch (IllegalStateException exception) {
            throw new AccountEventRejectedException(
                    "Account balance cannot accept the transaction event", exception);
        }

        String verb = reversal ? "Reversal of" : "Transaction";
        String description = verb + " #" + event.transactionId()
                + (snapshot.description() == null || snapshot.description().isBlank()
                        ? ""
                        : ": " + snapshot.description());
        if (description.length() > 500) {
            description = description.substring(0, 500);
        }
        ledgerRepository.save(new AccountLedgerEntry(
                UUID.randomUUID(),
                account.getId(),
                event.eventId(),
                credit ? LedgerEntryType.CREDIT : LedgerEntryType.DEBIT,
                amount,
                description,
                now,
                event.transactionId(),
                event.transactionVersion(),
                event.eventId(),
                entrySequence));
        return entrySequence + 1;
    }

    private boolean sameSnapshot(TransactionSnapshot left, TransactionSnapshot right) {
        return left != null
                && right != null
                && Objects.equals(left.accountId(), right.accountId())
                && left.transactionType() == right.transactionType()
                && left.amount().compareTo(right.amount()) == 0
                && Objects.equals(left.currency(), right.currency())
                && Objects.equals(left.category(), right.category())
                && Objects.equals(left.description(), right.description())
                && Objects.equals(left.transactionDate(), right.transactionDate());
    }
}
