package com.roottrace.workspace.controller;

import com.roottrace.security.WorkspaceIdentity;
import com.roottrace.workspace.service.QueueMonitoringService;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/workspace/queue-health")
@PreAuthorize("hasAnyRole('ADMIN','SRE')")
public class QueueMonitoringController {
    private final QueueMonitoringService service;
    public QueueMonitoringController(QueueMonitoringService service) { this.service=service; }
    @GetMapping
    public ResponseEntity<QueueMonitoringService.Summary> summary(Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.summary(WorkspaceIdentity.require(authentication)));
    }
}
