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
import com.roottrace.security.WorkspaceIdentity;
import com.roottrace.security.dto.WorkspaceMembershipDto;
import com.roottrace.domain.exception.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import com.roottrace.security.dto.AuthResponse;
import com.roottrace.security.dto.DemoLoginRequest;
import com.roottrace.security.dto.LoginRequest;
import com.roottrace.security.dto.SignupRequest;
import com.roottrace.security.dto.UserProfileDto;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
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

        return response(user, member);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email().toLowerCase().trim())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }
        // Account-only session: the user must explicitly select a workspace.
        return response(user, null);
    }

    @Transactional(readOnly = true)
    public List<WorkspaceMembershipDto> listWorkspaces(UUID userId) {
        return workspaceMemberRepository.findByUserId(userId).stream()
                .map(member -> new WorkspaceMembershipDto(
                        member.getWorkspace().getId(), member.getWorkspace().getName(),
                        member.getWorkspace().getSlug(), member.getRole()))
                .sorted(Comparator.comparing(WorkspaceMembershipDto::name)
                        .thenComparing(WorkspaceMembershipDto::workspaceId))
                .toList();
    }

    @Transactional(readOnly = true)
    public AuthResponse selectWorkspace(UUID userId, UUID workspaceId) {
        WorkspaceMember member = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));
        return response(member.getUser(), member);
    }

    @Transactional(readOnly = true)
    public AuthResponse demoLogin(DemoLoginRequest request) {
        String email = switch (request.role().toUpperCase().trim()) {
            case "ADMIN" -> "bob.admin@roottrace.io";
            case "VIEWER" -> "charlie.dev@roottrace.io";
            default -> "alice.sre@roottrace.io";
        };
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Demo account unavailable"));
        WorkspaceMember member = workspaceMemberRepository
                .findByWorkspaceIdAndUserId(AuthDataSeeder.DEFAULT_WORKSPACE_ID, user.getId())
                .orElseThrow(() -> new BadCredentialsException("Demo membership unavailable"));
        return response(user, member);
    }

    @Transactional(readOnly = true)
    public UserProfileDto getProfile(WorkspaceIdentity identity) {
        User user = userRepository.findById(identity.userId())
                .orElseThrow(() -> new BadCredentialsException("Account unavailable"));
        WorkspaceMember member = identity.workspaceId() == null ? null : workspaceMemberRepository
                .findByWorkspaceIdAndUserId(identity.workspaceId(), identity.userId())
                .orElseThrow(() -> new AccessDeniedException("Membership unavailable"));
        Workspace ws = member == null ? null : member.getWorkspace();
        return new UserProfileDto(user.getId(), user.getEmail(), user.getFullName(), identity.roles(),
                ws == null ? null : ws.getId(), ws == null ? null : ws.getName(), ws == null ? null : ws.getSlug(),
                member != null && "ROLE_ADMIN".equals(member.getRole()) ? ws.getApiKey() : null, false);
    }

    private AuthResponse response(User user, WorkspaceMember member) {
        Workspace ws = member == null ? null : member.getWorkspace();
        var identity = new WorkspaceIdentity(user.getId(), user.getEmail(), user.getFullName(),
                ws == null ? null : ws.getId(), member == null ? null : member.getRole());
        return new AuthResponse(tokenProvider.generateToken(user.getId(), identity.workspaceId()),
                user.getId(), user.getEmail(), user.getFullName(), identity.roles(), identity.workspaceId(),
                ws == null ? null : ws.getName(), ws == null ? null : ws.getSlug(),
                member != null && "ROLE_ADMIN".equals(member.getRole()) ? ws.getApiKey() : null);
    }
}
