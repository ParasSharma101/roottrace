package com.roottrace.workspace.controller;

import com.roottrace.security.WorkspaceIdentity;
import com.roottrace.security.dto.TeamMemberDto;
import com.roottrace.workspace.service.MembershipService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspace/members")
public class MembershipController {
    private final MembershipService service;
    public MembershipController(MembershipService service) { this.service=service; }

    @PatchMapping("/{membershipId}/role") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TeamMemberDto> role(Authentication auth,@PathVariable UUID membershipId,@Valid @RequestBody RoleChange request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.changeRole(WorkspaceIdentity.require(auth),membershipId,request.role(),request.expectedVersion(),request.reason()));
    }
    @PostMapping("/{membershipId}/remove")
    public ResponseEntity<Void> remove(Authentication auth,@PathVariable UUID membershipId,@Valid @RequestBody Removal request) {
        service.remove(WorkspaceIdentity.require(auth),membershipId,request.expectedVersion(),request.reason());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @GetMapping("/audit") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MembershipService.AuditPage> history(Authentication auth,@RequestParam(required=false) UUID before,@RequestParam(defaultValue="25") int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.history(WorkspaceIdentity.require(auth),before,limit));
    }
    public record RoleChange(@NotBlank @Pattern(regexp="ROLE_(ADMIN|SRE|VIEWER)") String role,@NotNull @Min(0) Long expectedVersion,@NotBlank @Size(max=500) String reason) {}
    public record Removal(@NotNull @Min(0) Long expectedVersion,@NotBlank @Size(max=500) String reason) {}
}
