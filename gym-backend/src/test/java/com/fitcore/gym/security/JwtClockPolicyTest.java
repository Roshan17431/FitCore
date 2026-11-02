package com.fitcore.gym.security;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class JwtClockPolicyTest {
    @ParameterizedTest @ValueSource(ints={-1,121,Integer.MAX_VALUE})
    void unsafeIssueTimeTolerancePreventsStartup(int seconds) {
        new ApplicationContextRunner().withUserConfiguration(JwtService.class)
            .withPropertyValues("jwt.secret=synthetic-jwt-clock-regression-key-at-least-32-characters","jwt.expiration-ms=60000","jwt.allowed-issue-clock-skew-seconds="+seconds)
            .run(context->assertThat(context).hasFailed());
    }
}
