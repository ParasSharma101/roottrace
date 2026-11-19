package com.roottrace.security;

import com.roottrace.security.dto.AuthResponse;
import com.roottrace.security.dto.LoginRequest;
import com.roottrace.security.dto.SignupRequest;
import com.roottrace.security.dto.UserProfileDto;
import com.roottrace.security.dto.SelectWorkspaceRequest;
import com.roottrace.security.dto.WorkspaceMembershipDto;
import com.roottrace.security.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        AuthResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public UserProfileDto getCurrentUser(Authentication authentication) {
        return authService.getProfile(WorkspaceIdentity.require(authentication));
    }

    @GetMapping("/workspaces")
    public List<WorkspaceMembershipDto> listWorkspaces(Authentication authentication) {
        return authService.listWorkspaces(WorkspaceIdentity.require(authentication).userId());
    }

    @PostMapping("/select-workspace")
    public AuthResponse selectWorkspace(@Valid @RequestBody SelectWorkspaceRequest request, Authentication authentication) {
        return authService.selectWorkspace(WorkspaceIdentity.require(authentication), request.workspaceId());
    }
}
