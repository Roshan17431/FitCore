package com.fitcore.gym.config;
import com.fitcore.gym.email.*;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
@Configuration
public class OutboxConfiguration {
 @Bean OutboxCipher outboxCipher(Environment env){
  if(env.getProperty("fitcore.email.outbox.worker-enabled",Boolean.class,false) && !env.getProperty("fitcore.email.outbox.enabled",Boolean.class,false))throw new IllegalStateException("Outbox worker requires enabled encryption configuration");
  if(!env.getProperty("fitcore.email.outbox.enabled",Boolean.class,false))return new OutboxCipher(){public Sealed seal(String b,Message m){throw new IllegalStateException("Email outbox disabled");}public Message open(String k,byte[] v,String b){throw new IllegalStateException("Email outbox disabled");}};
  try{var keys=new HashMap<String,byte[]>();for(String pair:env.getProperty("fitcore.email.outbox.keys","").split(";")){String[] parts=pair.split("=",2);if(parts.length!=2||keys.put(parts[0],Base64.getDecoder().decode(parts[1]))!=null)throw new IllegalArgumentException();}return new AesGcmOutboxCipher(keys,env.getProperty("fitcore.email.outbox.active-key"));}
  catch(RuntimeException failure){throw new IllegalStateException("Valid external outbox key ring is required");}
 }
 @Bean EmailProvider emailProvider(){return (key,message)->{throw new IllegalStateException("External email delivery is disabled");};}
}
