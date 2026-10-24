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
    public Optional<ApiResponse> begin(String scope, String key) {
        String scopedKey = scopedKey(scope, key);
        if (scopedKey == null) {
            return Optional.empty();
        }
        if (idempotencyRepository.claim(scopedKey)) {
            idempotencyRepository.deleteOlderThan(LocalDateTime.now().minusHours(TTL_HOURS));
            return Optional.empty();
        }
        String json = idempotencyRepository.find(scopedKey);
        if (json == null) {
            throw new DuplicateRequestException("Permintaan dengan Idempotency-Key yang sama sedang diproses");
        }
        if (json.contains("\"pending\":true")) {
            throw new DuplicateRequestException("Permintaan dengan Idempotency-Key yang sama sedang diproses");
        }
        return Optional.of(parse(json));
    }

    public void complete(String scope, String key, ApiResponse response) {
        String scopedKey = scopedKey(scope, key);
        if (scopedKey == null) {
            return;
        }
        try {
            idempotencyRepository.complete(scopedKey, objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            idempotencyRepository.delete(scopedKey);
        }
    }

    /** Lepas klaim agar request yang gagal bisa dicoba ulang dengan key yang sama. */
    public void abort(String scope, String key) {
        String scopedKey = scopedKey(scope, key);
        if (scopedKey != null) {
            idempotencyRepository.delete(scopedKey);
        }
    }

    private static String scopedKey(String scope, String key) {
        if (key == null || key.isBlank() || scope == null || scope.isBlank()) {
            return null;
        }
        return scope + ":" + key;
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
