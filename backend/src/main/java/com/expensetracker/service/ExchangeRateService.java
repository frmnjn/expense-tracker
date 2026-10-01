package com.expensetracker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Kurs mata uang asing -> IDR dari API kurs (fawazahmed0). Dipakai bersama oleh
 * analisis struk (scan) dan parsing email. Bila kurs tidak tersedia, pemanggil
 * yang memutuskan perilakunya.
 */
@Service
public class ExchangeRateService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExchangeRateService.class);

    private final ObjectMapper objectMapper;
    private final String fxApi;
    private final Duration fxTimeout;
    private final HttpClient httpClient;

    public ExchangeRateService(ObjectMapper objectMapper,
                               @Value("${ai.fx-api:}") String fxApi,
                               @Value("${ai.fx-timeout-ms:8000}") long fxTimeoutMs) {
        this.objectMapper = objectMapper;
        this.fxApi = fxApi == null ? "" : fxApi.trim();
        this.fxTimeout = Duration.ofMillis(fxTimeoutMs < 1 ? 8000 : fxTimeoutMs);
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    /** Tanggal kurs (YYYY-MM-DD) dari tanggal transaksi; fallback hari ini bila tak terbaca. */
    public static String exchangeDateOf(String cleanedDate) {
        if (cleanedDate == null || cleanedDate.isBlank()) {
            return LocalDate.now().toString();
        }
        String datePart = cleanedDate.substring(0, Math.min(10, cleanedDate.length()));
        try {
            return LocalDate.parse(datePart).toString();
        } catch (Exception e) {
            return LocalDate.now().toString();
        }
    }

    /**
     * Kurs 1 {base} = IDR pada {date}. Coba tanggal itu; bila future/belum rilis,
     * mundur hingga 5 hari. Mengembalikan null bila tetap gagal.
     */
    public Double rateToIdr(String base, String date) {
        if (base == null || base.isBlank() || "IDR".equalsIgnoreCase(base) || fxApi.isBlank()) {
            return null;
        }
        String currency = base.toUpperCase(Locale.ROOT);
        for (int back = 0; back <= 5; back++) {
            String target = back == 0 ? date : LocalDate.parse(date).minusDays(back).toString();
            try {
                Double rate = fetchRateFor(currency, target);
                if (rate != null) {
                    if (back > 0) {
                        LOGGER.warn("fx date {} unavailable, using {} for {}", date, target, currency);
                    }
                    return rate;
                }
            } catch (Exception e) {
                LOGGER.warn("fx fetch failed for {} at {}: {}", currency, target, e.getMessage());
            }
        }
        return null;
    }

    private Double fetchRateFor(String base, String date) throws Exception {
        String url = fxApi.replace("%DATE%", date).replace("%CUR%", base.toLowerCase(Locale.ROOT));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(fxTimeout)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return null;
        }
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode baseNode = root.path(base.toLowerCase(Locale.ROOT)).path("idr");
        if (baseNode.isMissingNode() || baseNode.asText().isBlank()) {
            return null;
        }
        double rate = baseNode.asDouble();
        return rate > 0 ? rate : null;
    }
}
