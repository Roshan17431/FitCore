package com.fitcore.gym.tenancy;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Vetted signature verification plus shared nonce consumption, exact origin and byte binding. */
class BrowserGatewaySecurityTest extends BrowserAdmissionTest {
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void isolateSyntheticRates(){jdbc.update("delete from security_rate_limits");}
    String assertion(Map<String,Object> edits)throws Exception{return assertion(edits,Set.of());}
    String assertion(Map<String,Object> edits,Set<String> omitted)throws Exception{
        Instant now=Instant.now();var c=new HashMap<String,Object>();
        c.put("iss","fitcore-local-bff");c.put("aud","fitcore-browser-spike");c.put("iat",Date.from(now));c.put("exp",Date.from(now.plusSeconds(20)));c.put("jti",UUID.randomUUID().toString());
        c.put("method","POST");c.put("target","/api/browser/bootstrap");c.put("bodySha256",hash("{}"));c.put("cookieSha256",hash(""));c.put("clientIp","127.0.0.1");c.putAll(edits);
        omitted.forEach(c::remove);
        return Jwts.builder().setClaims(c).signWith(KEY.getPrivate(),SignatureAlgorithm.RS256).compact();
    }
    MockHttpServletRequestBuilder bootstrap(String proof){return post("/api/browser/bootstrap").secure(true).contentType("application/json").content("{}").header("Origin",ORIGIN).header("Sec-Fetch-Site","same-origin").header("X-FitCore-Bootstrap","1").header("X-FitCore-Gateway",proof);}
    @Test void alteredIssuerAudienceMethodTargetBodyCookieAndPeerAreRejected()throws Exception{
        for(var edit:List.of(Map.<String,Object>of("iss","attacker"),Map.<String,Object>of("aud","attacker"),Map.<String,Object>of("method","DELETE"),Map.<String,Object>of("target","/api/admin/users"),Map.<String,Object>of("bodySha256",hash("altered")),Map.<String,Object>of("cookieSha256",hash("stolen")),Map.<String,Object>of("clientIp","198.51.100.1"))){
            var r=mvc.perform(bootstrap(assertion(edit))).andReturn().getResponse();assertThat(r.getStatus()).as(edit.keySet().toString()).isEqualTo(401);assertThat(r.getHeaders("Set-Cookie")).isEmpty();
        }
    }
    @Test void missingMalformedWrongKeyWrongAlgorithmAndExpiredProofsFailClosed()throws Exception{
        var generator=java.security.KeyPairGenerator.getInstance("RSA");generator.initialize(2048);var other=generator.generateKeyPair();
        var now=Instant.now();
        for(String token:List.of("invalid",Jwts.builder().setSubject("other").signWith(other.getPrivate(),SignatureAlgorithm.RS256).compact(),Jwts.builder().setSubject("wrong-alg").signWith(Keys.hmacShaKeyFor(new byte[32])).compact(),assertion(Map.of("iat",Date.from(now.minusSeconds(120)),"exp",Date.from(now.minusSeconds(100)))),assertion(Map.of("iat",Date.from(now.plusSeconds(60)),"exp",Date.from(now.plusSeconds(80)))),assertion(Map.of("exp",Date.from(now.plusSeconds(120)))))){
            assertThat(mvc.perform(bootstrap(token)).andReturn().getResponse().getStatus()).isEqualTo(401);
        }
        assertThat(mvc.perform(post("/api/browser/bootstrap")).andReturn().getResponse().getStatus()).isEqualTo(401);
    }
    long rows(String table){return jdbc.queryForObject("select count(*) from "+table,Long.class);}
    void rejectedWithoutSession(MockHttpServletRequestBuilder request,int... statuses)throws Exception{
        long families=rows("browser_session_families"),sessions=rows("browser_sessions");
        var response=mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isIn(Arrays.stream(statuses).boxed().toList());
        assertThat(response.getHeaders("Set-Cookie")).isEmpty();
        assertThat(rows("browser_session_families")).isEqualTo(families);
        assertThat(rows("browser_sessions")).isEqualTo(sessions);
    }
    MockHttpServletRequestBuilder replaceHeader(MockHttpServletRequestBuilder request,String name,String value){
        return request.with(r->{if(name.equalsIgnoreCase("Content-Type"))r.setContentType(value);r.removeHeader(name);if(value!=null)r.addHeader(name,value);return r;});
    }
    Map<String,Object> databaseState(){
        return Map.of("families",jdbc.queryForList("select * from browser_session_families order by id"),
            "sessions",jdbc.queryForList("select * from browser_sessions order by token_hash"),
            "nonces",jdbc.queryForList("select * from browser_gateway_nonces order by nonce_hash"));
    }
    @ParameterizedTest
    @ValueSource(strings={"iss","aud","iat","exp","jti","method","target","bodySha256","cookieSha256","clientIp"})
    void eachMandatoryClaimMustBePresent(String claim)throws Exception{
        var before=databaseState();
        rejectedWithoutSession(bootstrap(assertion(Map.of(),Set.of(claim))),401);
        assertThat(databaseState()).isEqualTo(before);
    }
    @Test void nullWrongTypeAndNonloopbackClientIpAreControlledRejections()throws Exception{
        for(Object value:Arrays.asList(null,123,List.of("127.0.0.1"),"198.51.100.1","")){
            var edits=new HashMap<String,Object>();edits.put("clientIp",value);
            var before=databaseState();
            rejectedWithoutSession(bootstrap(assertion(edits)),401);
            assertThat(databaseState()).isEqualTo(before);
        }
    }
    @Test void singletonOriginFetchMetadataTlsAndBootstrapAdmissionRequired()throws Exception{
        for(String value:Arrays.asList(null,"https://attacker.invalid","http://127.0.0.1:18444","https://127.0.0.1:18445"))
            rejectedWithoutSession(replaceHeader(bootstrap(assertion(Map.of())),"Origin",value),403);
        for(String value:Arrays.asList(null,"cross-site","same-site","none"))
            rejectedWithoutSession(replaceHeader(bootstrap(assertion(Map.of())),"Sec-Fetch-Site",value),403);
        for(String value:Arrays.asList(null,"0"))
            rejectedWithoutSession(replaceHeader(bootstrap(assertion(Map.of())),"X-FitCore-Bootstrap",value),403);
        rejectedWithoutSession(bootstrap(assertion(Map.of())).secure(false),401);
    }
    @Test void duplicateSecurityHeadersAreRejectedSeparately()throws Exception{
        for(String name:List.of("Origin","Sec-Fetch-Site","X-FitCore-Gateway","Cookie","Content-Type","X-CSRF-TOKEN","X-FitCore-Bootstrap")){
            var request=bootstrap(assertion(Map.of())).with(r->{r.addHeader(name,"duplicate-a");r.addHeader(name,"duplicate-b");return r;});
            rejectedWithoutSession(request,403);
        }
    }
    @ParameterizedTest
    @ValueSource(strings={"application/json","application/json;charset=UTF-8"})
    void exactJsonMediaTypeWithSupportedCharsetIsAccepted(String type)throws Exception{
        var response=mvc.perform(bootstrap(assertion(Map.of())).contentType(type)).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
        assertThat(response.getHeaders("Set-Cookie")).hasSize(1);
    }
    @ParameterizedTest
    @ValueSource(strings={"application/json-evil","application/jsonp","text/plain","application/json; charset=unknown-fitcore-charset","application/json; charset=\"unterminated","not-a-media-type"})
    void nonJsonOrMalformedBootstrapMediaTypeIsRejected(String type)throws Exception{
        rejectedWithoutSession(bootstrap(assertion(Map.of())).contentType(type).with(r->{r.setCharacterEncoding("UTF-8");return r;}),400,403);
    }
    @Test void absentBootstrapMediaTypeIsRejected()throws Exception{
        rejectedWithoutSession(replaceHeader(bootstrap(assertion(Map.of())),"Content-Type",null),400,403);
    }
    @Test void replayIsRejectedEvenAfterTheHandlerTransactionFails()throws Exception{
        String token=assertion(Map.of("target","/api/browser/logout"));
        var request=post("/api/browser/logout").secure(true).contentType("application/json").content("{}").header("Origin",ORIGIN).header("Sec-Fetch-Site","same-origin").header("X-FitCore-Gateway",token);
        assertThat(mvc.perform(request).andReturn().getResponse().getStatus()).isEqualTo(403); // Missing framework CSRF.
        assertThat(mvc.perform(request).andReturn().getResponse().getStatus()).isEqualTo(401); // Nonce stayed consumed.
    }
    @Test void simultaneousIdenticalProofCommitsOnlyOneBootstrap()throws Exception{
        String proof=assertion(Map.of());var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            List<Callable<Integer>> jobs=List.of(()->{start.await();return mvc.perform(bootstrap(proof)).andReturn().getResponse().getStatus();},()->{start.await();return mvc.perform(bootstrap(proof)).andReturn().getResponse().getStatus();});
            var futures=new ArrayList<Future<Integer>>();for(var job:jobs)futures.add(pool.submit(job));start.countDown();
            assertThat(List.of(futures.get(0).get(15,TimeUnit.SECONDS),futures.get(1).get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,401);
        }finally{pool.shutdownNow();}
    }
    @Test void forwardedAuthorizationAndTenantSelectorHeadersNeverSupplyAuthority()throws Exception{
        for(String header:List.of("Authorization","Forwarded","X-Forwarded-For","X-Gym-ID")){
            assertThat(mvc.perform(bootstrap(assertion(Map.of())).header(header,"spoofed")).andReturn().getResponse().getStatus()).isEqualTo(400);
        }
    }
    @Test void bodyIsBoundedBeforeAnySessionCreation()throws Exception{
        var r=mvc.perform(bootstrap(assertion(Map.of())).content("x".repeat(1048577))).andReturn().getResponse();assertThat(r.getStatus()).isEqualTo(413);assertThat(r.getHeaders("Set-Cookie")).isEmpty();
    }
}
