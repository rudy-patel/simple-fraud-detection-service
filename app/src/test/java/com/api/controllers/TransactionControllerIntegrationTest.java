package com.api.controllers;

import com.api.models.MonitoringStats;
import com.api.models.Transaction;
import com.api.models.TransactionAnalysisResponse;
import com.api.models.TransactionRequest;
import com.api.models.TransactionStatus;
import com.api.services.ExternalApiService;
import com.configuration.TestConfiguration;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Disabled;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Collections;

import javax.naming.ServiceUnavailableException;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("TransactionController-IntegTest")
@Import(TestConfiguration.class)
@RunWith(SpringRunner.class)
public class TransactionControllerIntegrationTest {
    private static final int VALID_AMOUNT = 5000;
    private static final double AMOUNT_OVER_LIMIT = 50000.1;
    private static final Integer VALID_CARD_USAGE_COUNT = 40;
    private static final long CARD_NUM = 5206840000000001L;
    private static final String OBFUSCATED_CARD_NUM = "5206********0001";
    
    private TransactionRequest request;
    private Transaction transaction;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ExternalApiService externalApiService;

    @Autowired
    private TransactionController transactionController;

    @Before
    public void setUp() throws ServiceUnavailableException, IOException {
        request = new TransactionRequest();
        objectMapper = new ObjectMapper();
        transaction = new Transaction();
        transaction.setCardNum(CARD_NUM);
        request.setTransaction(transaction);

        when(externalApiService.fetchCardUsageCounts(CARD_NUM))
        .thenReturn(Collections.singletonList(VALID_CARD_USAGE_COUNT));
    }

    @Test
    @Disabled("Skipping setup for this test")
    public void testAnalyzeTransaction_fullEndToEndWorkflow() throws JsonMappingException, JsonProcessingException, UnsupportedEncodingException, Exception {
        request = new TransactionRequest();
        objectMapper = new ObjectMapper();
        transaction = new Transaction();
        
        request.setTransaction(transaction);
        transaction.setCardNum(CARD_NUM);
        transaction.setAmount(VALID_AMOUNT);

        // Send the request
        TransactionAnalysisResponse response = sendRequestAndGetResponse();

        // Validate the response
        assertEquals(OBFUSCATED_CARD_NUM, response.getCardNumber());
        assertEquals(VALID_AMOUNT, response.getTransactionAmount());
}

    @Test
    public void testAnalyzeTransaction_happyCase_TransactionSuccessful() throws Exception {
        transaction.setAmount(VALID_AMOUNT);

        // Send the request
        TransactionAnalysisResponse response = sendRequestAndGetResponse();

        // Validate the response
        assertEquals(OBFUSCATED_CARD_NUM, response.getCardNumber());
        assertEquals(VALID_AMOUNT, response.getTransactionAmount());
        assertEquals(TransactionStatus.APPROVED, response.getTransactionStatus());
    }

    @Test
    public void testAnalyzeTransaction_AmountOverLimit_TransactionDeclined() throws Exception {
        transaction.setAmount(AMOUNT_OVER_LIMIT);

        // Send the request
        TransactionAnalysisResponse response = sendRequestAndGetResponse();
        
        // Validate the response
        assertEquals(OBFUSCATED_CARD_NUM, response.getCardNumber());
        assertEquals(AMOUNT_OVER_LIMIT, response.getTransactionAmount());
        assertEquals(TransactionStatus.DECLINED, response.getTransactionStatus());
    }

    @Test
    public void testAnalyzeTransaction_cardUsageTooLow_transactionDeclined() throws Exception {
        transaction.setAmount(VALID_AMOUNT);
        int cardUsageTooLow = 5;

        when(externalApiService.fetchCardUsageCounts(CARD_NUM))
                .thenReturn(Collections.singletonList(cardUsageTooLow));

        // Send the request
        TransactionAnalysisResponse response = sendRequestAndGetResponse();

        // Validate the response
        assertEquals(OBFUSCATED_CARD_NUM, response.getCardNumber());
        assertEquals(VALID_AMOUNT, response.getTransactionAmount());
        assertEquals(TransactionStatus.DECLINED, response.getTransactionStatus());
    }

