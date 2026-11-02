package com.fitcore.gym.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

class CorsPolicyTest {
    SecurityConfig config(String origins) {
        var config=new SecurityConfig(mock(JwtAuthenticationFilter.class),mock(SecurityRequestLoggingFilter.class),mock(CustomUserDetailsService.class));
        ReflectionTestUtils.setField(config,"allowedOrigins",origins);return config;
    }
    @ParameterizedTest @ValueSource(strings={"*","https://*.example.invalid","null","https://app.invalid/path","https://app.invalid?secret=x"})
    void rejectsBroadOrMalformedOrigins(String origins) {
        assertThatThrownBy(()->config(origins).corsConfigurationSource()).isInstanceOf(IllegalStateException.class);
    }
    @Test void exactAllowlistDoesNotEnableCookieCredentials() {
        var cors=config("https://app.invalid,http://127.0.0.1:5175").corsConfigurationSource().getCorsConfiguration(new MockHttpServletRequest("GET","/api/gym"));
        assertThat(cors.checkOrigin("https://app.invalid")).isEqualTo("https://app.invalid");
        assertThat(cors.checkOrigin("https://evil.app.invalid")).isNull();
        assertThat(cors.getAllowCredentials()).isFalse();
    }
}
