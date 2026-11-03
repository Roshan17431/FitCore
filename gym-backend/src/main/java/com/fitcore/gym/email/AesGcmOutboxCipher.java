package com.fitcore.gym.email;
import java.util.*;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import javax.crypto.*;
import javax.crypto.spec.*;
import tools.jackson.databind.json.JsonMapper;
public final class AesGcmOutboxCipher implements OutboxCipher {
 private final Map<String,javax.crypto.SecretKey> keys;private final String active;
 private final JsonMapper json=JsonMapper.builder().build();
 public AesGcmOutboxCipher(Map<String,byte[]> keys,String active){
  var copy=new HashMap<String,javax.crypto.SecretKey>();
  keys.forEach((id,value)->{if(!id.matches("[A-Za-z0-9_-]{1,32}")||value.length!=32)throw new IllegalStateException("Invalid outbox key configuration");copy.put(id,new SecretKeySpec(value.clone(),"AES"));});
  if(!copy.containsKey(active))throw new IllegalStateException("Active outbox key is required");
  this.keys=Map.copyOf(copy);this.active=active;
 }
 public Sealed seal(String binding,Message message){
  try {byte[] iv=new byte[12];new SecureRandom().nextBytes(iv);var cipher=Cipher.getInstance("AES/GCM/NoPadding");
   cipher.init(Cipher.ENCRYPT_MODE,keys.get(active),new GCMParameterSpec(128,iv));cipher.updateAAD(binding.getBytes(StandardCharsets.UTF_8));
   byte[] body=cipher.doFinal(json.writeValueAsBytes(message));byte[] result=new byte[iv.length+body.length];System.arraycopy(iv,0,result,0,12);System.arraycopy(body,0,result,12,body.length);return new Sealed(active,result);
  }catch(Exception failure){throw new IllegalStateException("Outbox encryption unavailable");}
 }
 public Message open(String id,byte[] bytes,String binding){
  try {if(bytes==null||bytes.length<29||!keys.containsKey(id))throw new IllegalArgumentException();
   var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,keys.get(id),new GCMParameterSpec(128,Arrays.copyOf(bytes,12)));cipher.updateAAD(binding.getBytes(StandardCharsets.UTF_8));
   return json.readValue(cipher.doFinal(Arrays.copyOfRange(bytes,12,bytes.length)),Message.class);
  }catch(Exception failure){throw new IllegalStateException("Outbox payload unavailable");}
 }
}
