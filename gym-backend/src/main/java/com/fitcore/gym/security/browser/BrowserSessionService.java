package com.fitcore.gym.security.browser;

import com.fitcore.gym.domain.GymStatus;
import com.fitcore.gym.entity.User;
import com.fitcore.gym.repository.GymRepository;
import com.fitcore.gym.security.CustomUserDetailsService;
import com.fitcore.gym.security.JwtService;
import jakarta.servlet.http.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/** Digest-backed shared sessions. Delivery uncertainty is persistent; never time-unlock a family. */
@Service
@ConditionalOnProperty(name="fitcore.browser.spike-enabled",havingValue="true")
public class BrowserSessionService {
    public static final String AUTH="__Host-fitcore_session", PREAUTH="__Host-fitcore_preauth";
    public static final String CONTEXT=BrowserSessionService.class.getName()+".context";
    private final JdbcTemplate jdbc;
    private final AuthenticationManager authentication;
    private final CustomUserDetailsService identities;
    private final GymRepository gyms;
    private final JwtService versions;
    private final SecureRandom random=new SecureRandom();
    public BrowserSessionService(JdbcTemplate jdbc,AuthenticationManager authentication,CustomUserDetailsService identities,GymRepository gyms,JwtService versions){
        this.jdbc=jdbc;this.authentication=authentication;this.identities=identities;this.gyms=gyms;this.versions=versions;
    }
    public record Context(String hash,UUID family,String kind,long revision,UUID user,UUID gym,Long authz,String credentials,String csrf,boolean used,Instant expires,Instant absolute,long currentRevision,String pendingHash,String pendingKind,Instant familyExpires) {
        public boolean pending(){return pendingHash!=null;}
        public boolean current(){return !used && revision==currentRevision && expires.isAfter(Instant.now()) && absolute.isAfter(Instant.now()) && familyExpires.isAfter(Instant.now());}
    }
    public static final class Failure extends RuntimeException {
        public final int status;
        public Failure(int status,String code){super(code);this.status=status;}
    }
    public static String digest(String value){return digest(value.getBytes(StandardCharsets.UTF_8));}
    public static String digest(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    private String secret(){byte[] b=new byte[32];random.nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
    public static String cookie(HttpServletRequest request,String name){
        // The gateway signs this exact header. Servlet containers may omit malformed
        // cookies from getCookies(), so parsed cookies cannot establish absence.
        String raw=request.getHeader("Cookie");
        if(raw==null)return null;
        String found=null;int count=0;
        for(String part:raw.split(";",-1)){
            String pair=part.strip();int equals=pair.indexOf('=');
            String candidate=equals<0?pair:pair.substring(0,equals).strip();
            if(!name.equals(candidate))continue;
            if(++count>1)throw new Failure(400,"AMBIGUOUS_COOKIE");
            if(equals<0 || !pair.substring(0,equals).equals(name))throw new Failure(400,"MALFORMED_COOKIE");
            found=pair.substring(equals+1);
            if(!found.matches("[A-Za-z0-9_-]{43}"))throw new Failure(400,"MALFORMED_COOKIE");
        }
        return found;
    }
    private static void setCookie(HttpServletResponse response,String name,String value,boolean expire){
        var b=ResponseCookie.from(name,value).secure(true).httpOnly(true).sameSite("Lax").path("/");
        if(expire)b.maxAge(Duration.ZERO);
        response.addHeader("Set-Cookie",b.build().toString());
    }
    public Context resolve(HttpServletRequest request){
        String auth=cookie(request,AUTH),pre=cookie(request,PREAUTH);
        Context context=auth==null?null:find(digest(auth));
        // A presented retired authentication handle cannot fall back to an older pre-auth context.
        if(auth!=null)return context;
        return pre==null?null:find(digest(pre));
    }
    private Context find(String hash){
        var rows=jdbc.query("""
            select s.*,f.revision as current_revision,f.pending_hash,f.pending_kind,f.expires_at as family_expires
            from browser_sessions s join browser_session_families f on f.id=s.family_id where s.token_hash=?
            """,(r,n)->new Context(r.getString("token_hash"),r.getObject("family_id",UUID.class),r.getString("kind"),r.getLong("family_revision"),r.getObject("user_id",UUID.class),r.getObject("gym_id",UUID.class),(Long)r.getObject("authz_version"),r.getString("credentials_version"),r.getString("csrf_token"),r.getBoolean("used"),r.getTimestamp("expires_at").toInstant(),r.getTimestamp("absolute_expires_at").toInstant(),r.getLong("current_revision"),r.getString("pending_hash"),r.getString("pending_kind"),r.getTimestamp("family_expires").toInstant()),hash);
        return rows.isEmpty()?null:rows.get(0);
    }
    private Context lock(Context before){
        if(before==null)throw new Failure(401,"BROWSER_CONTEXT_REQUIRED");
        jdbc.queryForList("select id from browser_session_families where id=? for update",before.family());
        Context c=find(before.hash());if(c==null)throw new Failure(401,"BROWSER_CONTEXT_REQUIRED");return c;
    }
    private void requireIdle(Context c){if(c.pending())throw new Failure(423,"COOKIE_DELIVERY_PENDING");if(!c.current())throw new Failure(401,"SESSION_RETIRED");}
    public User account(Context c){
        if(c==null || !"AUTH".equals(c.kind()) || !c.current())throw new Failure(401,"AUTHENTICATION_REQUIRED");
        try{
            User u=identities.loadById(c.user());
            if(!Objects.equals(u.getGymId(),c.gym()) || u.getAuthzVersion()!=c.authz() || u.getEmailVerifiedAt()==null || !MessageDigest.isEqual(versions.credentialsVersion(u).getBytes(StandardCharsets.UTF_8),c.credentials().getBytes(StandardCharsets.UTF_8)))throw new Failure(401,"SESSION_REVOKED");
            if(gyms.findById(u.getGymId()).map(g->g.getStatus()!=GymStatus.ACTIVE).orElse(true))throw new Failure(403,"GYM_UNAVAILABLE");
            return u;
        }catch(AuthenticationException e){throw new Failure(401,"SESSION_REVOKED");}
    }
    private boolean liveControl(Context c){
        return c!=null && !c.used() && c.revision()==c.currentRevision()
            && c.familyExpires().isAfter(Instant.now()) && c.absolute().isAfter(Instant.now());
    }
    private Map<String,Object> state(Context c,boolean authenticated,boolean reauthenticationRequired){
        var result=new LinkedHashMap<String,Object>();
        result.put("csrfToken",c.csrf());result.put("authenticated",authenticated);
        result.put("transitionState",c.pending()?"PENDING":"IDLE");
        // Correlation is deliberately non-authorizing. Never expose handle or digest.
        result.put("familyId",c.family().toString());
        result.put("contextRevision",Long.toString(c.revision()));
        result.put("familyRevision",Long.toString(c.currentRevision()));
        result.put("contextKind",c.kind());
        result.put("currentContext",!c.used() && c.revision()==c.currentRevision());
        result.put("pendingKind",c.pendingKind());
        result.put("reauthenticationRequired",reauthenticationRequired);
        return result;
    }
    public Map<String,Object> csrf(Context c){
        if(c==null || !c.familyExpires().isAfter(Instant.now()) || !c.absolute().isAfter(Instant.now())
            || (!c.current() && !c.pending() && !liveControl(c)))throw new Failure(401,"BROWSER_CONTEXT_REQUIRED");
        boolean reauthenticationRequired=!c.current(),authenticated=false;
        if("AUTH".equals(c.kind()) && c.current()){
            try { account(c);authenticated=!c.pending(); }
            catch(Failure invalid){
                // Current-context control proof can revoke its own handle, never authenticate.
                if(invalid.status!=401 && invalid.status!=403)throw invalid;
                reauthenticationRequired=true;
            }
        }
        return state(c,authenticated,reauthenticationRequired);
    }
    @Transactional
    public Map<String,Object> bootstrap(HttpServletRequest request,HttpServletResponse response){
        if(cookie(request,AUTH)!=null || cookie(request,PREAUTH)!=null)throw new Failure(409,"BROWSER_CONTEXT_ALREADY_EXISTS");
        Instant now=Instant.now(),end=now.plus(Duration.ofHours(12));UUID id=UUID.randomUUID();
        jdbc.update("insert into browser_session_families(id,created_at,expires_at) values(?,?,?)",id,Timestamp.from(now),Timestamp.from(end));
        String handle=insert(id,"PREAUTH",0,null,now,end);
        setCookie(response,PREAUTH,handle,false);return csrf(find(digest(handle)));
    }
    private String insert(UUID family,String kind,long revision,User user,Instant now,Instant absolute){
        String handle=secret(),csrf=secret();
        Instant expiry=now.plus(Duration.ofMinutes(30));if(expiry.isAfter(absolute))expiry=absolute;
        jdbc.update("""
            insert into browser_sessions(token_hash,family_id,kind,family_revision,user_id,gym_id,authz_version,credentials_version,csrf_token,created_at,expires_at,absolute_expires_at)
            values(?,?,?,?,?,?,?,?,?,?,?,?)
            """,digest(handle),family,kind,revision,user==null?null:user.getId(),user==null?null:user.getGymId(),user==null?null:user.getAuthzVersion(),user==null?null:versions.credentialsVersion(user),csrf,Timestamp.from(now),Timestamp.from(expiry),Timestamp.from(absolute));return handle;
    }
    @Transactional
    public Map<String,Object> login(Context before,String email,String password,HttpServletResponse response){
        Context c=lock(before);requireIdle(c);
        if("AUTH".equals(c.kind()))account(c); // A revoked source may only sign out.
        if(email==null || password==null || email.length()>254 || password.length()>256)throw new Failure(401,"INVALID_CREDENTIALS");
        User u;
        try{
            var result=authentication.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email,password));
            u=(User)result.getPrincipal();u=identities.loadById(u.getId());
        }catch(AuthenticationException e){throw new Failure(401,"INVALID_CREDENTIALS");}
        if(u.getGymId()==null || u.getEmailVerifiedAt()==null)throw new Failure(401,"INVALID_CREDENTIALS");
        var gym=gyms.findById(u.getGymId()).filter(g->g.getStatus()==GymStatus.ACTIVE).orElseThrow(()->new Failure(403,"GYM_UNAVAILABLE"));
        long revision=c.currentRevision()+1;String handle=insert(c.family(),"AUTH",revision,u,Instant.now(),c.familyExpires());
        retireAndFence(c,revision,digest(handle),"LOGIN");
        setCookie(response,AUTH,handle,false);
        return csrf(find(digest(handle)));
    }
    private void retireAndFence(Context c,long revision,String hash,String kind){
        jdbc.update("update browser_sessions set used=true where family_id=? and token_hash<>?",c.family(),hash);
        jdbc.update("update browser_session_families set revision=?,pending_hash=?,pending_kind=? where id=?",revision,hash,kind,c.family());
    }
    @Transactional
    public Map<String,Object> logout(Context before,HttpServletResponse response){
        Context c=lock(before);
        if(c.pending())throw new Failure(423,"COOKIE_DELIVERY_PENDING");
        if(!liveControl(c))throw new Failure(401,"SESSION_RETIRED");
        long revision=c.currentRevision()+1;String handle=insert(c.family(),"PREAUTH",revision,null,Instant.now(),c.familyExpires());
        retireAndFence(c,revision,digest(handle),"LOGOUT");
        setCookie(response,AUTH,"",true);setCookie(response,PREAUTH,handle,false);
        return csrf(find(digest(handle)));
    }
    @Transactional
    public Map<String,Object> ack(Context before,HttpServletRequest request){
        Context c=lock(before);
        if(!c.current() || !c.pending() || !c.hash().equals(c.pendingHash()) || ("LOGIN".equals(c.pendingKind()) && !"AUTH".equals(c.kind())) || ("LOGOUT".equals(c.pendingKind()) && (!"PREAUTH".equals(c.kind()) || cookie(request,AUTH)!=null)))throw new Failure(409,"COOKIE_DELIVERY_UNPROVEN");
        if("AUTH".equals(c.kind()))account(c);
        jdbc.update("update browser_session_families set pending_hash=null,pending_kind=null where id=?",c.family());
        return Map.of("acknowledged",true);
    }
}
