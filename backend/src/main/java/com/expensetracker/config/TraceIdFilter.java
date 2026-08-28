package com.expensetracker.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Menambahkan trace id pada setiap request agar semua log yang terkait dapat
 * ditelusuri. Trace id diambil dari header X-Trace-Id (bila client mengirim),
 * atau di-generate UUID bila tidak ada. Dimasukkan ke MDC ("trace.id") sehingga
 * otomatis muncul di log ECS, dan dikembalikan ke client via header.
 * Dijalankan paling awal agar trace id sudah tersedia untuk filter lain.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_HEADER = "X-Trace-Id";
    public static final String MDC_TRACE_ID = "trace.id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = resolveTraceId(request);
        MDC.put(MDC_TRACE_ID, traceId);
        response.setHeader(TRACE_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_TRACE_ID);
        }
    }

    private static String resolveTraceId(HttpServletRequest request) {
        String provided = request.getHeader(TRACE_HEADER);
        if (provided == null || provided.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return provided.trim();
    }
}
