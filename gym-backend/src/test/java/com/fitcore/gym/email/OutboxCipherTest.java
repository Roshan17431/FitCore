package com.fitcore.gym.email;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.*;
class OutboxCipherTest {
 @Test void envelopeIsConfidentialAuthenticatedAndRotatable(){
  byte[] old=new byte[32],next=new byte[32];Arrays.fill(next,(byte)7);
  var first=new AesGcmOutboxCipher(Map.of("old",old),"old");var message=new OutboxCipher.Message("private@fixture.invalid","Subject","secret-token","<p>secret-token</p>");
  var sealed=first.seal("bound-id",message);assertThat(new String(sealed.bytes(),java.nio.charset.StandardCharsets.UTF_8)).doesNotContain("secret-token","private@");
  assertThat(first.open(sealed.keyId(),sealed.bytes(),"bound-id")).isEqualTo(message);
  var rotated=new AesGcmOutboxCipher(Map.of("old",old,"new",next),"new");assertThat(rotated.open("old",sealed.bytes(),"bound-id")).isEqualTo(message);
  assertThat(rotated.seal("bound-id",message).keyId()).isEqualTo("new");
  assertThatThrownBy(()->first.open("old",sealed.bytes(),"wrong-binding")).hasMessage("Outbox payload unavailable");
  byte[] broken=sealed.bytes().clone();broken[15]^=1;assertThatThrownBy(()->first.open("old",broken,"bound-id")).hasMessage("Outbox payload unavailable");
  assertThatThrownBy(()->rotated.open("missing",sealed.bytes(),"bound-id")).hasMessage("Outbox payload unavailable");
  assertThat(sealed.toString()).doesNotContain("secret");assertThat(message.toString()).doesNotContain("secret-token");
 }
 @Test void nonceIsUniqueAndKeysAreMandatory(){byte[] key=new byte[32];var cipher=new AesGcmOutboxCipher(Map.of("key",key),"key");var m=new OutboxCipher.Message("a@b.invalid","s","t","h");assertThat(cipher.seal("id",m).bytes()).isNotEqualTo(cipher.seal("id",m).bytes());assertThatThrownBy(()->new AesGcmOutboxCipher(Map.of(),"missing")).isInstanceOf(IllegalStateException.class);}
}
