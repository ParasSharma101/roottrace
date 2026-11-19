package com.roottrace.workspace.controller;

import com.roottrace.security.WorkspaceIdentity;
import com.roottrace.security.dto.InviteMemberRequest;
import com.roottrace.workspace.service.InvitationService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
public class InvitationController {
    private final InvitationService service;
    public InvitationController(InvitationService service) { this.service=service; }
    @PostMapping("/api/v1/workspace/invites") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InvitationService.Invitation> create(Authentication auth,@Valid @RequestBody InviteMemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(service.create(WorkspaceIdentity.require(auth),request));
    }
    @GetMapping("/api/v1/workspace/invites") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InvitationService.Page> list(Authentication auth,@RequestParam(required=false) UUID before,@RequestParam(defaultValue="25") int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(WorkspaceIdentity.require(auth),before,limit));
    }
    @DeleteMapping("/api/v1/workspace/invites/{id}") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> revoke(Authentication auth,@PathVariable UUID id) {
        service.revoke(WorkspaceIdentity.require(auth),id); return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @GetMapping("/api/v1/auth/invitations")
    public ResponseEntity<InvitationService.Page> mine(Authentication auth,@RequestParam(required=false) UUID before,@RequestParam(defaultValue="25") int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.mine(WorkspaceIdentity.require(auth),before,limit));
    }
    @PostMapping("/api/v1/auth/invitations/{id}/accept")
    public ResponseEntity<InvitationService.Accepted> accept(Authentication auth,@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.accept(WorkspaceIdentity.require(auth),id));
    }
}
