package com.expensetracker.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

/**
 * Gate akses API dengan shared access code (PIN). Semua request wajib membawa
 * kode via header X-Access-Code atau query access_code (untuk request media
 * yang tidak bisa mengirim header). /health dan /error dikecualikan.
 * Jika ACCESS_CODE kosong, filter dinonaktifkan (mode pengembangan).
 *
 * Percobaan gagal dibatasi per IP dan global untuk mencegah brute force.
 */
@Component
public class AccessCodeFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccessCodeFilter.class);
    private static final Pattern IP_PATTERN = Pattern.compile("[0-9a-fA-F:.]{3,45}");

    private final String accessCode;
    private final boolean allowBlank;
    private final AccessAttemptLimiter attemptLimiter;

    public AccessCodeFilter(@Value("${access.code:}") String accessCode,
                            @Value("${access.allow-blank:false}") boolean allowBlank,
                            AccessAttemptLimiter attemptLimiter) {
        this.accessCode = accessCode == null ? "" : accessCode.trim();
        this.allowBlank = allowBlank;
        this.attemptLimiter = attemptLimiter;
        if (this.accessCode.isEmpty()) {
            if (allowBlank) {
                LOGGER.warn("ACCESS_CODE kosong dan ACCESS_ALLOW_BLANK=true: autentikasi API DINONAKTIFKAN");
            } else {
                LOGGER.error("ACCESS_CODE kosong: semua request API akan ditolak. "
                        + "Set ACCESS_CODE atau ACCESS_ALLOW_BLANK=true untuk development.");
            }
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/health") || path.equals("/ready") || path.equals("/error");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (accessCode.isEmpty()) {
            if (allowBlank) {
                filterChain.doFilter(request, response);
            } else {
                writeJson(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        "ACCESS_CODE belum dikonfigurasi");
            }
            return;
        }

        String clientKey = clientKey(request);
        if (attemptLimiter.isBlocked(clientKey)) {
            long retryAfter = attemptLimiter.retryAfterSeconds(clientKey);
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            writeJson(response, 429, "Terlalu banyak percobaan. Coba lagi nanti.");
            return;
        }

        String provided = request.getHeader("X-Access-Code");
        if (provided == null || provided.isBlank()) {
            provided = request.getParameter("access_code");
        }
        if (provided != null && constantTimeEquals(accessCode, provided.trim())) {
            attemptLimiter.recordSuccess(clientKey);
            filterChain.doFilter(request, response);
            return;
        }

        attemptLimiter.recordFailure(clientKey);
        writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "Akses ditolak");
    }

    private static void writeJson(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"success\":false,\"message\":\"" + message + "\"}");
    }

    private static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // Nginx menambahkan remote address asli di elemen terakhir; elemen
            // sebelumnya bisa dikirim klien sehingga tidak boleh dipercaya.
            String[] parts = forwarded.split(",");
            String last = parts[parts.length - 1].trim();
            if (IP_PATTERN.matcher(last).matches()) {
                return last;
            }
        }
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] ba = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(ba, bb);
    }
}
