package com.expensetracker.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AccessCodeFilterTest {

    private final FilterChain chain = mock(FilterChain.class);

    @Test
    void correctHeader_shouldPass() throws Exception {
        AccessCodeFilter filter = new AccessCodeFilter("482917", false, new AccessAttemptLimiter());
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/options");
        req.addHeader("X-Access-Code", "482917");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, chain);
        verify(chain).doFilter(any(), any());
        assertEquals(200, res.getStatus());
    }

    @Test
    void missingCode_shouldReturn401() throws Exception {
        AccessCodeFilter filter = new AccessCodeFilter("482917", false, new AccessAttemptLimiter());
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/expenses");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, chain);
        verify(chain, never()).doFilter(any(), any());
        assertEquals(401, res.getStatus());
        assertEquals("{\"success\":false,\"message\":\"Akses ditolak\"}", res.getContentAsString());
    }

    @Test
    void wrongCode_shouldReturn401() throws Exception {
        AccessCodeFilter filter = new AccessCodeFilter("482917", false, new AccessAttemptLimiter());
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/expenses");
        req.addHeader("X-Access-Code", "000000");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, chain);
        assertEquals(401, res.getStatus());
    }

    @Test
    void queryParam_shouldPass() throws Exception {
        AccessCodeFilter filter = new AccessCodeFilter("482917", false, new AccessAttemptLimiter());
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/invoices/abc/photo");
        req.setQueryString("access_code=482917");
        req.addParameter("access_code", "482917");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, chain);
        verify(chain).doFilter(any(), any());
        assertEquals(200, res.getStatus());
    }

    @Test
    void repeatedWrongCode_shouldBlockWith429() throws Exception {
        AccessAttemptLimiter limiter = new AccessAttemptLimiter();
        AccessCodeFilter filter = new AccessCodeFilter("482917", false, limiter);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/expenses");
        req.addHeader("X-Access-Code", "000000");
        req.setRemoteAddr("203.0.113.10");

        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, chain);
            assertEquals(401, res.getStatus());
        }

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(req, blocked, chain);
        assertEquals(429, blocked.getStatus());
        assertTrue(blocked.getContentAsString().contains("Terlalu banyak"));
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void health_shouldAlwaysPass() throws Exception {
        AccessCodeFilter filter = new AccessCodeFilter("482917", false, new AccessAttemptLimiter());
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/health");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, chain);
        verify(chain).doFilter(any(), any());
        assertEquals(200, res.getStatus());
    }

    @Test
    void emptyCode_withoutOptIn_shouldFailClosed() throws Exception {
        AccessCodeFilter filter = new AccessCodeFilter("", false, new AccessAttemptLimiter());
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/expenses");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, chain);
        verify(chain, never()).doFilter(any(), any());
        assertEquals(503, res.getStatus());
        assertTrue(res.getContentAsString().contains("ACCESS_CODE"));
    }

    @Test
    void emptyCode_withOptIn_shouldPassEverything() throws Exception {
        AccessCodeFilter filter = new AccessCodeFilter("", true, new AccessAttemptLimiter());
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/expenses");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, chain);
        verify(chain).doFilter(any(), any());
        assertEquals(200, res.getStatus());
        assertEquals("", res.getContentAsString());
    }

    @Test
    void forwardedFor_shouldUseLastHopNotClientSuppliedValue() throws Exception {
        AccessCodeFilter filter = new AccessCodeFilter("482917", false, new AccessAttemptLimiter());
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/expenses");
        req.addHeader("X-Access-Code", "000000");
        req.addHeader("X-Forwarded-For", "1.2.3.4, 203.0.113.10");
        for (int i = 0; i < 5; i++) {
            filter.doFilter(req, new MockHttpServletResponse(), chain);
        }
        MockHttpServletRequest real = new MockHttpServletRequest("GET", "/expenses");
        real.addHeader("X-Access-Code", "000000");
        real.addHeader("X-Forwarded-For", "9.9.9.9, 203.0.113.10");
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(real, blocked, chain);
        assertEquals(429, blocked.getStatus());
    }
}
