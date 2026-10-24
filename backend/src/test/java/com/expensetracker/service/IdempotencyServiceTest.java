package com.expensetracker.service;

import com.expensetracker.data.IdempotencyRepository;
import com.expensetracker.model.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
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
    void begin_newKey_shouldClaimAndReturnEmpty() {
        when(idempotencyRepository.claim("expenses:key-1")).thenReturn(true);

        assertEquals(Optional.empty(), idempotencyService.begin("expenses", "key-1"));
        verify(idempotencyRepository).claim("expenses:key-1");
    }

    @Test
    void begin_blankOrNullKey_shouldReturnEmpty() {
        assertEquals(Optional.empty(), idempotencyService.begin("expenses", null));
        assertEquals(Optional.empty(), idempotencyService.begin("expenses", "  "));
        verify(idempotencyRepository, never()).claim(anyString());
    }

    @Test
    void begin_completedKey_shouldReturnCachedResponse() throws Exception {
        String json = new ObjectMapper().writeValueAsString(ApiResponse.ok(Map.of("id", "abc")));
        when(idempotencyRepository.claim("expenses:key-1")).thenReturn(false);
        when(idempotencyRepository.find("expenses:key-1")).thenReturn(json);

        Optional<ApiResponse> result = idempotencyService.begin("expenses", "key-1");

        assertTrue(result.isPresent());
        assertTrue(result.get().success());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get().data();
        assertEquals("abc", data.get("id"));
    }

    @Test
    void begin_pendingKey_shouldRejectConcurrentRequest() {
        when(idempotencyRepository.claim("expenses:key-1")).thenReturn(false);
        when(idempotencyRepository.find("expenses:key-1")).thenReturn("{\"pending\":true}");

        assertThrows(DuplicateRequestException.class, () -> idempotencyService.begin("expenses", "key-1"));
    }

    @Test
    void complete_shouldPersistSerializedResponse() {
        idempotencyService.complete("expenses", "key-1", ApiResponse.ok());

        verify(idempotencyRepository).complete("expenses:key-1", "{\"success\":true}");
    }

    @Test
    void abort_shouldDeleteClaim() {
        idempotencyService.abort("expenses", "key-1");

        verify(idempotencyRepository).delete("expenses:key-1");
    }

    @Test
    void begin_sameKeyDifferentScope_shouldUseIndependentKeys() {
        when(idempotencyRepository.claim("expenses:key-1")).thenReturn(true);
        when(idempotencyRepository.claim("topups:key-1")).thenReturn(true);

        assertEquals(Optional.empty(), idempotencyService.begin("expenses", "key-1"));
        assertEquals(Optional.empty(), idempotencyService.begin("topups", "key-1"));

        verify(idempotencyRepository).claim("expenses:key-1");
        verify(idempotencyRepository).claim("topups:key-1");
    }

    @Test
    void complete_blankKey_shouldDoNothing() {
        idempotencyService.complete("expenses", null, ApiResponse.ok());
        idempotencyService.complete("expenses", "", ApiResponse.ok());

        verify(idempotencyRepository, never()).complete(anyString(), anyString());
    }
}
