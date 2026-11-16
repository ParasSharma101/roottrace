package com.roottrace.workspace.controller;

import com.roottrace.security.WorkspaceIdentity;
import com.roottrace.security.dto.*;
import com.roottrace.security.service.IngestionKeyService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspace/ingestion-keys")
@PreAuthorize("hasRole('ADMIN')")
public class IngestionKeyController {
    private final IngestionKeyService service;

    public IngestionKeyController(IngestionKeyService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<CreatedIngestionKeyDto> create(@Valid @RequestBody CreateIngestionKeyRequest request,
                                                       Authentication authentication) {
        var created = service.create(WorkspaceIdentity.require(authentication), request.name());
        return ResponseEntity.created(URI.create("/api/v1/workspace/ingestion-keys/" + created.key().id()))
                .cacheControl(CacheControl.noStore()).body(created);
    }

    @GetMapping
    public ResponseEntity<List<IngestionKeyDto>> list(Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.list(WorkspaceIdentity.require(authentication)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(@PathVariable UUID id, Authentication authentication) {
        service.revoke(WorkspaceIdentity.require(authentication), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<IngestionKeyDto> get(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.get(WorkspaceIdentity.require(authentication), id));
    }
}
