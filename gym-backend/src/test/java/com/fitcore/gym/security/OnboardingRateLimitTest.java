package com.fitcore.gym.security;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.*;

class OnboardingRateLimitTest {
    @Test
    void boundsPerClientAndGlobalRegistrationAttempts() {
        var limit =
                new OnboardingRateLimit(
                        Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC));
        for (int n = 0; n < 50; n++) assertThat(limit.allow("client")).isTrue();
        assertThat(limit.allow("client")).isFalse();
        for (int n = 0; n < 450; n++) assertThat(limit.allow("client-" + n)).isTrue();
        assertThat(limit.allow("fresh-client")).isFalse();
    }
}
