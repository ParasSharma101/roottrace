package com.roottrace.workspace.controller;

import com.roottrace.domain.model.IncidentStatus;
import com.roottrace.domain.model.SeverityLevel;
import com.roottrace.workspace.dto.*;
import com.roottrace.workspace.service.IncidentWorkspaceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/incidents")
@CrossOrigin(origins = "*")
public class IncidentController {

    private final IncidentWorkspaceService workspaceService;

    public IncidentController(IncidentWorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping
    public ResponseEntity<List<IncidentSummaryResponse>> listIncidents(
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) SeverityLevel severity,
            @RequestParam(required = false) String serviceName
    ) {
        List<IncidentSummaryResponse> list = workspaceService.listIncidents(status, severity, serviceName);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<IncidentDetailResponse> getIncident(@PathVariable UUID id) {
        IncidentDetailResponse detail = workspaceService.getIncident(id);
        return ResponseEntity.ok(detail);
    }

    @GetMapping("/{id}/timeline")
    public ResponseEntity<List<TimelineEventDto>> getTimeline(@PathVariable UUID id) {
        List<TimelineEventDto> timeline = workspaceService.getTimeline(id);
        return ResponseEntity.ok(timeline);
    }

    @PostMapping("/{id}/hypotheses/{hypothesisId}/verify")
    public ResponseEntity<HypothesisDetailDto> verifyHypothesis(
            @PathVariable UUID id,
            @PathVariable UUID hypothesisId,
            @RequestBody(required = false) VerifyHypothesisRequest request,
            Authentication authentication
    ) {
        String fallback = request != null ? request.author() : null;
        String author = resolveAuthor(authentication, fallback);
        HypothesisDetailDto result = workspaceService.verifyHypothesis(id, hypothesisId, author);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/hypotheses/{hypothesisId}/reject")
    public ResponseEntity<HypothesisDetailDto> rejectHypothesis(
            @PathVariable UUID id,
            @PathVariable UUID hypothesisId,
            @Valid @RequestBody RejectHypothesisRequest request,
            Authentication authentication
    ) {
        String author = resolveAuthor(authentication, request.author());
        HypothesisDetailDto result = workspaceService.rejectHypothesis(id, hypothesisId, author, request.reason());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/notes")
    public ResponseEntity<IncidentNoteDetailDto> addNote(
            @PathVariable UUID id,
            @Valid @RequestBody AddNoteRequest request,
            Authentication authentication
    ) {
        String author = resolveAuthor(authentication, request.author());
        AddNoteRequest effectiveRequest = new AddNoteRequest(author, request.note(), request.hypothesisId());
        IncidentNoteDetailDto result = workspaceService.addNote(id, effectiveRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    private String resolveAuthor(Authentication authentication, String fallback) {
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
            return authentication.getName();
        }
        return "On-Call Engineer";
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<IncidentDetailResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateIncidentStatusRequest request
    ) {
        IncidentDetailResponse result = workspaceService.updateStatus(id, request.status());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/postmortem")
    public ResponseEntity<PostmortemResponse> getPostmortem(@PathVariable UUID id) {
        PostmortemResponse response = workspaceService.generatePostmortem(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/{id}/postmortem/export", produces = MediaType.TEXT_MARKDOWN_VALUE)
    public ResponseEntity<String> exportPostmortemMarkdown(@PathVariable UUID id) {
        PostmortemResponse response = workspaceService.generatePostmortem(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"postmortem-" + id + ".md\"")
                .body(response.markdownContent());
    }
}
