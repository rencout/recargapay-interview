package com.recargapay.walletservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recargapay.walletservice.dto.CreateWalletRequest;
import com.recargapay.walletservice.dto.TransactionRequest;
import com.recargapay.walletservice.dto.TransferRequest;
import com.recargapay.walletservice.util.WalletConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Transactional
class WalletControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("wallet_service_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @Test
    void createWallet_Success() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        CreateWalletRequest request = new CreateWalletRequest();
        request.setUserId("user123");

        mockMvc.perform(post("/api/wallets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value("user123"))
                .andExpect(jsonPath("$.balance").value("0.00"))
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void createWallet_InvalidRequest() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        CreateWalletRequest request = new CreateWalletRequest();
        request.setUserId(""); // Invalid empty userId

        mockMvc.perform(post("/api/wallets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getCurrentBalance_Success() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        // First create a wallet
        CreateWalletRequest createRequest = new CreateWalletRequest();
        createRequest.setUserId("user123");

        String createResponse = mockMvc.perform(post("/api/wallets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String walletId = objectMapper.readTree(createResponse).get("id").asText();

        // Then get the balance
        mockMvc.perform(get("/api/wallets/{walletId}/balance", walletId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(walletId))
                .andExpect(jsonPath("$.balance").value("0.00"));
    }

    @Test
    void getCurrentBalance_WalletNotFound() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        UUID nonExistentWalletId = UUID.randomUUID();

        mockMvc.perform(get("/api/wallets/{walletId}/balance", nonExistentWalletId))
                .andExpect(status().isNotFound());
    }

    @Test
    void deposit_Success() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        // First create a wallet
        CreateWalletRequest createRequest = new CreateWalletRequest();
        createRequest.setUserId("user123");

        String createResponse = mockMvc.perform(post("/api/wallets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String walletId = objectMapper.readTree(createResponse).get("id").asText();

        // Then deposit funds
        TransactionRequest depositRequest = new TransactionRequest();
        depositRequest.setAmount(WalletConstants.DEFAULT_TEST_AMOUNT);

        mockMvc.perform(post("/api/wallets/{walletId}/deposit", walletId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(depositRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(walletId))
                .andExpect(jsonPath("$.balanceAfter").value("100.00"));
    }

    @Test
    void withdraw_Success() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        // First create a wallet and deposit funds
        CreateWalletRequest createRequest = new CreateWalletRequest();
        createRequest.setUserId("user123");

        String createResponse = mockMvc.perform(post("/api/wallets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String walletId = objectMapper.readTree(createResponse).get("id").asText();

        // Deposit funds first
        TransactionRequest depositRequest = new TransactionRequest();
        depositRequest.setAmount(WalletConstants.DEFAULT_TEST_AMOUNT);

        mockMvc.perform(post("/api/wallets/{walletId}/deposit", walletId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(depositRequest)))
                .andExpect(status().isOk());

        // Then withdraw funds
        TransactionRequest withdrawRequest = new TransactionRequest();
        withdrawRequest.setAmount(BigDecimal.valueOf(30.00));

        mockMvc.perform(post("/api/wallets/{walletId}/withdraw", walletId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(withdrawRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(walletId))
                .andExpect(jsonPath("$.balanceAfter").value("70.00"));
    }

    @Test
    void withdraw_InsufficientFunds() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        // First create a wallet
        CreateWalletRequest createRequest = new CreateWalletRequest();
        createRequest.setUserId("user123");

        String createResponse = mockMvc.perform(post("/api/wallets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String walletId = objectMapper.readTree(createResponse).get("id").asText();

        // Try to withdraw more than available
        TransactionRequest withdrawRequest = new TransactionRequest();
        withdrawRequest.setAmount(BigDecimal.valueOf(50.00));

        mockMvc.perform(post("/api/wallets/{walletId}/withdraw", walletId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(withdrawRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transfer_Success() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        // Create two wallets
        CreateWalletRequest createRequest1 = new CreateWalletRequest();
        createRequest1.setUserId("user1");

        CreateWalletRequest createRequest2 = new CreateWalletRequest();
        createRequest2.setUserId("user2");

        String wallet1Response = mockMvc.perform(post("/api/wallets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest1)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String wallet2Response = mockMvc.perform(post("/api/wallets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest2)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String wallet1Id = objectMapper.readTree(wallet1Response).get("id").asText();
        String wallet2Id = objectMapper.readTree(wallet2Response).get("id").asText();

        // Deposit funds to source wallet
        TransactionRequest depositRequest = new TransactionRequest();
        depositRequest.setAmount(WalletConstants.DEFAULT_TEST_AMOUNT);

        mockMvc.perform(post("/api/wallets/{walletId}/deposit", wallet1Id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(depositRequest)))
                .andExpect(status().isOk());

        // Transfer funds
        TransferRequest transferRequest = new TransferRequest();
        transferRequest.setSourceWalletId(UUID.fromString(wallet1Id));
        transferRequest.setTargetWalletId(UUID.fromString(wallet2Id));
        transferRequest.setAmount(BigDecimal.valueOf(30.00));

        mockMvc.perform(post("/api/wallets/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isOk());
    }

    @Test
    void getHistoricalBalance_Success() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        // First create a wallet
        CreateWalletRequest createRequest = new CreateWalletRequest();
        createRequest.setUserId("user123");

        String createResponse = mockMvc.perform(post("/api/wallets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String walletId = objectMapper.readTree(createResponse).get("id").asText();

        // Create a transaction first by making a deposit
        TransactionRequest depositRequest = new TransactionRequest();
        depositRequest.setAmount(BigDecimal.valueOf(100.00));

        mockMvc.perform(post("/api/wallets/{walletId}/deposit", walletId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(depositRequest)))
                .andExpect(status().isOk());

        // Get historical balance after the transaction
        LocalDateTime timestamp = LocalDateTime.now();
        String formattedTimestamp = timestamp.format(DateTimeFormatter.ISO_DATE_TIME);

        mockMvc.perform(get("/api/wallets/{walletId}/balance/history", walletId)
                .param("timestamp", formattedTimestamp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(walletId))
                .andExpect(jsonPath("$.balance").value("100.00"));
    }

    @Test
    void getHistoricalBalance_NoTransactions_ThrowsException() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        // First create a wallet
        CreateWalletRequest createRequest = new CreateWalletRequest();
        createRequest.setUserId("user123");

        String createResponse = mockMvc.perform(post("/api/wallets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String walletId = objectMapper.readTree(createResponse).get("id").asText();

        // Try to get historical balance without any transactions
        LocalDateTime timestamp = LocalDateTime.now();
        String formattedTimestamp = timestamp.format(DateTimeFormatter.ISO_DATE_TIME);

        mockMvc.perform(get("/api/wallets/{walletId}/balance/history", walletId)
                .param("timestamp", formattedTimestamp))
                .andExpect(status().isInternalServerError());
    }
}
