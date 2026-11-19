package com.roottrace.ingestion.controller;

import com.roottrace.ingestion.service.IngestionSubmissionService;
import com.roottrace.security.IngestionIdentity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/webhooks/requests")
public class IngestionStatusController {
    private final IngestionSubmissionService submissions;
    public IngestionStatusController(IngestionSubmissionService submissions) { this.submissions = submissions; }
    @GetMapping("/{id}")
    public Map<String,Object> status(@PathVariable UUID id, Authentication authentication) {
        return submissions.status(IngestionIdentity.requireWorkspace(authentication), id);
    }
}
