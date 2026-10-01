package com.hrplatform.common.web;

import com.hrplatform.common.api.TraceId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = TraceId.normalize(request.getHeader(HEADER_NAME));
        if (traceId == null) {
            traceId = TraceId.generate();
        }
        request.setAttribute(TraceId.REQUEST_ATTRIBUTE, traceId);
        response.setHeader(HEADER_NAME, traceId);
        TraceId.bind(traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            TraceId.clear();
        }
    }
}
