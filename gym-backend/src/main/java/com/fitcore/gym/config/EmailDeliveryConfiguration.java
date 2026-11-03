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
            @Value("${fitcore.email.provider:disabled}") String provider,
            @Value("${fitcore.email.outbox.enabled:false}") boolean queued, org.springframework.beans.factory.ObjectProvider<com.fitcore.gym.email.EmailOutbox> outbox) {
        if (!"disabled".equals(provider))
            throw new IllegalStateException("Email providers require a separately approved implementation");
        if(queued)return new TransactionalEmailSender(){
            public void send(String r,String s,String t){throw new IllegalStateException("Structured transactional intent required");}
            public void enqueue(com.fitcore.gym.email.EmailIntent intent){outbox.getObject().enqueue(intent);}
        };
        return new DisabledEmailSender();
    }

    public static final class DisabledEmailSender implements TransactionalEmailSender {
        @Override
        public void send(String recipient, String subject, String text) {
            throw new IllegalStateException("Email delivery is disabled");
        }
    }
}
