package com.fitcore.gym.tenancy;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import jakarta.servlet.http.Cookie;
import io.jsonwebtoken.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import tools.jackson.databind.json.JsonMapper;

/** Real Spring CSRF and restricted PostgreSQL HTTP/session contracts, no mock authorization. */
class BrowserSessionSecurityTest extends BrowserAdmissionTest {
    @Autowired JdbcTemplate jdbc;
    final JsonMapper json=JsonMapper.builder().build();
    final FoundationMigrationTest f=new FoundationMigrationTest();
    static final String PASSWORD="Browser-Session!2026";
    record Account(UUID id,UUID gym,String email,String role){}
    Account a,b,member;
    static final class Jar {
        final SortedMap<String,String> values=new TreeMap<>();String csrf;
        String raw(){return values.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).collect(java.util.stream.Collectors.joining("; "));}
        Jar copy(){Jar c=new Jar();c.values.putAll(values);c.csrf=csrf;return c;}
    }
    @BeforeEach void accounts()throws Exception{
        f.schema=SCHEMA;
        jdbc.update("delete from security_rate_limits");
        String hash=new BCryptPasswordEncoder().encode(PASSWORD);
        try(var c=f.connection()){
            c.setAutoCommit(false);
            Account[] owners=new Account[2];
            for(int n=0;n<2;n++){
                UUID gym=UUID.randomUUID(),id=UUID.randomUUID();String email=id+"@fixture.invalid";
                FoundationMigrationTest.execute(c,"insert into gyms(id,name,slug,status,created_at,updated_at) values(?,'Synthetic browser gym',?,'ACTIVE',now(),now())",gym,gym.toString());
                FoundationMigrationTest.execute(c,"insert into users(id,full_name,email,password_hash,role,enabled,email_verified_at,gym_id,created_at,updated_at) values(?,'Browser owner',?,?,'OWNER',true,now(),?,now(),now())",id,email,hash,gym);
                FoundationMigrationTest.execute(c,"update gyms set primary_owner_user_id=? where id=?",id,gym);
                owners[n]=new Account(id,gym,email,"OWNER");
            }
            a=owners[0];b=owners[1];UUID id=UUID.randomUUID();member=new Account(id,a.gym(),id+"@fixture.invalid","MEMBER");
            FoundationMigrationTest.execute(c,"insert into users(id,full_name,email,password_hash,role,enabled,email_verified_at,gym_id,created_at,updated_at) values(?,'Browser member',?,?,'MEMBER',true,now(),?,now(),now())",id,member.email(),hash,a.gym());c.commit();
        }
    }
    String signed(String method,String target,String body,String cookies,String nonce)throws Exception{
        var now=Instant.now();return Jwts.builder().setIssuer("fitcore-local-bff").setAudience("fitcore-browser-spike").setIssuedAt(Date.from(now)).setExpiration(Date.from(now.plusSeconds(20))).setId(nonce).claim("method",method).claim("target",target).claim("bodySha256",hash(body)).claim("cookieSha256",hash(cookies)).claim("clientIp","127.0.0.1").signWith(KEY.getPrivate(),SignatureAlgorithm.RS256).compact();
    }
    MockHttpServletResponse send(Jar jar,String method,String target,Object body,boolean csrf)throws Exception{
        String text=body==null?"":json.writeValueAsString(body),cookies=jar.raw();
        var r=request(HttpMethod.valueOf(method),target).secure(true).header("Origin",ORIGIN).header("Sec-Fetch-Site","same-origin").header("X-FitCore-Gateway",signed(method,target,text,cookies,UUID.randomUUID().toString()));
        if(!cookies.isEmpty())r.header("Cookie",cookies).cookie(jar.values.entrySet().stream().map(e->new Cookie(e.getKey(),e.getValue())).toArray(Cookie[]::new));
        if(body!=null)r.contentType("application/json").content(text);
        if(csrf && jar.csrf!=null)r.header("X-CSRF-TOKEN",jar.csrf);
        if(target.equals("/api/browser/bootstrap"))r.header("X-FitCore-Bootstrap","1");
        return mvc.perform(r).andReturn().getResponse();
    }
    void accept(Jar jar,MockHttpServletResponse r)throws Exception{
        for(String cookie:r.getHeaders("Set-Cookie")){
            String[] pair=cookie.split(";",2)[0].split("=",2);if(pair[1].isEmpty())jar.values.remove(pair[0]);else jar.values.put(pair[0],pair[1]);
        }
        if(!r.getContentAsString().isEmpty()){
            var data=json.readTree(r.getContentAsString());if(data.has("csrfToken"))jar.csrf=data.get("csrfToken").asText();
        }
    }
    Jar bootstrap()throws Exception{Jar jar=new Jar();var r=send(jar,"POST","/api/browser/bootstrap",Map.of(),false);assertThat(r.getStatus()).isEqualTo(200);accept(jar,r);return jar;}
    MockHttpServletResponse login(Jar jar,Account who)throws Exception{return send(jar,"POST","/api/browser/login",Map.of("email",who.email(),"password",PASSWORD),true);}
    Jar authenticated(Account who)throws Exception{Jar jar=bootstrap();var r=login(jar,who);assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);accept(jar,r);assertThat(send(jar,"POST","/api/browser/ack",Map.of(),true).getStatus()).isEqualTo(200);return jar;}
    @Test void loginRotatesDigestOnlyHandleAndAckIsRequiredForBusiness()throws Exception{
        Jar jar=bootstrap(),old=jar.copy();var response=login(jar,a);assertThat(response.getStatus()).isEqualTo(200);accept(jar,response);
        String handle=jar.values.get("__Host-fitcore_session");assertThat(handle).hasSize(43);
        assertThat(jdbc.queryForObject("select count(*) from browser_sessions where token_hash=?",Integer.class,hash(handle))).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from browser_sessions where token_hash=?",Integer.class,handle)).isZero();
        assertThat(response.getContentAsString()).doesNotContain(handle,"token\":","refreshToken","credentialsVersion");
        assertThat(send(jar,"GET","/api/auth/me",null,false).getStatus()).isEqualTo(423);
        assertThat(send(old,"POST","/api/browser/ack",Map.of(),true).getStatus()).isEqualTo(409);
        var ack=send(jar,"POST","/api/browser/ack",Map.of(),true);assertThat(ack.getStatus()).isEqualTo(200);assertThat(ack.getHeaders("Set-Cookie")).isEmpty();
        assertThat(send(jar,"GET","/api/auth/me",null,false).getContentAsString()).contains(a.id().toString());
        assertThat(send(jar,"POST","/api/browser/ack",Map.of(),true).getStatus()).isEqualTo(409);
    }
    @Test void loginAndLogoutRequireSynchronizerCsrf()throws Exception{
        Jar jar=bootstrap();assertThat(send(jar,"POST","/api/browser/login",Map.of("email",a.email(),"password",PASSWORD),false).getStatus()).isEqualTo(403);
        jar=authenticated(a);assertThat(send(jar,"POST","/api/browser/logout",Map.of(),false).getStatus()).isEqualTo(403);
        assertThat(send(jar,"GET","/api/browser/logout",null,false).getStatus()).isEqualTo(405);
    }
    @Test void logoutNeedsFreshPreauthCookieAndAbsenceOfAuthCookie()throws Exception{
        Jar jar=authenticated(a),old=jar.copy();var out=send(jar,"POST","/api/browser/logout",Map.of(),true);assertThat(out.getStatus()).isEqualTo(200);assertThat(out.getHeaders("Set-Cookie")).hasSize(2);accept(jar,out);
        assertThat(jar.values).doesNotContainKey("__Host-fitcore_session");
        Jar ambiguous=jar.copy();ambiguous.values.put("__Host-fitcore_session",old.values.get("__Host-fitcore_session"));
        assertThat(send(ambiguous,"POST","/api/browser/ack",Map.of(),true).getStatus()).isIn(403,409);
        assertThat(send(jar,"POST","/api/browser/ack",Map.of(),true).getStatus()).isEqualTo(200);
        assertThat(send(jar,"GET","/api/auth/me",null,false).getStatus()).isEqualTo(401);
        assertThat(send(old,"GET","/api/auth/me",null,false).getStatus()).isEqualTo(401);
    }
    @Test void pendingFamilyCannotBeTimeUnlockedOrUsedForAnotherTransition()throws Exception{
        Jar old=bootstrap();assertThat(login(old,a).getStatus()).isEqualTo(200);
        for(String path:List.of("/api/browser/login","/api/browser/logout")){
            Object body=path.endsWith("login")?Map.of("email",b.email(),"password",PASSWORD):Map.of();
            var r=send(old,"POST",path,body,true);assertThat(r.getStatus()).isEqualTo(423);assertThat(r.getHeaders("Set-Cookie")).isEmpty();
        }
        assertThat(send(old,"GET","/api/browser/state",null,false).getContentAsString()).contains("PENDING");
        assertThat(jdbc.queryForObject("select count(*) from browser_session_families where pending_hash is not null",Integer.class)).isPositive();
    }
    @Test void concurrentLoginCommitsExactlyOneTransition()throws Exception{
        Jar jar=bootstrap();var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            var jobs=List.<Callable<Integer>>of(()->{start.await();return login(jar,a).getStatus();},()->{start.await();return login(jar,b).getStatus();});
            var futures=new ArrayList<Future<Integer>>();for(var job:jobs)futures.add(pool.submit(job));start.countDown();
            assertThat(List.of(futures.get(0).get(15,TimeUnit.SECONDS),futures.get(1).get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,423);
        }finally{pool.shutdownNow();}
    }
    @Test void passwordChangeAuthzChangeDisableAndExpiryRevokeOldSessions()throws Exception{
        for(String mutation:List.of("password_hash='changed-synthetic'","authz_version=authz_version+1","enabled=false")){
            Jar jar=authenticated(member);f.sql("update users set "+mutation+" where id=?",member.id());
            var r=send(jar,"GET","/api/auth/me",null,false);assertThat(r.getStatus()).isEqualTo(401);assertThat(r.getHeaders("Set-Cookie")).isEmpty();
            f.sql("update users set password_hash=?,enabled=true where id=?",new BCryptPasswordEncoder().encode(PASSWORD),member.id());
        }
        Jar expired=authenticated(member);f.sql("update browser_sessions set created_at=now()-interval '2 hours',expires_at=now()-interval '1 hour' where token_hash=?",hash(expired.values.get("__Host-fitcore_session")));
        assertThat(send(expired,"GET","/api/auth/me",null,false).getStatus()).isEqualTo(401);
    }
    @Test void assignedUnverifiedAccountNeverReceivesAuthenticationCookie()throws Exception{
        f.sql("update users set email_verified_at=null where id=?",member.id());
        var r=login(bootstrap(),member);assertThat(r.getStatus()).isEqualTo(401);assertThat(r.getHeaders("Set-Cookie")).isEmpty();
    }
    MockHttpServletResponse rawCookieRequest(Jar jar,String raw,String target,Object body,boolean parsed)throws Exception{
        String text=json.writeValueAsString(body);
        var request=post(target).secure(true).contentType("application/json").content(text)
            .header("Origin",ORIGIN).header("Sec-Fetch-Site","same-origin")
            .header("Cookie",raw).header("X-FitCore-Gateway",signed("POST",target,text,raw,UUID.randomUUID().toString()));
        if(jar.csrf!=null)request.header("X-CSRF-TOKEN",jar.csrf);
        if(parsed)request.cookie(jar.values.entrySet().stream().map(e->new Cookie(e.getKey(),e.getValue())).toArray(Cookie[]::new));
        if(target.endsWith("bootstrap"))request.header("X-FitCore-Bootstrap","1");
        // MockMvc's cookie builder rewrites Cookie; install the exact signed raw value last.
        request.with(r->{r.removeHeader("Cookie");r.addHeader("Cookie",raw);return r;});
        return mvc.perform(request).andReturn().getResponse();
    }
    long sessionRows(){return jdbc.queryForObject("select count(*) from browser_sessions",Long.class);}
    long familyRows(){return jdbc.queryForObject("select count(*) from browser_session_families",Long.class);}
    void rejectedCookieRequest(Jar jar,String raw,String target,Object body,boolean parsed)throws Exception{
        long sessions=sessionRows(),families=familyRows();
        var response=rawCookieRequest(jar,raw,target,body,parsed);
        assertThat(response.getStatus()).as(target+": "+response.getContentAsString()).isEqualTo(400);
        assertThat(response.getHeaders("Set-Cookie")).isEmpty();
        assertThat(sessionRows()).isEqualTo(sessions);assertThat(familyRows()).isEqualTo(families);
    }
    @Test void rawMalformedOrDuplicateBrowserCookiesCannotBootstrapNewFamily()throws Exception{
        for(String name:List.of("__Host-fitcore_session","__Host-fitcore_preauth")){
            for(String value:List.of("bad","","\""+"a".repeat(43)+"\""))
                rejectedCookieRequest(new Jar(),name+"="+value,"/api/browser/bootstrap",Map.of(),false);
            rejectedCookieRequest(new Jar(),name,"/api/browser/bootstrap",Map.of(),false);
            rejectedCookieRequest(new Jar(),name+"="+"a".repeat(43)+"; "+name+"="+"b".repeat(43),"/api/browser/bootstrap",Map.of(),false);
        }
    }
    @Test void malformedAuthCannotFallBackToValidPreauthForLogin()throws Exception{
        Jar jar=bootstrap();
        for(boolean parsed:List.of(true,false))for(String value:List.of("bad",""))
            rejectedCookieRequest(jar,jar.raw()+"; __Host-fitcore_session="+value,"/api/browser/login",Map.of("email",a.email(),"password",PASSWORD),parsed);
        String pre=jar.values.get("__Host-fitcore_preauth");
        rejectedCookieRequest(jar,jar.raw()+"; __Host-fitcore_preauth="+pre,"/api/browser/login",Map.of("email",a.email(),"password",PASSWORD),false);
        assertThat(login(jar,a).getStatus()).isEqualTo(200);
    }
    @Test void malformedAuthCannotAcknowledgePendingLogout()throws Exception{
        Jar jar=authenticated(a);var logout=send(jar,"POST","/api/browser/logout",Map.of(),true);
        assertThat(logout.getStatus()).isEqualTo(200);accept(jar,logout);
        String prehash=hash(jar.values.get("__Host-fitcore_preauth"));
        for(boolean parsed:List.of(true,false))for(String value:List.of("bad","")){
            rejectedCookieRequest(jar,jar.raw()+"; __Host-fitcore_session="+value,"/api/browser/ack",Map.of(),parsed);
            assertThat(jdbc.queryForObject("select pending_hash from browser_session_families where id=(select family_id from browser_sessions where token_hash=?)",String.class,prehash)).isEqualTo(prehash);
        }
        assertThat(send(jar,"POST","/api/browser/ack",Map.of(),true).getStatus()).isEqualTo(200);
    }
    @Test void rawOnlyValidPreauthCanLoginButCannotBootstrapAgain()throws Exception{
        Jar jar=bootstrap();long families=familyRows();
        var existing=rawCookieRequest(jar,jar.raw(),"/api/browser/bootstrap",Map.of(),false);
        assertThat(existing.getStatus()).isEqualTo(409);assertThat(existing.getHeaders("Set-Cookie")).isEmpty();
        assertThat(familyRows()).isEqualTo(families);
        assertThat(rawCookieRequest(jar,jar.raw(),"/api/browser/login",Map.of("email",a.email(),"password",PASSWORD),false).getStatus()).isEqualTo(200);
    }
    @Test void roleDowngradeRevokesExistingCookieAndNewLoginUsesCurrentRole()throws Exception{
        f.sql("update users set role='TRAINER' where id=?",member.id());Jar previous=authenticated(member);
        f.sql("update users set role='MEMBER' where id=?",member.id());
        var revoked=send(previous,"GET","/api/auth/me",null,false);
        assertThat(revoked.getStatus()).isEqualTo(401);assertThat(revoked.getHeaders("Set-Cookie")).isEmpty();
        Jar current=authenticated(member);
        var me=send(current,"GET","/api/auth/me",null,false);
        assertThat(me.getStatus()).isEqualTo(200);assertThat(me.getContentAsString()).contains("MEMBER").doesNotContain("TRAINER");
    }
    @Test void gymSuspensionAndVerificationRemovalRevokeExistingCookies()throws Exception{
        Jar suspended=authenticated(member);f.sql("update gyms set status='SUSPENDED' where id=?",a.gym());
        var denied=send(suspended,"GET","/api/auth/me",null,false);
        assertThat(denied.getStatus()).isEqualTo(403);assertThat(denied.getHeaders("Set-Cookie")).isEmpty();
        f.sql("update gyms set status='ACTIVE' where id=?",a.gym());
        Jar unverified=authenticated(member);f.sql("update users set email_verified_at=null where id=?",member.id());
        var revoked=send(unverified,"GET","/api/auth/me",null,false);
        assertThat(revoked.getStatus()).isEqualTo(401);assertThat(revoked.getHeaders("Set-Cookie")).isEmpty();
    }
    @Test void absoluteFamilyExpiryRejectsOtherwiseUnexpiredSession()throws Exception{
        Jar jar=authenticated(member);
        f.sql("update browser_session_families set created_at=now()-interval '13 hours',expires_at=now()-interval '1 hour' where id=(select family_id from browser_sessions where token_hash=?)",hash(jar.values.get("__Host-fitcore_session")));
        for(String target:List.of("/api/auth/me","/api/browser/csrf","/api/browser/state")){
            var response=send(jar,"GET",target,null,false);
            assertThat(response.getStatus()).isEqualTo(401);assertThat(response.getHeaders("Set-Cookie")).isEmpty();
        }
    }
    @Test void twoGymOwnersCanOnlyListAndManageTheirOwnRecords()throws Exception{
        for(Account local:List.of(a,b)){
            Account foreign=local==a?b:a;Jar jar=authenticated(local);
            assertThat(send(jar,"GET","/api/users/"+foreign.id(),null,false).getStatus()).isEqualTo(404);
            var list=send(jar,"GET","/api/admin/users",null,false);assertThat(list.getStatus()).isEqualTo(200);assertThat(list.getContentAsString()).contains(local.email()).doesNotContain(foreign.email());
            var write=send(jar,"PUT","/api/admin/users/"+foreign.id(),Map.of("fullName","Changed","email",foreign.email(),"role","MEMBER"),true);assertThat(write.getStatus()).isEqualTo(404);
        }
        Jar jar=authenticated(member);assertThat(send(jar,"GET","/api/admin/users",null,false).getStatus()).isEqualTo(403);
    }
    @Test void ordinaryReadsAndDeniedOnboardingCannotWriteCookies()throws Exception{
        Jar jar=authenticated(a);
        for(String path:List.of("/api/auth/me","/api/browser/csrf","/api/browser/state","/api/admin/dashboard"))assertThat(send(jar,"GET",path,null,false).getHeaders("Set-Cookie")).isEmpty();
        for(String path:List.of("/api/auth/login","/api/auth/register-owner","/api/auth/verify-email","/api/auth/reset-password")){
            var r=send(jar,"POST",path,Map.of(),true);assertThat(r.getStatus()).isEqualTo(403);assertThat(r.getHeaders("Set-Cookie")).isEmpty();
        }
    }
}
