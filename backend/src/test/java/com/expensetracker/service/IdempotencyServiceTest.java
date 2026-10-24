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
        when(idempotencyRepository.claim("key-1")).thenReturn(true);

        assertEquals(Optional.empty(), idempotencyService.begin("key-1"));
        verify(idempotencyRepository).claim("key-1");
    }

    @Test
    void begin_blankOrNullKey_shouldReturnEmpty() {
        assertEquals(Optional.empty(), idempotencyService.begin(null));
        assertEquals(Optional.empty(), idempotencyService.begin("  "));
        verify(idempotencyRepository, never()).claim(anyString());
    }

    @Test
    void begin_completedKey_shouldReturnCachedResponse() throws Exception {
        String json = new ObjectMapper().writeValueAsString(ApiResponse.ok(Map.of("id", "abc")));
        when(idempotencyRepository.claim("key-1")).thenReturn(false);
        when(idempotencyRepository.find("key-1")).thenReturn(json);

        Optional<ApiResponse> result = idempotencyService.begin("key-1");

        assertTrue(result.isPresent());
        assertTrue(result.get().success());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get().data();
        assertEquals("abc", data.get("id"));
    }

    @Test
    void begin_pendingKey_shouldRejectConcurrentRequest() {
        when(idempotencyRepository.claim("key-1")).thenReturn(false);
        when(idempotencyRepository.find("key-1")).thenReturn("{\"pending\":true}");

        assertThrows(DuplicateRequestException.class, () -> idempotencyService.begin("key-1"));
    }

    @Test
    void complete_shouldPersistSerializedResponse() {
        idempotencyService.complete("key-1", ApiResponse.ok());

        verify(idempotencyRepository).complete("key-1", "{\"success\":true}");
    }

    @Test
    void abort_shouldDeleteClaim() {
        idempotencyService.abort("key-1");

        verify(idempotencyRepository).delete("key-1");
    }

    @Test
    void complete_blankKey_shouldDoNothing() {
        idempotencyService.complete(null, ApiResponse.ok());
        idempotencyService.complete("", ApiResponse.ok());

        verify(idempotencyRepository, never()).complete(anyString(), anyString());
    }
}
