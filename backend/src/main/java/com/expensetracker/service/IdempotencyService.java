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

    /**
     * Klaim key sebelum side effect dijalankan. Bila key sudah selesai, hasil
     * lama dikembalikan. Bila request lain masih memproses key yang sama,
     * lempar {@link DuplicateRequestException} agar tidak terjadi double write.
     */
    public Optional<ApiResponse> begin(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        if (idempotencyRepository.claim(key)) {
            idempotencyRepository.deleteOlderThan(LocalDateTime.now().minusHours(TTL_HOURS));
            return Optional.empty();
        }
        String json = idempotencyRepository.find(key);
        if (json == null) {
            throw new DuplicateRequestException("Permintaan dengan Idempotency-Key yang sama sedang diproses");
        }
        if (json.contains("\"pending\":true")) {
            throw new DuplicateRequestException("Permintaan dengan Idempotency-Key yang sama sedang diproses");
        }
        return Optional.of(parse(json));
    }

    public void complete(String key, ApiResponse response) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            idempotencyRepository.complete(key, objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            idempotencyRepository.delete(key);
        }
    }

    /** Lepas klaim agar request yang gagal bisa dicoba ulang dengan key yang sama. */
    public void abort(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        idempotencyRepository.delete(key);
    }

    private ApiResponse parse(String json) {
        try {
            Map<?, ?> parsed = objectMapper.readValue(json, Map.class);
            Object data = parsed.get("data");
            return new ApiResponse(true, null, data);
        } catch (Exception e) {
            throw new DuplicateRequestException("Respons idempotensi tidak valid");
        }
    }
}
