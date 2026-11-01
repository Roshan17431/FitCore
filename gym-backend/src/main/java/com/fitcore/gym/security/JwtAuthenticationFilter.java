package com.fitcore.gym.security;

import com.fitcore.gym.domain.GymStatus;

import io.jsonwebtoken.JwtException;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import lombok.RequiredArgsConstructor;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final CustomUserDetailsService identities;
    private final com.fitcore.gym.repository.GymRepository gyms;

    static boolean publicRequest(HttpServletRequest r) {
        String p = r.getServletPath();
        return "OPTIONS".equals(r.getMethod())
                || ("POST".equals(r.getMethod())
                        && java.util.Set.of(
                                        "/api/auth/login",
                                        "/api/auth/register",
                                        "/api/auth/register-owner",
                                        "/api/auth/forgot-password")
                                .contains(p))
                || ("GET".equals(r.getMethod()) && p.equals("/api/health"))
                || p.equals("/error")
                || p.startsWith("/v3/api-docs")
                || p.startsWith("/swagger-ui");
    }

    static boolean hasSelector(HttpServletRequest r) {
        return r.getHeader("X-Gym-ID") != null
                || r.getParameterMap().keySet().stream()
                        .map(
                                k ->
                                        k.replace("_", "")
                                                .replace("-", "")
                                                .toLowerCase(java.util.Locale.ROOT))
                        .anyMatch(k -> java.util.Set.of("gymid", "tenantid").contains(k));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest r) {
        return publicRequest(r) && !hasSelector(r);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            if (hasSelector(request)) {
                error(response, 400, "Tenant selectors are not supported");
                return;
            }
            String h = request.getHeader("Authorization");
            if (h != null && h.startsWith("Bearer ")) {
                String token = h.substring(7);
                try {
                    String subject = jwtService.extractUsername(token);
                    if (subject == null || subject.isBlank()) {
                        throw new IllegalArgumentException("Missing token subject");
                    }
                    var account = identities.loadById(UUID.fromString(subject));
                    if (jwtService.isTokenValid(token, account)) {
                        if (gyms.findById(account.getGymId())
                                .map(g -> g.getStatus() != GymStatus.ACTIVE)
                                .orElse(true)) {
                            error(response, 403, "GYM_UNAVAILABLE");
                            return;
                        }
                        var actor =
                                new TenantActor(
                                        account.getId(),
                                        account.getGymId(),
                                        account.getRole(),
                                        account.getAuthzVersion());
                        request.setAttribute("fitcore.tenantActor", actor);
                        SecurityContextHolder.getContext()
                                .setAuthentication(
                                        new UsernamePasswordAuthenticationToken(
                                                actor, null, account.getAuthorities()));
                    }
                } catch (JwtException
                        | IllegalArgumentException
                        | AuthenticationException invalid) {
                    SecurityContextHolder.clearContext();
                }
            }
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void error(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message + "\"}");
    }
}
