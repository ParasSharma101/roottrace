package com.roottrace.workspace.controller;

import com.roottrace.domain.model.Workspace;
import com.roottrace.domain.model.WorkspaceMember;
import com.roottrace.domain.repository.WorkspaceMemberRepository;
import com.roottrace.domain.repository.WorkspaceRepository;
import com.roottrace.security.WorkspaceIdentity;
import com.roottrace.security.dto.TeamMemberDto;
import com.roottrace.security.dto.WorkspaceDetailsDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workspace")
@CrossOrigin(origins = "*")
public class WorkspaceController {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    public WorkspaceController(
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository
    ) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
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

}
