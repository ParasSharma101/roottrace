package com.roottrace.security.service;

import com.roottrace.domain.model.User;
import com.roottrace.domain.model.Workspace;
import com.roottrace.domain.model.WorkspaceMember;
import com.roottrace.domain.repository.UserRepository;
import com.roottrace.domain.repository.WorkspaceMemberRepository;
import com.roottrace.domain.repository.WorkspaceRepository;
import com.roottrace.security.AuthDataSeeder;
import com.roottrace.security.JwtTokenProvider;
import com.roottrace.security.UserRole;
import com.roottrace.security.dto.AuthResponse;
import com.roottrace.security.dto.DemoLoginRequest;
import com.roottrace.security.dto.LoginRequest;
import com.roottrace.security.dto.SignupRequest;
import com.roottrace.security.dto.UserProfileDto;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AuthService(
            UserRepository userRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider tokenProvider
    ) {
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        String email = request.email().toLowerCase().trim();
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("An account with this email already exists: " + email);
        }

        // Generate unique slug
        String baseSlug = request.workspaceName().toLowerCase().replaceAll("[^a-z0-9]", "-").replaceAll("-+", "-");
        if (baseSlug.isBlank()) baseSlug = "workspace";
        String slug = baseSlug;
        int counter = 1;
        while (workspaceRepository.existsBySlug(slug)) {
            slug = baseSlug + "-" + counter++;
        }

        String apiKey = "rt_live_" + UUID.randomUUID().toString().replace("-", "");

        Workspace workspace = new Workspace(request.workspaceName().trim(), slug, apiKey);
        workspace = workspaceRepository.save(workspace);

        User user = new User(email, passwordEncoder.encode(request.password()), request.fullName().trim());
        user = userRepository.save(user);

        // Creator is Admin of their workspace
        WorkspaceMember member = new WorkspaceMember(workspace, user, UserRole.ROLE_ADMIN.name());
        workspaceMemberRepository.save(member);

        List<String> roles = List.of(UserRole.ROLE_ADMIN.name(), UserRole.ROLE_SRE.name(), UserRole.ROLE_VIEWER.name());
        String token = tokenProvider.generateToken(user.getEmail(), user.getFullName(), roles);

        return new AuthResponse(
                token,
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                roles,
                workspace.getId(),
                workspace.getName(),
                workspace.getSlug(),
                workspace.getApiKey()
        );
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = request.email().toLowerCase().trim();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        List<WorkspaceMember> memberships = workspaceMemberRepository.findByUserId(user.getId());
        WorkspaceMember primaryMembership = !memberships.isEmpty() ? memberships.getFirst() : null;

        Workspace ws;
        List<String> roles;
        if (primaryMembership != null) {
            ws = primaryMembership.getWorkspace();
            String role = primaryMembership.getRole();
            if (UserRole.ROLE_ADMIN.name().equals(role)) {
                roles = List.of(UserRole.ROLE_ADMIN.name(), UserRole.ROLE_SRE.name(), UserRole.ROLE_VIEWER.name());
            } else if (UserRole.ROLE_SRE.name().equals(role)) {
                roles = List.of(UserRole.ROLE_SRE.name(), UserRole.ROLE_VIEWER.name());
            } else {
                roles = List.of(UserRole.ROLE_VIEWER.name());
            }
        } else {
            ws = workspaceRepository.findById(AuthDataSeeder.DEFAULT_WORKSPACE_ID)
                    .orElseGet(() -> workspaceRepository.findAll().stream().findFirst().orElse(null));
            roles = List.of(UserRole.ROLE_SRE.name(), UserRole.ROLE_VIEWER.name());
        }

        String token = tokenProvider.generateToken(user.getEmail(), user.getFullName(), roles);

        return new AuthResponse(
                token,
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                roles,
                ws != null ? ws.getId() : null,
                ws != null ? ws.getName() : "RootTrace Workspace",
                ws != null ? ws.getSlug() : "default",
                ws != null ? ws.getApiKey() : "roottrace_dev_secret_key"
        );
    }

    @Transactional(readOnly = true)
    public AuthResponse demoLogin(DemoLoginRequest request) {
        String roleParam = request.role().toUpperCase().trim();
        String email = switch (roleParam) {
            case "ADMIN" -> "bob.admin@roottrace.io";
            case "VIEWER" -> "charlie.dev@roottrace.io";
            default -> "alice.sre@roottrace.io";
        };

        User user = userRepository.findByEmail(email)
                .orElseGet(() -> {
                    // Fallback in test context if DB not seeded yet
                    User fallback = new User(email, passwordEncoder.encode("password123"), roleParam + " User");
                    return userRepository.save(fallback);
                });

        List<WorkspaceMember> memberships = workspaceMemberRepository.findByUserId(user.getId());
        Workspace ws = !memberships.isEmpty()
                ? memberships.getFirst().getWorkspace()
                : workspaceRepository.findById(AuthDataSeeder.DEFAULT_WORKSPACE_ID).orElse(null);

        List<String> roles = switch (roleParam) {
            case "ADMIN" -> List.of(UserRole.ROLE_ADMIN.name(), UserRole.ROLE_SRE.name(), UserRole.ROLE_VIEWER.name());
            case "VIEWER" -> List.of(UserRole.ROLE_VIEWER.name());
            default -> List.of(UserRole.ROLE_SRE.name(), UserRole.ROLE_VIEWER.name());
        };

        String token = tokenProvider.generateToken(user.getEmail(), user.getFullName(), roles);

        return new AuthResponse(
                token,
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                roles,
                ws != null ? ws.getId() : null,
                ws != null ? ws.getName() : "RootTrace Cloud Dev",
                ws != null ? ws.getSlug() : "roottrace-dev",
                ws != null ? ws.getApiKey() : "roottrace_dev_secret_key"
        );
    }

    @Transactional(readOnly = true)
    public UserProfileDto getProfile(String email, boolean isBreakGlass) {
        if (isBreakGlass || "emergency-breakglass@roottrace.internal".equals(email)) {
            Workspace ws = workspaceRepository.findById(AuthDataSeeder.DEFAULT_WORKSPACE_ID).orElse(null);
            return new UserProfileDto(
                    null,
                    "emergency-breakglass@roottrace.internal",
                    "Emergency Break-Glass Account",
                    List.of(UserRole.ROLE_ADMIN.name(), UserRole.ROLE_SRE.name()),
                    ws != null ? ws.getId() : null,
                    ws != null ? ws.getName() : "Emergency Mode",
                    ws != null ? ws.getSlug() : "emergency",
                    ws != null ? ws.getApiKey() : "",
                    true
            );
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));

        List<WorkspaceMember> memberships = workspaceMemberRepository.findByUserId(user.getId());
        Workspace ws = !memberships.isEmpty() ? memberships.getFirst().getWorkspace() : null;
        List<String> roles = !memberships.isEmpty()
                ? List.of(memberships.getFirst().getRole())
                : List.of(UserRole.ROLE_SRE.name());

        return new UserProfileDto(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                roles,
                ws != null ? ws.getId() : null,
                ws != null ? ws.getName() : "Default Workspace",
                ws != null ? ws.getSlug() : "default",
                ws != null ? ws.getApiKey() : "",
                false
        );
    }
}
