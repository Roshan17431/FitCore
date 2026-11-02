package com.fitcore.gym.config;

import com.fitcore.gym.email.TransactionalEmailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** A fail-closed extension point, not an email delivery implementation. */
@Configuration
public class EmailDeliveryConfiguration {
    @Bean
    public TransactionalEmailSender transactionalEmailSender(
            @Value("${fitcore.email.provider:disabled}") String provider) {
        if (!"disabled".equals(provider))
            throw new IllegalStateException("Email providers require a separately approved implementation");
        return new DisabledEmailSender();
    }

    public static final class DisabledEmailSender implements TransactionalEmailSender {
        @Override
        public void send(String recipient, String subject, String text) {
            throw new IllegalStateException("Email delivery is disabled");
        }
    }
}
