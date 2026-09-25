package com.dogfood.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_ATTRIBUTE =
            "dogfood.requestId";

    public static final String HEADER =
            "X-Request-Id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String requestId =
                request.getHeader(HEADER);

        if (requestId == null || requestId.isBlank()) {
            requestId =
                    "req_" + UUID.randomUUID();
        }

        request.setAttribute(
                REQUEST_ID_ATTRIBUTE,
                requestId
        );

        response.setHeader(
                HEADER,
                requestId
        );

        filterChain.doFilter(
                request,
                response
        );
    }

    public static String getRequestId(
            HttpServletRequest request
    ) {
        Object value =
                request.getAttribute(
                        REQUEST_ID_ATTRIBUTE
                );

        return value == null
                ? "unknown"
                : value.toString();
    }
}
