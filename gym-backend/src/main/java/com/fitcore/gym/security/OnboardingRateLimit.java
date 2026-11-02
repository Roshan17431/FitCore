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

public class OnboardingRateLimit extends OncePerRequestFilter {
    private final Clock clock;
    private final SharedAbuseLimiter shared;
    public OnboardingRateLimit(Clock clock){this.clock=clock;this.shared=null;}
    @org.springframework.beans.factory.annotation.Autowired
    public OnboardingRateLimit(Clock clock,SharedAbuseLimiter shared){this.clock=clock;this.shared=shared;}
    private boolean permitted(String path,String ip){
        if(shared!=null && shared.postgres())return shared.allow(path,ip);
        return allow(ip); // H2 exists only on the test classpath; never a PostgreSQL fallback.
    }
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
        String path=r.getRequestURI().substring(r.getContextPath().length());
        if(r.getMethod().equals("POST") && Set.of("/api/auth/register","/api/auth/register-owner",
            "/api/auth/registration/owner","/api/auth/registration/invitation","/api/auth/verify-email",
            "/api/auth/verification/request","/api/auth/forgot-password","/api/auth/reset-password").contains(path)) {
            try { if(!permitted(path,r.getRemoteAddr())) {reject(s,429,"Security request rate exceeded","300");return;} }
            catch(RuntimeException unavailable){reject(s,503,"Security service temporarily unavailable","30");return;}
        }
        chain.doFilter(r, s);
    }
    private void reject(HttpServletResponse response,int code,String message,String retry)throws IOException {
        response.setStatus(code);response.setHeader("Retry-After",retry);response.setContentType("application/json");
        response.getWriter().write("{\"message\":\""+message+"\"}");
    }

}