    @Test
    public void testAnalyzeTransaction_cardUsageTooHigh_transactionDeclined() throws Exception {
        transaction.setAmount(VALID_AMOUNT);
        int cardUsageTooHigh = 70;

        when(externalApiService.fetchCardUsageCounts(CARD_NUM))
                .thenReturn(Collections.singletonList(cardUsageTooHigh));

        // Send the request
        TransactionAnalysisResponse response = sendRequestAndGetResponse();

        // Validate the response
        assertEquals(OBFUSCATED_CARD_NUM, response.getCardNumber());
        assertEquals(VALID_AMOUNT, response.getTransactionAmount());
        assertEquals(TransactionStatus.DECLINED, response.getTransactionStatus());
    }

    @Test
    public void testAnalyzeTransaction_missingTransactionField_badRequest() throws Exception {
        String requestBody = "{}";

        // Send the request and assert bad request
        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders
                .post("/analyzeTransaction")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andReturn();
    }

    @Test
    public void testAnalyzeTransaction_invalidCardNumber_badRequest() throws Exception {
        String requestBody = "{\"transaction\": {\"cardNum\": 123, \"amount\": 1000}}";

        // Send the request
        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders
                .post("/analyzeTransaction")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andReturn();

        // Extract the response body and assert its contents
        String responseBody = mvcResult.getResponse().getContentAsString();
        assertTrue(responseBody.contains("Card number must be at least 16 digits long"));
    }

    @Test
    public void testAnalyzeTransaction_invalidTransactionAmount_badRequest() throws Exception {
        String requestBody = "{\"transaction\": {\"cardNum\": 1234567890123456, \"amount\": -100}}";

        // Send the request and assert bad request
        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders
                .post("/analyzeTransaction")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andReturn();

        // Extract the response body and assert its contents
        String responseBody = mvcResult.getResponse().getContentAsString();
        assertTrue(responseBody.contains("Transaction amount must be greater than or equal to 0"));
    }

    @Test
    public void testAnalyzeTransaction_externalApiServiceUnavailable_internalServerError() throws Exception {
        transaction.setAmount(VALID_AMOUNT);

        when(externalApiService.fetchCardUsageCounts(CARD_NUM)).thenThrow(ServiceUnavailableException.class);

        // Send the request and assert internal server error
        mockMvc.perform(MockMvcRequestBuilders
                .post("/analyzeTransaction")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isInternalServerError())
                .andReturn();
    }

    @Test
    public void testAnalyzeTransaction_jsonProcessingException_badRequest() throws Exception {
        // Prepare the request payload with invalid JSON
        String requestBody = "{";

        // Send the request and ensure bad request
        mockMvc.perform(MockMvcRequestBuilders
                .post("/analyzeTransaction")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andReturn();
    }

    private TransactionAnalysisResponse sendRequestAndGetResponse()
            throws Exception, JsonProcessingException, UnsupportedEncodingException, JsonMappingException {
        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders
                .post("/analyzeTransaction")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn();

        // Extract the response body and assert its contents
        String responseBody = mvcResult.getResponse().getContentAsString();
        TransactionAnalysisResponse response = objectMapper.readValue(responseBody, TransactionAnalysisResponse.class);
        
        return response;
    }

    @Test
    public void testGetMonitoringStats_ReturnsCorrectStats() throws Exception {
        // Set up the expected monitoring stats
        int expectedTransactionCount = 10;
        double expectedTotalTransactionAmount = 1000.50;
        transactionController.setTransactionCount(expectedTransactionCount);
        transactionController.setTotalTransactionAmount(expectedTotalTransactionAmount);

        // Send the request to the monitoring stats endpoint
        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders
                .get("/monitoringStats")
                .accept(MediaType.APPLICATION_JSON))
                .andReturn();

        // Verify the response status code and content
        int statusCode = mvcResult.getResponse().getStatus();
        assertEquals(HttpStatus.OK.value(), statusCode);

        String responseBody = mvcResult.getResponse().getContentAsString();
        MonitoringStats monitoringStats = objectMapper.readValue(responseBody, MonitoringStats.class);
        assertEquals(expectedTransactionCount, monitoringStats.getTransactionCount());
        assertEquals(expectedTotalTransactionAmount, monitoringStats.getTotalTransactionAmount());
    }
}
