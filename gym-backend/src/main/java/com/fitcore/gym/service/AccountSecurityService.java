package com.fitcore.gym.service;

import com.fitcore.gym.dto.auth.*;
import com.fitcore.gym.dto.gym.OwnerRegistrationRequest;
import com.fitcore.gym.dto.user.SessionUserDto;
import com.fitcore.gym.email.TransactionalEmailSender;
import com.fitcore.gym.entity.User;
import com.fitcore.gym.exception.*;
import com.fitcore.gym.repository.UserIdentityRepository;
import com.fitcore.gym.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

/** Auth-only global identity boundary. Never returns challenge secrets to HTTP clients. */
@Service
public class AccountSecurityService {
    private final JdbcTemplate jdbc; private final TransactionTemplate tx; private final PasswordEncoder encoder;
    private final TransactionalEmailSender mail; private final UserIdentityRepository identities;
    private final OnboardingService onboarding; private final JwtService jwt;
    @Value("${fitcore.security-flows.demo-enabled:false}") private boolean enabled;
    @Value("${fitcore.security.public-origin:http://127.0.0.1:5175}") private String origin;
    public AccountSecurityService(JdbcTemplate jdbc,PlatformTransactionManager transactions,PasswordEncoder encoder,
            TransactionalEmailSender mail,UserIdentityRepository identities,OnboardingService onboarding,JwtService jwt){
        this.jdbc=jdbc;this.tx=new TransactionTemplate(transactions);this.tx.setPropagationBehavior(3);
        this.encoder=encoder;this.mail=mail;this.identities=identities;this.onboarding=onboarding;this.jwt=jwt;
    }
    @jakarta.annotation.PostConstruct void validateOrigin(){
        var uri=java.net.URI.create(origin);
        if(uri.getHost()==null || uri.getRawUserInfo()!=null || uri.getRawQuery()!=null || uri.getRawFragment()!=null
                || !(uri.getRawPath()==null || uri.getRawPath().isEmpty())
                || !("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme()) && Set.of("127.0.0.1","localhost").contains(uri.getHost()))))
            throw new IllegalStateException("Security links require a fixed HTTPS or loopback origin");
    }
    void gate(){if(!enabled)throw new ForbiddenException("SECURITY_ONBOARDING_DISABLED");}
    public static String digest(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    static String secret(){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    static String email(String value){return value.trim();}
    public static void password(String value){if(value==null || value.length()<12 || value.length()>64 || value.getBytes(StandardCharsets.UTF_8).length>72
        || !value.matches("(?s)(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^a-zA-Z0-9]).*"))throw new BadRequestException("Use 12–64 characters with uppercase, lowercase, number and symbol; maximum72 UTF-8 bytes");}
    record Pending(String purpose,String email,UUID user,UUID gym,Long version,String name,String hash,String gymName,String slug,String goal,String invite){}
    private void issue(Pending p){
        String token=secret();
        try {tx.executeWithoutResult(status->{
            lockEmail(p.email());
            // Limit delivery independently of the peer; apply to known/unknown addresses alike at callers.
            if(jdbc.queryForObject("select count(*) from account_challenges where email=? and created_at>current_timestamp-interval '5 minutes'",Long.class,p.email())>=5)return;
            jdbc.update("delete from account_challenges where id in (select id from account_challenges where expires_at<current_timestamp limit 100)");
            var now=jdbc.queryForObject("select localtimestamp",LocalDateTime.class);
            jdbc.update("insert into account_challenges(id,token_hash,purpose,email,user_id,gym_id,authz_version,full_name,password_hash,gym_name,slug,fitness_goal,invitation_hash,expires_at,created_at) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(),digest(token),p.purpose(),p.email(),p.user(),p.gym(),p.version(),p.name(),p.hash(),p.gymName(),p.slug(),p.goal(),p.invite(),now.plusMinutes(p.purpose().equals("RESET")?15:1440),now);
            try {mail.send(p.email(),p.purpose().equals("RESET")?"Reset your FitCore password":"Verify your FitCore email",origin+(p.purpose().equals("RESET")?"/reset-password":"/verify-email")+"#token="+token);}
            catch(RuntimeException failure){throw new DeliveryUnavailable();}
        });}catch(DeliveryUnavailable failure){/* Generic202; transaction rolled back and no unusable persisted plaintext. */}
    }
    static class DeliveryUnavailable extends RuntimeException {}
    private void lockEmail(String email){jdbc.execute("set local lock_timeout='3s'");jdbc.execute("set local statement_timeout='5s'");jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))",email);}
    public void pendingOwner(OwnerRegistrationRequest r){gate();password(r.password());String address=email(r.email());
        String hash=encoder.encode(r.password());if(identities.findByEmail(address).isPresent())return;
        issue(new Pending("OWNER",address,null,null,null,r.fullName().trim(),hash,r.gymName().trim(),r.slug(),null,null));}
    public void pendingInvite(RegisterRequest r){gate();password(r.getPassword());String address=email(r.getEmail());
        if(r.getInvitationToken()==null || !r.getInvitationToken().matches("[A-Za-z0-9_-]{43}"))return;
        String hash=encoder.encode(r.getPassword());
        var found=jdbc.queryForList("select i.gym_id from gym_invitations i join gyms g on g.id=i.gym_id where token_hash=? and email=? and accepted_at is null and revoked_at is null and expires_at>current_timestamp and g.status='ACTIVE'",digest(r.getInvitationToken()),address);
        if(found.size()!=1 || identities.findByEmail(address).isPresent())return;
        issue(new Pending("INVITE",address,null,(UUID)found.get(0).get("gym_id"),null,r.getFullName(),hash,null,null,r.getFitnessGoal(),digest(r.getInvitationToken())));}
    public void request(String address,boolean reset){if(!enabled)return;address=email(address);
        var account=identities.findByEmail(address);
        if(account.isEmpty() || !account.get().isEnabled() || (!reset && account.get().getEmailVerifiedAt()!=null))return;
        User user=account.get();
        if(user.getGymId()==null || jdbc.queryForObject("select count(*) from gyms where id=? and status='ACTIVE'",Long.class,user.getGymId())!=1)return;
        issue(new Pending(reset?"RESET":"VERIFY",address,user.getId(),user.getGymId(),user.getAuthzVersion(),null,null,null,null,null,null));}
    public AuthResponse verify(String address,String token){gate();return consume(address,token,null);}
    public void reset(String address,String token,String newPassword){gate();password(newPassword);consume(address,token,newPassword);}
    private AuthResponse consume(String address,String token,String newPassword){
        if(token==null || !token.matches("[A-Za-z0-9_-]{43}"))throw invalid();
        String normalized=email(address);
        var created=tx.execute(status->{
            lockEmail(normalized);
            var rows=jdbc.queryForList("select * from account_challenges where token_hash=? and email=? for update",digest(token),normalized);
            if(rows.size()!=1)throw invalid();var c=rows.get(0);String purpose=(String)c.get("purpose");
            if(c.get("consumed_at")!=null || !((java.sql.Timestamp)c.get("expires_at")).toLocalDateTime().isAfter(jdbc.queryForObject("select localtimestamp",LocalDateTime.class))
                || (newPassword==null)==purpose.equals("RESET"))throw invalid();
            OnboardingService.Created result=null;
            if(purpose.equals("OWNER"))result=onboarding.verifiedOwner((String)c.get("full_name"),normalized,(String)c.get("password_hash"),(String)c.get("gym_name"),(String)c.get("slug"));
            else if(purpose.equals("INVITE"))result=onboarding.verifiedInvite((String)c.get("full_name"),normalized,(String)c.get("password_hash"),(String)c.get("invitation_hash"),(String)c.get("fitness_goal"));
            else {
                var user=identities.findById((UUID)c.get("user_id")).orElseThrow(this::invalid);
                if(!user.isEnabled() || !user.getEmail().equals(normalized) || !user.getGymId().equals(c.get("gym_id")) || user.getAuthzVersion()!=((Number)c.get("authz_version")).longValue()
                    || jdbc.queryForObject("select count(*) from gyms where id=? and status='ACTIVE'",Long.class,user.getGymId())!=1)throw invalid();
                int changed;
                if(purpose.equals("RESET"))changed=jdbc.update("update users set password_hash=?,authz_version=authz_version+1,updated_at=current_timestamp where id=? and gym_id=? and authz_version=?",encoder.encode(newPassword),user.getId(),user.getGymId(),user.getAuthzVersion());
                else if(purpose.equals("VERIFY"))changed=jdbc.update("update users set email_verified_at=current_timestamp where id=? and gym_id=? and authz_version=? and email_verified_at is null",user.getId(),user.getGymId(),user.getAuthzVersion());
                else throw invalid();
                if(changed!=1)throw invalid();
            }
            jdbc.update("update account_challenges set consumed_at=current_timestamp,password_hash=null where email=? and consumed_at is null",normalized);
            return result;
        });
        // Sign only after the entire challenge/account transaction has committed.
        return created==null?null:AuthResponse.builder().token(jwt.generateToken(created.user())).user(SessionUserDto.from(created.user(),created.gym())).build();
    }
    private BadRequestException invalid(){return new BadRequestException("Invalid or unavailable security token");}
}
