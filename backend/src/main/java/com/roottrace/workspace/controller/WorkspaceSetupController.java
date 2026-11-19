package com.roottrace.workspace.controller;

import com.roottrace.security.WorkspaceIdentity;
import com.roottrace.workspace.service.WorkspaceSetupService;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/workspace/setup-status")
public class WorkspaceSetupController {
    private final WorkspaceSetupService service;
    public WorkspaceSetupController(WorkspaceSetupService service) { this.service=service; }
    @GetMapping
    public ResponseEntity<WorkspaceSetupService.SetupStatus> status(Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.status(WorkspaceIdentity.require(authentication)));
    }
}
