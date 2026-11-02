package com.fitcore.gym.email;

/** Future delivery boundary. Never log recipient, content, verification or recovery secrets. */
public interface TransactionalEmailSender {
    void send(String recipient, String subject, String text);
}
