package com.learning.financeflow.account.controller;

import com.learning.financeflow.account.api.CreateTransferRequest;
import com.learning.financeflow.account.api.TransferResponse;
import com.learning.financeflow.account.model.AccountTransfer;
import com.learning.financeflow.account.service.TransferService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Validated
@RestController
@RequestMapping("/api/v1/transfers")
@SecurityRequirement(name = "bearerAuth")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> create(
            @RequestHeader("Idempotency-Key")
                    @NotBlank @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody CreateTransferRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        AccountTransfer transfer = transferService.create(userId(jwt), idempotencyKey, request);
        return ResponseEntity.created(URI.create("/api/v1/transfers/" + transfer.getId()))
                .body(TransferResponse.from(transfer));
    }

    @GetMapping
    public Page<TransferResponse> list(
            @RequestParam(defaultValue = "0") @jakarta.validation.constraints.Min(0) int page,
            @RequestParam(defaultValue = "20")
                    @jakarta.validation.constraints.Min(1)
                    @jakarta.validation.constraints.Max(100) int size,
            @AuthenticationPrincipal Jwt jwt) {
        return transferService
                .list(
                        userId(jwt),
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")))
                .map(TransferResponse::from);
    }

    @GetMapping("/{transferId}")
    public TransferResponse get(
            @PathVariable UUID transferId, @AuthenticationPrincipal Jwt jwt) {
        return TransferResponse.from(transferService.get(userId(jwt), transferId));
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
