package com.expensetracker.controller;

import com.expensetracker.config.ApiExceptionHandler;
import com.expensetracker.service.ExpenseService;
import com.expensetracker.service.IdempotencyService;
import com.expensetracker.service.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ExpenseControllerStatusTest {

    @Mock
    private ExpenseService expenseService;
    @Mock
    private IdempotencyService idempotencyService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ExpenseController controller = new ExpenseController(expenseService, idempotencyService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void missingExpense_shouldReturn404WithErrorEnvelope() throws Exception {
        when(expenseService.getExpense("missing")).thenThrow(new NotFoundException("Expense not found"));

        mockMvc.perform(get("/expenses/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Expense not found"));
    }

    @Test
    void createBudget_shouldReturn201() throws Exception {
        when(idempotencyService.begin("budgets", "key-1")).thenReturn(Optional.empty());
        doNothing().when(expenseService).createBudget(org.mockito.ArgumentMatchers.any());

        mockMvc.perform(post("/budgets")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Daily\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void createBudget_duplicateIdempotencyKey_shouldReturnCached200() throws Exception {
        when(idempotencyService.begin("budgets", "key-1"))
                .thenReturn(Optional.of(com.expensetracker.model.ApiResponse.ok()));

        mockMvc.perform(post("/budgets")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Daily\"}"))
                .andExpect(status().isOk());
    }
}
