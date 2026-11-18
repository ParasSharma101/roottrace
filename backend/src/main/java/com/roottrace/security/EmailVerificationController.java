package com.roottrace.security;

import com.roottrace.security.service.EmailVerificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth/email")
public class EmailVerificationController {
    private final EmailVerificationService service;
    public EmailVerificationController(EmailVerificationService service) { this.service=service; }
    @PostMapping("/verification")
    public ResponseEntity<Map<String,String>> request(Authentication authentication) {
        service.request(WorkspaceIdentity.require(authentication));
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(Map.of("status","ACCEPTED"));
    }
    @GetMapping("/status")
    public ResponseEntity<EmailVerificationService.Status> status(Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.status(WorkspaceIdentity.require(authentication)));
    }
    @PostMapping("/verify")
    public ResponseEntity<Map<String,String>> verify(Authentication authentication,@Valid @RequestBody VerifyRequest request) {
        service.verify(WorkspaceIdentity.require(authentication),request.token());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("status","VERIFIED"));
    }
    public record VerifyRequest(@NotBlank @Size(max=256) String token) { @Override public String toString() { return "EmailVerification[redacted]"; } }
}
