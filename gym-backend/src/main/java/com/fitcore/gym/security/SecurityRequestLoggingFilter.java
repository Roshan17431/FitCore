package com.fitcore.gym.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;

@Component
@Slf4j
public class SecurityRequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        String authorizationHeader = request.getHeader("Authorization");

        log.info("Incoming request: method={} path={} origin={} contentType={} hasAuthorization={}",
                request.getMethod(),
                request.getRequestURI(),
                request.getHeader("Origin"),
                request.getContentType(),
                authorizationHeader != null && !authorizationHeader.isBlank());

        try {
            filterChain.doFilter(request, response);
        } finally {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String user = authentication == null || !authentication.isAuthenticated()
                    ? "anonymous"
                    : authentication.getName();
            String authorities = authentication == null ? "[]" : authentication.getAuthorities().toString();
            Object matchedEndpoint = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            long elapsedMs = System.currentTimeMillis() - start;

            if (response.getStatus() == HttpServletResponse.SC_FORBIDDEN) {
                log.warn("Request completed with 403: method={} path={} matchedEndpoint={} user={} authorities={} elapsedMs={}",
                        request.getMethod(),
                        request.getRequestURI(),
                        matchedEndpoint == null ? "unmatched" : matchedEndpoint,
                        user,
                        authorities,
                        elapsedMs);
            } else {
                log.info("Request completed: status={} method={} path={} matchedEndpoint={} user={} authorities={} elapsedMs={}",
                        response.getStatus(),
                        request.getMethod(),
                        request.getRequestURI(),
                        matchedEndpoint == null ? "unmatched" : matchedEndpoint,
                        user,
                        authorities,
                        elapsedMs);
            }
        }
    }
}
