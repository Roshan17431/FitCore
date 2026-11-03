package com.fitcore.gym.email;
import java.util.UUID;
/** Provider adapters must enforce request timeout and honor stable idempotency UUIDs. */
public interface EmailProvider {
 record Acceptance(String reference) {@Override public String toString(){return "Acceptance[redacted]";}}
 Acceptance accept(UUID idempotencyKey,OutboxCipher.Message message);
 class Rejected extends RuntimeException {public Rejected(){super("Provider permanently rejected request");}}
}
