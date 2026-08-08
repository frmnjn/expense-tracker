package com.expensetracker.service;

import com.expensetracker.data.IdempotencyRepository;
import com.expensetracker.model.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private IdempotencyRepository idempotencyRepository;

    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        idempotencyService = new IdempotencyService(idempotencyRepository, new ObjectMapper());
    }

    @Test
    void find_keyWithoutCachedResponse_shouldReturnEmpty() {
        when(idempotencyRepository.find("key-1")).thenReturn(null);
        assertEquals(Optional.empty(), idempotencyService.find("key-1"));
    }

    @Test
    void find_blankOrNullKey_shouldReturnEmpty() {
        assertEquals(Optional.empty(), idempotencyService.find(null));
        assertEquals(Optional.empty(), idempotencyService.find("  "));
        verify(idempotencyRepository, never()).find(anyString());
    }

    @Test
    void find_existingCachedResponse_shouldReturnReconstructedResponse() throws Exception {
        String json = new ObjectMapper().writeValueAsString(ApiResponse.ok(Map.of("id", "abc")));
        when(idempotencyRepository.find("key-1")).thenReturn(json);
        Optional<ApiResponse> result = idempotencyService.find("key-1");
        assertTrue(result.isPresent());
        assertTrue(result.get().success());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get().data();
        assertEquals("abc", data.get("id"));
    }

    @Test
    void save_shouldPersistAndCleanupOldRows() {
        idempotencyService.save("key-1", ApiResponse.ok());
        verify(idempotencyRepository).save(eq("key-1"), anyString());
        verify(idempotencyRepository).deleteOlderThan(any(LocalDateTime.class));
    }

    @Test
    void save_blankOrNullKey_shouldDoNothing() {
        idempotencyService.save(null, ApiResponse.ok());
        idempotencyService.save("", ApiResponse.ok());
        verify(idempotencyRepository, never()).save(anyString(), anyString());
        verify(idempotencyRepository, never()).deleteOlderThan(any(LocalDateTime.class));
    }
}
