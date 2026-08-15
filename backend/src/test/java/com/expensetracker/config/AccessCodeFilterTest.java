package com.expensetracker.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AccessCodeFilterTest {

    private final FilterChain chain = mock(FilterChain.class);

    @Test
    void correctHeader_shouldPass() throws Exception {
        AccessCodeFilter filter = new AccessCodeFilter("482917");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/options");
        req.addHeader("X-Access-Code", "482917");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, chain);
        verify(chain).doFilter(any(), any());
        assertEquals(200, res.getStatus());
    }

    @Test
    void missingCode_shouldReturn401() throws Exception {
        AccessCodeFilter filter = new AccessCodeFilter("482917");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/expenses");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, chain);
        verify(chain, never()).doFilter(any(), any());
        assertEquals(401, res.getStatus());
        assertEquals("{\"success\":false,\"message\":\"Akses ditolak\"}", res.getContentAsString());
    }

    @Test
    void wrongCode_shouldReturn401() throws Exception {
        AccessCodeFilter filter = new AccessCodeFilter("482917");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/expenses");
        req.addHeader("X-Access-Code", "000000");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, chain);
        assertEquals(401, res.getStatus());
    }

    @Test
    void queryParam_shouldPass() throws Exception {
        AccessCodeFilter filter = new AccessCodeFilter("482917");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/invoices/abc/photo");
        req.setQueryString("access_code=482917");
        req.addParameter("access_code", "482917");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, chain);
        verify(chain).doFilter(any(), any());
        assertEquals(200, res.getStatus());
    }

    @Test
    void health_shouldAlwaysPass() throws Exception {
        AccessCodeFilter filter = new AccessCodeFilter("482917");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/health");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, chain);
        verify(chain).doFilter(any(), any());
        assertEquals(200, res.getStatus());
    }

    @Test
    void emptyCode_shouldPassEverything() throws Exception {
        AccessCodeFilter filter = new AccessCodeFilter("");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/expenses");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, chain);
        verify(chain).doFilter(any(), any());
        assertEquals(200, res.getStatus());
        assertEquals("", res.getContentAsString());
    }
}
