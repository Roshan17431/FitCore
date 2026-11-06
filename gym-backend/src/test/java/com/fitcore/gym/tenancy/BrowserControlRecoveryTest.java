package com.fitcore.gym.tenancy;

import static org.assertj.core.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Control/CSRF metadata is not authentication; only current idle contexts may sign out. */
class BrowserControlRecoveryTest extends BrowserSessionSecurityTest {
    @Test void metadataBindsTheExactCookieFamilyAndRevisionWithoutSecrets()throws Exception{
        Jar jar=bootstrap();var r=send(jar,"GET","/api/browser/csrf",null,false);
        assertThat(r.getStatus()).isEqualTo(200);var d=json.readTree(r.getContentAsString());
        assertThat(d.hasNonNull("familyId")).isTrue();assertThat(d.get("familyId").asText()).isEqualTo(jdbc.queryForObject("select family_id::text from browser_sessions where token_hash=?",String.class,hash(jar.values.get("__Host-fitcore_preauth"))));
        assertThat(d.get("contextRevision").asText()).isEqualTo("0");assertThat(d.get("familyRevision").asText()).isEqualTo("0");
        assertThat(d.get("contextKind").asText()).isEqualTo("PREAUTH");assertThat(d.get("currentContext").asBoolean()).isTrue();
        assertThat(d.get("reauthenticationRequired").asBoolean()).isFalse();assertThat(r.getHeaders("Set-Cookie")).isEmpty();
        assertThat(r.getContentAsString()).doesNotContain(jar.values.get("__Host-fitcore_preauth"),"token_hash","pending_hash","credentialsVersion","email");
    }
    @Test void expiredCurrentContextCanOnlySignOutThenAuthenticateExplicitly()throws Exception{
        Jar jar=authenticated(member),old=jar.copy();String token=hash(jar.values.get("__Host-fitcore_session"));
        f.sql("update browser_sessions set created_at=now()-interval '2 hours',expires_at=now()-interval '1 hour' where token_hash=?",token);
        var status=send(jar,"GET","/api/browser/csrf",null,false);assertThat(status.getStatus()).isEqualTo(200);
        var d=json.readTree(status.getContentAsString());assertThat(d.get("authenticated").asBoolean()).isFalse();assertThat(d.get("reauthenticationRequired").asBoolean()).isTrue();assertThat(d.get("currentContext").asBoolean()).isTrue();accept(jar,status);
        assertThat(send(jar,"GET","/api/auth/me",null,false).getStatus()).isEqualTo(401);
        var denied=login(jar,member);assertThat(denied.getStatus()).isEqualTo(401);assertThat(denied.getHeaders("Set-Cookie")).isEmpty();
        var logout=send(jar,"POST","/api/browser/logout",Map.of(),true);assertThat(logout.getStatus()).isEqualTo(200);accept(jar,logout);
        assertThat(send(jar,"POST","/api/browser/ack",Map.of(),true).getStatus()).isEqualTo(200);
        var success=login(jar,member);assertThat(success.getStatus()).isEqualTo(200);accept(jar,success);assertThat(send(jar,"POST","/api/browser/ack",Map.of(),true).getStatus()).isEqualTo(200);
        assertThat(send(jar,"GET","/api/auth/me",null,false).getStatus()).isEqualTo(200);assertThat(send(old,"GET","/api/auth/me",null,false).getStatus()).isEqualTo(401);
    }
    @Test void expiredCurrentPreauthRequiresExplicitSignOutBeforeFreshCredentials()throws Exception{
        Jar jar=bootstrap();String token=hash(jar.values.get("__Host-fitcore_preauth"));
        f.sql("update browser_sessions set created_at=now()-interval '2 hours',expires_at=now()-interval '1 hour' where token_hash=?",token);
        var status=send(jar,"GET","/api/browser/csrf",null,false);assertThat(status.getStatus()).isEqualTo(200);accept(jar,status);
        var d=json.readTree(status.getContentAsString());assertThat(d.get("contextKind").asText()).isEqualTo("PREAUTH");assertThat(d.get("authenticated").asBoolean()).isFalse();assertThat(d.get("reauthenticationRequired").asBoolean()).isTrue();
        assertThat(login(jar,member).getStatus()).isEqualTo(401);
        var out=send(jar,"POST","/api/browser/logout",Map.of(),true);assertThat(out.getStatus()).isEqualTo(200);accept(jar,out);
        assertThat(send(jar,"POST","/api/browser/ack",Map.of(),true).getStatus()).isEqualTo(200);assertThat(login(jar,member).getStatus()).isEqualTo(200);
    }
    @Test void revokedSourceAuthRequiresLogoutBeforeAnyNewAccountLogin()throws Exception{
        Jar jar=authenticated(member);f.sql("update users set authz_version=authz_version+1 where id=?",member.id());
        var denied=login(jar,b);assertThat(denied.getStatus()).isEqualTo(401);assertThat(denied.getHeaders("Set-Cookie")).isEmpty();
        var status=send(jar,"GET","/api/browser/csrf",null,false);assertThat(status.getStatus()).isEqualTo(200);var d=json.readTree(status.getContentAsString());
        assertThat(d.get("authenticated").asBoolean()).isFalse();assertThat(d.get("reauthenticationRequired").asBoolean()).isTrue();accept(jar,status);
        assertThat(send(jar,"GET","/api/admin/users",null,false).getStatus()).isEqualTo(401);
        var out=send(jar,"POST","/api/browser/logout",Map.of(),true);assertThat(out.getStatus()).isEqualTo(200);accept(jar,out);assertThat(send(jar,"POST","/api/browser/ack",Map.of(),true).getStatus()).isEqualTo(200);
        var next=login(jar,b);assertThat(next.getStatus()).isEqualTo(200);accept(jar,next);assertThat(send(jar,"POST","/api/browser/ack",Map.of(),true).getStatus()).isEqualTo(200);
        assertThat(send(jar,"GET","/api/auth/me",null,false).getContentAsString()).contains(b.id().toString()).doesNotContain(member.id().toString());
    }
    @Test void pendingMetadataDistinguishesOldCookieFromActualDeliveredCandidate()throws Exception{
        Jar old=bootstrap();var pending=login(old,a);assertThat(pending.getStatus()).isEqualTo(200);Jar candidate=old.copy();accept(candidate,pending);
        var before=json.readTree(send(old,"GET","/api/browser/csrf",null,false).getContentAsString());
        var after=json.readTree(send(candidate,"GET","/api/browser/csrf",null,false).getContentAsString());
        assertThat(before.hasNonNull("familyId")).isTrue();assertThat(after.hasNonNull("familyId")).isTrue();assertThat(before.get("familyId").asText()).isEqualTo(after.get("familyId").asText());
        assertThat(before.get("contextRevision").asText()).isEqualTo("0");assertThat(before.get("familyRevision").asText()).isEqualTo("1");assertThat(before.get("currentContext").asBoolean()).isFalse();
        assertThat(after.get("contextRevision").asText()).isEqualTo("1");assertThat(after.get("familyRevision").asText()).isEqualTo("1");assertThat(after.get("currentContext").asBoolean()).isTrue();assertThat(after.get("pendingKind").asText()).isEqualTo("LOGIN");
        assertThat(send(old,"POST","/api/browser/ack",Map.of(),true).getStatus()).isEqualTo(409);
        assertThat(send(candidate,"POST","/api/browser/ack",Map.of(),true).getStatus()).isEqualTo(200);
    }
    @Test void absoluteExpiredFamilyAndIdleRetiredCookieCannotAcquireControlAuthority()throws Exception{
        Jar jar=authenticated(member);f.sql("update browser_session_families set created_at=now()-interval '13 hours',expires_at=now()-interval '1 hour' where id=(select family_id from browser_sessions where token_hash=?)",hash(jar.values.get("__Host-fitcore_session")));
        assertThat(send(jar,"GET","/api/browser/csrf",null,false).getStatus()).isEqualTo(401);
        var out=send(jar,"POST","/api/browser/logout",Map.of(),true);assertThat(out.getStatus()).isEqualTo(401);assertThat(out.getHeaders("Set-Cookie")).isEmpty();
        Jar other=authenticated(a),retired=other.copy();var next=login(other,b);accept(other,next);assertThat(send(other,"POST","/api/browser/ack",Map.of(),true).getStatus()).isEqualTo(200);
        assertThat(send(retired,"GET","/api/browser/csrf",null,false).getStatus()).isEqualTo(401);var stale=send(retired,"POST","/api/browser/logout",Map.of(),true);assertThat(stale.getStatus()).isEqualTo(401);assertThat(stale.getHeaders("Set-Cookie")).isEmpty();
    }
    @Test void writerMetadataIsCompleteAndStatusNeverIncludesCsrfOrUser()throws Exception{
        Jar jar=bootstrap();var login=login(jar,member);accept(jar,login);
        var d=json.readTree(login.getContentAsString());assertThat(d.get("contextKind").asText()).isEqualTo("AUTH");assertThat(d.get("pendingKind").asText()).isEqualTo("LOGIN");assertThat(d.get("authenticated").asBoolean()).isFalse();assertThat(d.has("user")).isFalse();
        assertThat(send(jar,"POST","/api/browser/ack",Map.of(),true).getStatus()).isEqualTo(200);
        var state=send(jar,"GET","/api/browser/state",null,false);assertThat(state.getStatus()).isEqualTo(200);assertThat(state.getContentAsString()).doesNotContain("csrfToken","email","token_hash");
        var out=send(jar,"POST","/api/browser/logout",Map.of(),true);d=json.readTree(out.getContentAsString());assertThat(d.get("contextKind").asText()).isEqualTo("PREAUTH");assertThat(d.get("pendingKind").asText()).isEqualTo("LOGOUT");assertThat(d.get("contextRevision").asText()).isEqualTo("2");assertThat(d.get("familyRevision").asText()).isEqualTo("2");
    }
    @Test void pendingCandidateRevocationCannotBeAcknowledgedOrAuthorizeBusiness()throws Exception{
        Jar jar=bootstrap();accept(jar,login(jar,member));f.sql("update users set authz_version=authz_version+1 where id=?",member.id());
        var ack=send(jar,"POST","/api/browser/ack",Map.of(),true);assertThat(ack.getStatus()).isEqualTo(401);assertThat(ack.getHeaders("Set-Cookie")).isEmpty();
        assertThat(send(jar,"GET","/api/auth/me",null,false).getStatus()).isEqualTo(423);
        assertThat(json.readTree(send(jar,"GET","/api/browser/csrf",null,false).getContentAsString()).get("reauthenticationRequired").asBoolean()).isTrue();
    }
    @Test void rowAbsoluteExpiryWithLiveFamilyNeverAcquiresControl()throws Exception{
        Jar jar=authenticated(member);f.sql("update browser_sessions set created_at=now()-interval '2 hours',expires_at=now()-interval '1 hour',absolute_expires_at=now()-interval '30 minutes' where token_hash=?",hash(jar.values.get("__Host-fitcore_session")));
        assertThat(send(jar,"GET","/api/browser/csrf",null,false).getStatus()).isEqualTo(401);var out=send(jar,"POST","/api/browser/logout",Map.of(),true);assertThat(out.getStatus()).isEqualTo(401);assertThat(out.getHeaders("Set-Cookie")).isEmpty();
    }
    @Test void disabledCurrentSessionCanRevokeOnlyItsOwnHandleAndStillCannotLogin()throws Exception{
        Jar jar=authenticated(member);f.sql("update users set enabled=false where id=?",member.id());
        var status=send(jar,"GET","/api/browser/csrf",null,false);assertThat(status.getStatus()).isEqualTo(200);accept(jar,status);
        assertThat(json.readTree(status.getContentAsString()).get("authenticated").asBoolean()).isFalse();assertThat(send(jar,"GET","/api/auth/me",null,false).getStatus()).isEqualTo(401);
        var out=send(jar,"POST","/api/browser/logout",Map.of(),true);assertThat(out.getStatus()).isEqualTo(200);accept(jar,out);assertThat(send(jar,"POST","/api/browser/ack",Map.of(),true).getStatus()).isEqualTo(200);
        assertThat(login(jar,member).getStatus()).isEqualTo(401);
    }
}
