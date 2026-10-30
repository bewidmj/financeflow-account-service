package com.learning.financeflow.account.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.learning.financeflow.account.api.AccountResponse;
import com.learning.financeflow.account.api.CreateAccountRequest;
import com.learning.financeflow.account.model.AccountStatus;
import com.learning.financeflow.account.model.AccountType;
import com.learning.financeflow.account.service.AccountService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AccountController.class)
@AutoConfigureMockMvc
@Import(com.learning.financeflow.account.config.SecurityConfig.class)
@TestPropertySource(properties = "app.security.jwks-uri=http://localhost:8081/.well-known/jwks.json")
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountService accountService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void createUsesAuthenticatedSubjectRatherThanRequestUserId() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AccountResponse response = new AccountResponse(
                accountId,
                "Savings",
                AccountType.SAVINGS,
                "INR",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                AccountStatus.ACTIVE,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"));
        when(accountService.create(eq(userId), any(CreateAccountRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/accounts")
                        .with(jwt().jwt(token -> token.subject(userId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "00000000-0000-0000-0000-000000000001",
                                  "name": "Savings",
                                  "accountType": "SAVINGS",
                                  "openingBalance": 0.00
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(accountId.toString()))
                .andExpect(jsonPath("$.currency").value("INR"));

        verify(accountService).create(eq(userId), any(CreateAccountRequest.class));
    }

    @Test
    void rejectsInvalidAccountRequest() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "accountType": "SAVINGS",
                                  "openingBalance": -3.50
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }
}
