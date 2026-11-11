package com.fitcore.gym.tenancy;

import com.fitcore.gym.GymBackendApplication;
import com.fitcore.gym.email.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import tools.jackson.databind.json.JsonMapper;

/** Test-classpath-only launcher. No real provider, public mailbox API or production scheduler. */
public final class BrowserSyntheticMailApplication {
    public static void main(String[] args)throws Exception{
        // Same real container marker/URL checks as all disposable fixtures, never a spoofed marker.
        var fixture=new FoundationMigrationTest();fixture.schema="public";
        try(var ignored=fixture.connection()){}
        new SpringApplicationBuilder(GymBackendApplication.class,Config.class).run(args);
    }
    @TestConfiguration
    static class Config {
        @Bean @Primary EmailProvider syntheticMail(org.springframework.core.env.Environment env)throws Exception{
            Path dir=Path.of(env.getRequiredProperty("fitcore.fixture.mailbox")).toRealPath();
            if(!Files.isDirectory(dir) || !Files.getPosixFilePermissions(dir).equals(PosixFilePermissions.fromString("rwx------")))throw new IllegalStateException("Owned private synthetic mailbox required");
            return (id,message)->{
                if(message.recipient()==null || !message.recipient().matches("[A-Za-z0-9._+\\-]+@fixture\\.invalid"))throw new EmailProvider.Rejected();
                try(var channel=FileChannel.open(dir.resolve(id+".lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var ignored=channel.lock()){
                    Files.setPosixFilePermissions(dir.resolve(id+".lock"),PosixFilePermissions.fromString("rw-------"));
                    Path target=dir.resolve(id+".json");
                    if(!Files.exists(target)){
                        Path temp=Files.createTempFile(dir,"intent-",".tmp",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
                        try{Files.writeString(temp,JsonMapper.builder().build().writeValueAsString(message));Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE);}
                        finally{Files.deleteIfExists(temp);}
                    }
                    return new EmailProvider.Acceptance("synthetic-accepted-"+id);
                }catch(java.io.IOException unavailable){throw new IllegalStateException("Synthetic mailbox unavailable");}
            };
        }
        @Bean(destroyMethod="shutdownNow") ScheduledExecutorService syntheticOutboxWorker(EmailOutbox outbox){
            var executor=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"synthetic-outbox-only");t.setDaemon(true);return t;});
            executor.scheduleWithFixedDelay(()->{try{for(int n=0;n<10&&outbox.processOne();n++){}outbox.cleanup();}catch(RuntimeException unavailable){/* No message/token/exception payloads in logs. Missing receipt fails harness. */}},200,100,TimeUnit.MILLISECONDS);
            return executor;
        }
    }
}
