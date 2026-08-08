package com.expensetracker.service;

import com.expensetracker.data.IdempotencyRepository;
import com.expensetracker.model.ApiResponse;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Service
public class IdempotencyService {

    private static final long TTL_HOURS = 24;

    private final IdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyRepository idempotencyRepository, ObjectMapper objectMapper) {
        this.idempotencyRepository = idempotencyRepository;
        this.objectMapper = objectMapper;
    }

    public Optional<ApiResponse> find(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        String json = idempotencyRepository.find(key);
        if (json == null) {
            return Optional.empty();
        }
        try {
            Map<?, ?> parsed = objectMapper.readValue(json, Map.class);
            Object data = parsed.get("data");
            return Optional.of(new ApiResponse(true, null, data));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void save(String key, ApiResponse response) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(response);
            idempotencyRepository.save(key, json);
        } catch (Exception e) {
            return;
        }
        idempotencyRepository.deleteOlderThan(LocalDateTime.now().minusHours(TTL_HOURS));
    }
}
