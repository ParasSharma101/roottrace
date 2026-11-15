package com.roottrace.security;

import com.roottrace.security.dto.AuthResponse;
import com.roottrace.security.dto.DemoLoginRequest;
import com.roottrace.security.dto.LoginRequest;
import com.roottrace.security.dto.UserProfileDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final JwtTokenProvider tokenProvider;

    public AuthController(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        String email = request.email().toLowerCase().trim();
        String name = email.split("@")[0];

        List<String> roles;
        if (email.contains("admin")) {
            roles = List.of(UserRole.ROLE_ADMIN.name(), UserRole.ROLE_SRE.name(), UserRole.ROLE_VIEWER.name());
        } else if (email.contains("viewer")) {
            roles = List.of(UserRole.ROLE_VIEWER.name());
        } else {
            roles = List.of(UserRole.ROLE_SRE.name(), UserRole.ROLE_VIEWER.name());
        }

        String token = tokenProvider.generateToken(email, name, roles);
        return ResponseEntity.ok(new AuthResponse(token, email, name, roles));
    }

    @PostMapping("/demo-login")
    public ResponseEntity<AuthResponse> demoLogin(@Valid @RequestBody DemoLoginRequest request) {
        String roleParam = request.role().toUpperCase().trim();
        String email;
        String name;
        List<String> roles;

        switch (roleParam) {
            case "ADMIN" -> {
                email = "bob.admin@roottrace.io";
                name = "Bob (Platform Admin)";
                roles = List.of(UserRole.ROLE_ADMIN.name(), UserRole.ROLE_SRE.name(), UserRole.ROLE_VIEWER.name());
            }
            case "VIEWER" -> {
                email = "charlie.dev@roottrace.io";
                name = "Charlie (Observer / Dev)";
                roles = List.of(UserRole.ROLE_VIEWER.name());
            }
            default -> { // SRE
                email = "alice.sre@roottrace.io";
                name = "Alice (On-Call SRE)";
                roles = List.of(UserRole.ROLE_SRE.name(), UserRole.ROLE_VIEWER.name());
            }
        }

        String token = tokenProvider.generateToken(email, name, roles);
        return ResponseEntity.ok(new AuthResponse(token, email, name, roles));
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileDto> getCurrentUser(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return ResponseEntity.status(401).build();
        }

        String email = auth.getName();
        List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        boolean isBreakGlass = Boolean.TRUE.equals(request.getAttribute("isBreakGlass"))
                || "emergency-breakglass@roottrace.internal".equals(email);

        String name = isBreakGlass ? "Emergency Break-Glass Account" : email.split("@")[0];

        return ResponseEntity.ok(new UserProfileDto(email, name, roles, isBreakGlass));
    }
}
