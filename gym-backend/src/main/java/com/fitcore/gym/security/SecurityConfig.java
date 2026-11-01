package com.fitcore.gym.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final SecurityRequestLoggingFilter securityRequestLoggingFilter;
    private final CustomUserDetailsService userDetailsService;

    @Value(
            "${cors.allowed-origins:https://fit-core-git-master-roshan17431s-projects.vercel.app,https://fit-core-one-beta.vercel.app,http://localhost:5173,http://127.0.0.1:5173}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(HttpMethod.OPTIONS, "/**")
                                        .permitAll()
                                        .requestMatchers(
                                                "/api/health",
                                                "/v3/api-docs/**",
                                                "/swagger-ui/**",
                                                "/swagger-ui.html",
                                                "/error")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/auth/login",
                                                "/api/auth/register",
                                                "/api/auth/register-owner",
                                                "/api/auth/forgot-password")
                                        .permitAll()
                                        .requestMatchers("/api/admin/**")
                                        .hasAnyRole("OWNER", "ADMIN")
                                        .anyRequest()
                                        .authenticated())
                .exceptionHandling(
                        exceptions ->
                                exceptions
                                        .authenticationEntryPoint(
                                                this::handleAuthenticationRequired)
                                        .accessDeniedHandler(this::handleAccessDenied))
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(
                        securityRequestLoggingFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins =
                Arrays.stream(allowedOrigins.split(","))
                        .map(String::trim)
                        .filter(origin -> !origin.isBlank())
                        .toList();

        log.info("Configured CORS allowed origin patterns: {}", origins);
        configuration.setAllowedOriginPatterns(origins);
        configuration.setAllowedMethods(
                Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(
                Arrays.asList(
                        "Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public static PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private void handleAuthenticationRequired(
            HttpServletRequest request, HttpServletResponse response, Exception exception)
            throws IOException {
        log.warn(
                "Security rejected unauthenticated request: method={} path={} reason={}",
                request.getMethod(),
                request.getRequestURI(),
                exception.getMessage());
        writeSecurityError(
                response,
                HttpStatus.UNAUTHORIZED,
                "Authentication required",
                request.getRequestURI());
    }

    private void handleAccessDenied(
            HttpServletRequest request, HttpServletResponse response, Exception exception)
            throws IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String user = authentication == null ? "anonymous" : authentication.getName();
        String authorities =
                authentication == null ? "[]" : authentication.getAuthorities().toString();

        log.warn(
                "Security denied request: method={} path={} user={} authorities={} reason={}",
                request.getMethod(),
                request.getRequestURI(),
                user,
                authorities,
                exception.getMessage());
        writeSecurityError(
                response, HttpStatus.FORBIDDEN, "Access denied", request.getRequestURI());
    }

    private void writeSecurityError(
            HttpServletResponse response, HttpStatus status, String message, String path)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter()
                .write(
                        String.format(
                                "{\"status\":%d,\"error\":\"%s\",\"message\":\"%s\",\"path\":\"%s\"}",
                                status.value(),
                                escapeJson(status.getReasonPhrase()),
                                escapeJson(message),
                                escapeJson(path)));
    }

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
