package com.fitcore.gym.security.browser;

import com.fitcore.gym.security.TenantActor;
import com.fitcore.gym.security.SharedAbuseLimiter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.*;

@Configuration
@ConditionalOnProperty(name="fitcore.browser.spike-enabled",havingValue="true")
public class BrowserSecurityConfiguration {
    @Bean SecurityFilterChain browserSecurity(HttpSecurity http,BrowserSecurityMaintenance maintenance,SharedAbuseLimiter limiter,BrowserSessionService sessions,
        @Value("${fitcore.browser.public-origin}") String origin,
        @Value("${fitcore.browser.gateway-public-key}") String publicKey) throws Exception {
        var csrfRepository=new CsrfTokenRepository(){
            public CsrfToken generateToken(HttpServletRequest r){return new DefaultCsrfToken("X-CSRF-TOKEN","_csrf",UUID.randomUUID().toString());}
            public void saveToken(CsrfToken token,HttpServletRequest r,HttpServletResponse s){/* Explicit transactional bootstrap only; no implicit GET/session/cookie writes. */}
            public CsrfToken loadToken(HttpServletRequest r){var c=(BrowserSessionService.Context)r.getAttribute(BrowserSessionService.CONTEXT);return c==null?null:new DefaultCsrfToken("X-CSRF-TOKEN","_csrf",c.csrf());}
        };
        http.cors(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable).logout(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(c->c.csrfTokenRepository(csrfRepository).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()).ignoringRequestMatchers(r->"POST".equals(r.getMethod()) && "/api/browser/bootstrap".equals(r.getRequestURI())))
            .authorizeHttpRequests(a->a.requestMatchers("/api/health","/error").permitAll()
                .requestMatchers("/api/browser/**").permitAll()
                .requestMatchers("/api/auth/login","/api/auth/register","/api/auth/register-owner","/api/auth/registration/**","/api/auth/verify-email","/api/auth/verification/**","/api/auth/reset-password","/api/auth/forgot-password").denyAll()
                .requestMatchers("/api/admin/**").hasAnyRole("OWNER","ADMIN").anyRequest().authenticated())
            .exceptionHandling(e->e.authenticationEntryPoint((r,s,x)->BrowserGatewayFilter.error(s,401,"AUTHENTICATION_REQUIRED")).accessDeniedHandler((r,s,x)->BrowserGatewayFilter.error(s,403,"ACCESS_DENIED")))
            .addFilterBefore(new SessionFilter(sessions),CsrfFilter.class)
            .addFilterBefore(new BrowserGatewayFilter(maintenance,limiter,origin,publicKey),SessionFilter.class);
        return http.build();
    }
    private static final class SessionFilter extends OncePerRequestFilter{
        private final BrowserSessionService sessions;SessionFilter(BrowserSessionService s){sessions=s;}
        @Override protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,FilterChain chain)throws ServletException,IOException{
            try{
                BrowserSessionService.Context c=sessions.resolve(r);r.setAttribute(BrowserSessionService.CONTEXT,c);
                boolean metadata=r.getRequestURI().startsWith("/api/browser/");
                if(c!=null && c.pending() && !metadata)throw new BrowserSessionService.Failure(423,"COOKIE_DELIVERY_PENDING");
                if(c!=null && "AUTH".equals(c.kind()) && !metadata){
                    var user=sessions.account(c);var actor=new TenantActor(user.getId(),user.getGymId(),user.getRole(),user.getAuthzVersion());
                    r.setAttribute("fitcore.tenantActor",actor);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,null,user.getAuthorities()));
                }
                chain.doFilter(r,s);
            }catch(BrowserSessionService.Failure e){BrowserGatewayFilter.error(s,e.status,e.getMessage());}
            finally{SecurityContextHolder.clearContext();}
        }
    }
}
