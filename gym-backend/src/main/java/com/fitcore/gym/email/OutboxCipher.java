package com.fitcore.gym.email;
public interface OutboxCipher {
 record Message(String recipient,String subject,String text,String html){@Override public String toString(){return "Message[redacted]";}}
 record Sealed(String keyId,byte[] bytes){@Override public String toString(){return "Sealed[redacted]";}}
 Sealed seal(String binding,Message message);
 Message open(String keyId,byte[] bytes,String binding);
}
