package com.roottrace.security;

import com.roottrace.security.dto.AuthResponse;
import com.roottrace.security.dto.DemoLoginRequest;
import com.roottrace.security.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Profile("dev & !prod")
@ConditionalOnProperty(name = "roottrace.demo.enabled", havingValue = "true")
public class DemoAuthController {

    private final AuthService authService;

    public DemoAuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/demo-login")
    public AuthResponse demoLogin(@Valid @RequestBody DemoLoginRequest request) {
        return authService.demoLogin(request);
    }
}
