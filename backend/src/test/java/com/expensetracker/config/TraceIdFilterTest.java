package com.expensetracker.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TraceIdFilterTest {

    private final FilterChain chain = mock(FilterChain.class);

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void withoutHeader_shouldGenerateAndSetMdcAndResponseHeader() throws Exception {
        TraceIdFilter filter = new TraceIdFilter();
        AtomicReference<String> mdcDuringChain = new AtomicReference<>();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/options");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, (request, response) -> {
            mdcDuringChain.set(MDC.get(TraceIdFilter.MDC_TRACE_ID));
            chain.doFilter(request, response);
        });

        String traceId = res.getHeader(TraceIdFilter.TRACE_HEADER);
        assertNotNull(traceId);
        assertTrue(!traceId.isBlank());
        assertEquals(traceId, mdcDuringChain.get());
        verify(chain).doFilter(req, res);
    }

    @Test
    void withHeader_shouldUseProvidedTraceId() throws Exception {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/expenses");
        req.addHeader(TraceIdFilter.TRACE_HEADER, "  my-trace-123  ");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, (request, response) -> chain.doFilter(request, response));

        assertEquals("my-trace-123", res.getHeader(TraceIdFilter.TRACE_HEADER));
    }

    @Test
    void afterChain_shouldClearMdc() throws Exception {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/options");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, (request, response) -> chain.doFilter(request, response));

        assertTrue(MDC.getCopyOfContextMap() == null || MDC.getCopyOfContextMap().isEmpty());
    }
}
