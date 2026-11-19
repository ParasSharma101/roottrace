package com.roottrace.workspace.controller;

import com.roottrace.infrastructure.jobs.DurableTaskStore;
import com.roottrace.security.WorkspaceIdentity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
public class InvestigationStatusController {
    private final DurableTaskStore tasks;
    public InvestigationStatusController(DurableTaskStore tasks) { this.tasks = tasks; }
    @GetMapping("/api/v1/incidents/{id}/investigation")
    public Map<String,Object> status(@PathVariable UUID id, Authentication authentication) {
        return tasks.investigationStatus(WorkspaceIdentity.require(authentication).requireWorkspace(), id);
    }
}
