package com.learning.financeflow.account.controller;

import com.learning.financeflow.account.api.AccountResponse;
import com.learning.financeflow.account.api.BalanceResponse;
import com.learning.financeflow.account.api.CreateAccountRequest;
import com.learning.financeflow.account.api.LedgerEntryResponse;
import com.learning.financeflow.account.api.UpdateAccountRequest;
import com.learning.financeflow.account.service.AccountService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Validated
@RestController
@RequestMapping("/api/v1/accounts")
@SecurityRequirement(name = "bearerAuth")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(
            @Valid @RequestBody CreateAccountRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return accountService.create(userId(jwt), request);
    }

    @GetMapping
    public List<AccountResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return accountService.list(userId(jwt));
    }

    @GetMapping("/{accountId}")
    public AccountResponse get(
            @PathVariable UUID accountId, @AuthenticationPrincipal Jwt jwt) {
        return accountService.get(userId(jwt), accountId);
    }

    @PutMapping("/{accountId}")
    public AccountResponse update(
            @PathVariable UUID accountId,
            @Valid @RequestBody UpdateAccountRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return accountService.update(userId(jwt), accountId, request);
    }

    @DeleteMapping("/{accountId}")
    public AccountResponse close(
            @PathVariable UUID accountId, @AuthenticationPrincipal Jwt jwt) {
        return accountService.close(userId(jwt), accountId);
    }

    @GetMapping("/{accountId}/balance")
    public BalanceResponse balance(
            @PathVariable UUID accountId, @AuthenticationPrincipal Jwt jwt) {
        return accountService.balance(userId(jwt), accountId);
    }

    @GetMapping("/{accountId}/transactions")
    public Page<LedgerEntryResponse> transactions(
            @PathVariable UUID accountId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal Jwt jwt) {
        return accountService.transactions(
                userId(jwt),
                accountId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    }

    private UUID userId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token subject");
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token subject");
        }
    }
}
