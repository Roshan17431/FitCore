package com.fitcore.gym.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.*;

/** Single-process demo limiter: ignores spoofable forwarding headers; bounded storage. */
@Component
@RequiredArgsConstructor
public class OnboardingRateLimit extends OncePerRequestFilter {
    private final Clock clock;
    private final Map<String, Integer> clients = new HashMap<>();
    private long window = -1;
    private int total;

    synchronized boolean allow(String ip) {
        long current = clock.instant().getEpochSecond() / 300;
        if (current != window) {
            clients.clear();
            total = 0;
            window = current;
        }
        if (total >= 500
                || clients.getOrDefault(ip, 0) >= 50
                || (!clients.containsKey(ip) && clients.size() >= 1024)) return false;
        total++;
        clients.merge(ip, 1, Integer::sum);
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest r, HttpServletResponse s, FilterChain chain)
            throws ServletException, IOException {
        if (r.getMethod().equals("POST")
                && Set.of("/api/auth/register", "/api/auth/register-owner")
                        .contains(r.getRequestURI().substring(r.getContextPath().length()))
                && !allow(r.getRemoteAddr())) {
            s.setStatus(429);
            s.setHeader("Retry-After", "300");
            s.setContentType("application/json");
            s.getWriter().write("{\"message\":\"Registration rate exceeded\"}");
            return;
        }
        chain.doFilter(r, s);
    }
}
