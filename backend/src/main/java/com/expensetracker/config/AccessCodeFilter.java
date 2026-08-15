package com.expensetracker.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Gate akses API dengan shared access code (PIN). Semua request wajib membawa
 * kode via header X-Access-Code atau query access_code (untuk request gambar
 * yang tidak bisa mengirim header). /health dan /error dikecualikan.
 * Jika ACCESS_CODE kosong, filter dinonaktifkan (mode pengembangan).
 */
@Component
public class AccessCodeFilter extends OncePerRequestFilter {

    private final String accessCode;

    public AccessCodeFilter(@Value("${access.code:}") String accessCode) {
        this.accessCode = accessCode == null ? "" : accessCode.trim();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/health") || path.equals("/error");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (accessCode.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }
        String provided = request.getHeader("X-Access-Code");
        if (provided == null || provided.isBlank()) {
            provided = request.getParameter("access_code");
        }
        if (provided != null && constantTimeEquals(accessCode, provided.trim())) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"success\":false,\"message\":\"Akses ditolak\"}");
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] ba = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(ba, bb);
    }
}
