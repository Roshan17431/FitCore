package com.fitcore.gym.security.browser;
import jakarta.servlet.http.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/browser")
@ConditionalOnProperty(name="fitcore.browser.spike-enabled",havingValue="true")
public class BrowserAuthController {
    private final BrowserSessionService sessions;
    public BrowserAuthController(BrowserSessionService sessions){this.sessions=sessions;}
    private BrowserSessionService.Context context(HttpServletRequest r){return (BrowserSessionService.Context)r.getAttribute(BrowserSessionService.CONTEXT);}
    public record Login(String email,String password){}
    @PostMapping("/bootstrap") public Map<String,Object> bootstrap(HttpServletRequest r,HttpServletResponse s){return sessions.bootstrap(r,s);}
    @GetMapping("/csrf") public Map<String,Object> csrf(HttpServletRequest r){return sessions.csrf(context(r));}
    @GetMapping("/state") public Map<String,Object> state(HttpServletRequest r){var c=new LinkedHashMap<>(sessions.csrf(context(r)));c.remove("csrfToken");return c;}
    @PostMapping("/login") public Map<String,Object> login(@RequestBody Login body,HttpServletRequest r,HttpServletResponse s){return sessions.login(context(r),body.email(),body.password(),s);}
    @PostMapping("/logout") public Map<String,Object> logout(HttpServletRequest r,HttpServletResponse s){return sessions.logout(context(r),s);}
    @PostMapping("/ack") public Map<String,Object> ack(HttpServletRequest r){return sessions.ack(context(r),r);}
    @ExceptionHandler(BrowserSessionService.Failure.class) public ResponseEntity<Map<String,String>> failure(BrowserSessionService.Failure e){return ResponseEntity.status(e.status).body(Map.of("message",e.getMessage()));}
}
