package com.roottrace.workspace.controller;

import com.roottrace.domain.model.User;
import com.roottrace.domain.model.Workspace;
import com.roottrace.domain.model.WorkspaceInvite;
import com.roottrace.domain.model.WorkspaceMember;
import com.roottrace.domain.repository.UserRepository;
import com.roottrace.domain.repository.WorkspaceInviteRepository;
import com.roottrace.domain.repository.WorkspaceMemberRepository;
import com.roottrace.domain.repository.WorkspaceRepository;
import com.roottrace.security.WorkspaceIdentity;
import com.roottrace.security.dto.InviteMemberRequest;
import com.roottrace.security.dto.InviteResponseDto;
import com.roottrace.security.dto.TeamMemberDto;
import com.roottrace.security.dto.WorkspaceDetailsDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspace")
@CrossOrigin(origins = "*")
public class WorkspaceController {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceInviteRepository workspaceInviteRepository;
    private final UserRepository userRepository;

    public WorkspaceController(
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            WorkspaceInviteRepository workspaceInviteRepository,
            UserRepository userRepository
    ) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.workspaceInviteRepository = workspaceInviteRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<WorkspaceDetailsDto> getWorkspace(Authentication authentication) {
        var identity = WorkspaceIdentity.require(authentication);
        Workspace ws = workspaceRepository.findById(identity.requireWorkspace()).orElseThrow();

        List<WorkspaceMember> members = workspaceMemberRepository.findByWorkspaceId(ws.getId());
        List<TeamMemberDto> memberDtos = members.stream()
                .map(m -> new TeamMemberDto(
                        m.getUser().getId(),
                        m.getUser().getFullName(),
                        m.getUser().getEmail(),
                        m.getRole(),
                        m.getCreatedAt()
                ))
                .toList();

        return ResponseEntity.ok(new WorkspaceDetailsDto(
                ws.getId(),
                ws.getName(),
                ws.getSlug(),
                null,
                memberDtos
        ));
    }

    @PostMapping("/invites")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<InviteResponseDto> inviteMember(
            @Valid @RequestBody InviteMemberRequest request,
            Authentication authentication
    ) {
        var identity = WorkspaceIdentity.require(authentication);
        User admin = userRepository.findById(identity.userId()).orElseThrow();
        Workspace ws = workspaceRepository.findById(identity.requireWorkspace()).orElseThrow();

        String inviteEmail = request.email().toLowerCase().trim();
        String role = request.role().toUpperCase().startsWith("ROLE_")
                ? request.role().toUpperCase()
                : "ROLE_" + request.role().toUpperCase();

        // If user already exists in the system, add directly to workspace
        User existingUser = userRepository.findByEmail(inviteEmail).orElse(null);
        if (existingUser != null) {
            if (workspaceMemberRepository.findByWorkspaceIdAndUserId(ws.getId(), existingUser.getId()).isEmpty()) {
                workspaceMemberRepository.save(new WorkspaceMember(ws, existingUser, role));
            }
        }

        String token = "rt_inv_" + UUID.randomUUID().toString().replace("-", "");
        WorkspaceInvite invite = new WorkspaceInvite(
                ws,
                inviteEmail,
                role,
                token,
                admin,
                Instant.now().plus(7, ChronoUnit.DAYS)
        );

        WorkspaceInvite saved = workspaceInviteRepository.save(invite);
        InviteResponseDto dto = new InviteResponseDto(
                saved.getId(),
                saved.getEmail(),
                saved.getRole(),
                saved.getStatus(),
                saved.getToken(),
                saved.getExpiresAt()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }
}
