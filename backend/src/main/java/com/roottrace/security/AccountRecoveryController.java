package com.roottrace.security;

import com.roottrace.security.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AccountRecoveryController {
    private final PasswordRecoveryService recovery;
    private final RecoveryInbox inbox;
    private final RecoveryRequestLimiter limiter;
    private final SessionRevocationService sessions;
    public AccountRecoveryController(PasswordRecoveryService recovery,RecoveryRequestLimiter limiter,SessionRevocationService sessions,RecoveryInbox inbox) {
        this.recovery=recovery; this.limiter=limiter; this.sessions=sessions;
        this.inbox=inbox;
    }
    @PostMapping("/password/forgot")
    public ResponseEntity<Map<String,String>> forgot(@Valid @RequestBody Forgot request,HttpServletRequest http) {
        if(limiter.allow(http.getRemoteAddr())) inbox.enqueue(request.email());
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(Map.of("status","ACCEPTED"));
    }
    @PostMapping("/password/reset")
    public ResponseEntity<Map<String,String>> reset(@Valid @RequestBody Reset request,HttpServletRequest http) {
        if(!limiter.allow(http.getRemoteAddr())) return ResponseEntity.status(429).cacheControl(CacheControl.noStore()).body(Map.of("status","TRY_LATER"));
        recovery.reset(request.token(),request.password());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("status","RESET"));
    }
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication auth) {
        sessions.logout(WorkspaceIdentity.require(auth)); return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(Authentication auth) {
        sessions.logoutAll(WorkspaceIdentity.require(auth)); return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    public record Forgot(@NotBlank @Email @Size(max=255) String email) {}
    public record Reset(@NotBlank @Size(max=256) String token,@NotBlank @Size(max=72) String password) {
        @Override public String toString() { return "PasswordReset[redacted]"; }
    }
}
