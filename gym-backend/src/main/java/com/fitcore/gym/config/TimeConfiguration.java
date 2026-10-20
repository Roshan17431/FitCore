package com.fitcore.gym.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

@Configuration
public class TimeConfiguration {
    /** Membership dates and trainer wall times use the existing Render UTC convention. */
    @Bean
    public Clock applicationClock() {
        return Clock.systemUTC();
    }
}
