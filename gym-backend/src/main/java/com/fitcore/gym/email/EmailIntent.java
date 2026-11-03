package com.fitcore.gym.email;
import java.time.LocalDateTime;
import java.util.UUID;
public record EmailIntent(UUID gymId,UUID challengeId,UUID invitationId,String kind,String recipient,String subject,String text,String html,LocalDateTime expiresAt) {
 @Override public String toString(){return "EmailIntent[redacted]";}
}
