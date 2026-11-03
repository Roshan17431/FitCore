package com.fitcore.gym.email;

/** Future delivery boundary. Never log recipient, content, verification or recovery secrets. */
public interface TransactionalEmailSender {
    default void enqueue(EmailIntent intent){send(intent.recipient(),intent.subject(),intent.text());}
    void send(String recipient, String subject, String text);
}
