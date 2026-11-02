package com.fitcore.gym.controller;
import com.fitcore.gym.service.AccountSecurityService;
import com.fitcore.gym.dto.gym.OwnerRegistrationRequest;
import com.fitcore.gym.dto.auth.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/auth")
public class AccountSecurityController {
    private final AccountSecurityService service;
    public AccountSecurityController(AccountSecurityService service){this.service=service;}
    public record VerifyRequest(@NotBlank @Email @Size(max=254) String email,@NotBlank @Size(max=128) String token){}
    public record ResetRequest(@NotBlank @Email @Size(max=254) String email,@NotBlank @Size(max=128) String token,@NotBlank @Size(max=64) String password){}
    @PostMapping("/registration/owner") public ResponseEntity<Void> owner(@Valid @RequestBody OwnerRegistrationRequest input){service.pendingOwner(input);return ResponseEntity.accepted().build();}
    @PostMapping("/registration/invitation") public ResponseEntity<Void> invite(@Valid @RequestBody RegisterRequest input){service.pendingInvite(input);return ResponseEntity.accepted().build();}
    @PostMapping("/verification/request") public ResponseEntity<Void> request(@Valid @RequestBody ForgotPasswordRequest input){service.request(input.getEmail(),false);return ResponseEntity.accepted().build();}
    @PostMapping("/verify-email") public ResponseEntity<AuthResponse> verify(@Valid @RequestBody VerifyRequest input){return ResponseEntity.ok(service.verify(input.email(),input.token()));}
    @PostMapping("/reset-password") public ResponseEntity<Void> reset(@Valid @RequestBody ResetRequest input){service.reset(input.email(),input.token(),input.password());return ResponseEntity.noContent().build();}
}
