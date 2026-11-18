package com.roottrace.workspace.controller;

import com.roottrace.security.WorkspaceIdentity;
import com.roottrace.workspace.service.TaskRecoveryService;
import com.roottrace.workspace.service.TaskRecoveryService.*;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspace/tasks")
@PreAuthorize("hasAnyRole('SRE','ADMIN')")
public class TaskRecoveryController {
    private final TaskRecoveryService service;
    public TaskRecoveryController(TaskRecoveryService service) { this.service=service; }
    @GetMapping
    public ResponseEntity<Page<TaskView>> list(Authentication auth,@RequestParam(defaultValue="FAILED") Status status,
            @RequestParam(required=false) Kind kind,@RequestParam(required=false) UUID before,@RequestParam(defaultValue="25") int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(WorkspaceIdentity.require(auth),status,kind,before,limit));
    }
    @GetMapping("/{id}")
    public ResponseEntity<TaskView> get(Authentication auth,@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(WorkspaceIdentity.require(auth),id));
    }
    @GetMapping("/{id}/redrives")
    public ResponseEntity<Page<RedriveView>> history(Authentication auth,@PathVariable UUID id,@RequestParam(required=false) UUID before,@RequestParam(defaultValue="25") int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.history(WorkspaceIdentity.require(auth),id,before,limit));
    }
    @PostMapping("/{id}/redrive")
    public ResponseEntity<TaskView> redrive(Authentication auth,@PathVariable UUID id,@Valid @RequestBody RedriveRequest request) {
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(service.redrive(WorkspaceIdentity.require(auth),id,request));
    }
}
