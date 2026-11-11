package com.fitcore.gym.security.browser;

import com.fitcore.gym.controller.AccountSecurityController.*;
import com.fitcore.gym.dto.auth.*;
import com.fitcore.gym.dto.gym.OwnerRegistrationRequest;
import com.fitcore.gym.service.AccountSecurityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** Tokenless, non-cookie-writing receipts; never calls a legacy JWT-producing endpoint. */
@RestController
@RequestMapping("/api/browser")
@ConditionalOnProperty(name="fitcore.browser.spike-enabled",havingValue="true")
public class BrowserAccountController {
    private final BrowserSessionService sessions;
    private final AccountSecurityService accounts;
    @Value("${fitcore.security-flows.demo-enabled:false}") private boolean enabled;
    @Value("${fitcore.email.outbox.enabled:false}") private boolean queued;
    public BrowserAccountController(BrowserSessionService sessions,AccountSecurityService accounts){this.sessions=sessions;this.accounts=accounts;}
    private void receipt(HttpServletRequest request,Runnable action){
        if(!enabled || !queued)throw new BrowserSessionService.Failure(403,"SECURITY_ONBOARDING_DISABLED");
        sessions.accountReceipt((BrowserSessionService.Context)request.getAttribute(BrowserSessionService.CONTEXT),action);
    }
    @PostMapping("/registration/owner") public ResponseEntity<Void> owner(@Valid @RequestBody OwnerRegistrationRequest input,HttpServletRequest request){receipt(request,()->accounts.pendingOwner(input));return ResponseEntity.accepted().build();}
    @PostMapping("/registration/invitation") public ResponseEntity<Void> invite(@Valid @RequestBody RegisterRequest input,HttpServletRequest request){receipt(request,()->accounts.pendingInvite(input));return ResponseEntity.accepted().build();}
    @PostMapping("/verification/request") public ResponseEntity<Void> verification(@Valid @RequestBody ForgotPasswordRequest input,HttpServletRequest request){receipt(request,()->accounts.request(input.getEmail(),false));return ResponseEntity.accepted().build();}
    @PostMapping("/forgot-password") public ResponseEntity<Void> forgot(@Valid @RequestBody ForgotPasswordRequest input,HttpServletRequest request){receipt(request,()->accounts.request(input.getEmail(),true));return ResponseEntity.accepted().build();}
    @PostMapping("/verify-email") public ResponseEntity<Void> verify(@Valid @RequestBody VerifyRequest input,HttpServletRequest request){receipt(request,()->accounts.verifyWithoutSession(input.email(),input.token()));return ResponseEntity.noContent().build();}
    @PostMapping("/reset-password") public ResponseEntity<Void> reset(@Valid @RequestBody ResetRequest input,HttpServletRequest request){receipt(request,()->accounts.reset(input.email(),input.token(),input.password()));return ResponseEntity.noContent().build();}
    @ExceptionHandler(BrowserSessionService.Failure.class) public ResponseEntity<Map<String,String>> failure(BrowserSessionService.Failure e){return ResponseEntity.status(e.status).body(Map.of("message",e.getMessage()));}
}
