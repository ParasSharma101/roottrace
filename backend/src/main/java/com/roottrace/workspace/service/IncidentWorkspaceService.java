package com.roottrace.workspace.service;

import com.roottrace.domain.exception.ResourceNotFoundException;
import com.roottrace.domain.model.*;
import com.roottrace.domain.repository.*;
import com.roottrace.workspace.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Transactional
public class IncidentWorkspaceService {

    private static final Logger log = LoggerFactory.getLogger(IncidentWorkspaceService.class);
    private static final DateTimeFormatter UTC_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private final IncidentRepository incidentRepository;
    private final AlertRepository alertRepository;
    private final DeploymentRepository deploymentRepository;
    private final GitHubPRRepository gitHubPRRepository;
    private final LogEventRepository logEventRepository;
    private final HypothesisRepository hypothesisRepository;
    private final IncidentNoteRepository incidentNoteRepository;

    public IncidentWorkspaceService(
            IncidentRepository incidentRepository,
            AlertRepository alertRepository,
            DeploymentRepository deploymentRepository,
            GitHubPRRepository gitHubPRRepository,
            LogEventRepository logEventRepository,
            HypothesisRepository hypothesisRepository,
            IncidentNoteRepository incidentNoteRepository
    ) {
        this.incidentRepository = incidentRepository;
        this.alertRepository = alertRepository;
        this.deploymentRepository = deploymentRepository;
        this.gitHubPRRepository = gitHubPRRepository;
        this.logEventRepository = logEventRepository;
        this.hypothesisRepository = hypothesisRepository;
        this.incidentNoteRepository = incidentNoteRepository;
    }

    @Transactional(readOnly = true)
    public List<IncidentSummaryResponse> listIncidents(UUID workspaceId, IncidentStatus status, SeverityLevel severity, String serviceName) {
        List<Incident> allIncidents = incidentRepository.findByWorkspaceIdOrderByStartTimeDesc(Objects.requireNonNull(workspaceId));

        return allIncidents.stream()
                .filter(i -> status == null || i.getStatus() == status)
                .filter(i -> severity == null || i.getSeverityLevel() == severity)
                .map(incident -> {
                    List<Alert> alerts = alertRepository.findByIncidentIdOrderByTriggeredAtDesc(incident.getId());
                    String resolvedService = resolveServiceName(incident, alerts);
                    int hypothesisCount = hypothesisRepository.findByIncidentIdOrderByConfidenceScoreDesc(incident.getId()).size();

                    return new IncidentSummaryResponse(
                            incident.getId(),
                            incident.getTitle(),
                            incident.getStatus(),
                            incident.getSeverityLevel(),
                            resolvedService,
                            incident.getStartTime(),
                            incident.getResolutionTime(),
                            alerts.size(),
                            hypothesisCount
                    );
                })
                .filter(summary -> serviceName == null || summary.serviceName().equalsIgnoreCase(serviceName.trim()))
                .toList();
    }

    @Transactional(readOnly = true)
    public IncidentDetailResponse getIncident(UUID workspaceId, UUID id) {
        Incident incident = ownedIncident(workspaceId, id);

        List<Alert> alerts = alertRepository.findByIncidentIdOrderByTriggeredAtDesc(id);
        List<Hypothesis> hypotheses = hypothesisRepository.findByIncidentIdOrderByConfidenceScoreDesc(id);
        List<IncidentNote> notes = incidentNoteRepository.findByIncidentIdOrderByCreatedAtAsc(id);
        String serviceName = resolveServiceName(incident, alerts);

        List<AlertDetailDto> alertDtos = alerts.stream()
                .map(a -> new AlertDetailDto(
                        a.getId(),
                        a.getSource(),
                        a.getAlertType(),
                        a.getServiceName(),
                        a.getRawPayload(),
                        a.getTriggeredAt()
                ))
                .toList();

        List<HypothesisDetailDto> hypothesisDtos = hypotheses.stream()
                .map(this::toHypothesisDetailDto)
                .toList();

        List<IncidentNoteDetailDto> noteDtos = notes.stream()
                .map(this::toIncidentNoteDetailDto)
                .toList();

        return new IncidentDetailResponse(
                incident.getId(),
                incident.getTitle(),
                incident.getSummary(),
                incident.getStatus(),
                incident.getSeverityLevel(),
                serviceName,
                incident.getStartTime(),
                incident.getResolutionTime(),
                incident.getCreatedAt(),
                incident.getUpdatedAt(),
                alertDtos,
                hypothesisDtos,
                noteDtos
        );
    }

    @Transactional(readOnly = true)
    public List<TimelineEventDto> getTimeline(UUID workspaceId, UUID id) {
        Incident incident = ownedIncident(workspaceId, id);

        List<Alert> alerts = alertRepository.findByIncidentIdOrderByTriggeredAtDesc(id);
        String serviceName = resolveServiceName(incident, alerts);

        Instant windowStart = incident.getStartTime().minus(Duration.ofMinutes(30));
        Instant windowEnd = incident.getResolutionTime() != null
                ? incident.getResolutionTime().plus(Duration.ofMinutes(5))
                : Instant.now().plus(Duration.ofMinutes(5));

        List<TimelineEventDto> timeline = new ArrayList<>();

        // 1. Deployments & GitHub PRs
        List<Deployment> deployments = deploymentRepository
                .findByWorkspaceIdAndServiceNameAndDeploymentTimeBetweenOrderByDeploymentTimeDesc(workspaceId, serviceName, windowStart, windowEnd);

        for (Deployment deployment : deployments) {
            Map<String, Object> depMeta = new HashMap<>();
            depMeta.put("versionTag", deployment.getVersionTag());
            depMeta.put("commitHash", deployment.getGithubCommitHash());
            depMeta.put("environment", deployment.getEnvironment());

            timeline.add(new TimelineEventDto(
                    "dep-" + deployment.getId(),
                    deployment.getDeploymentTime(),
                    TimelineEventType.DEPLOYMENT,
                    "Deployment: " + deployment.getServiceName() + " (" + deployment.getVersionTag() + ")",
                    "Deployed commit " + deployment.getGithubCommitHash() + " to " + deployment.getEnvironment(),
                    depMeta,
                    "INFO"
            ));

            List<GitHubPR> prs = gitHubPRRepository.findByDeploymentId(deployment.getId());
            for (GitHubPR pr : prs) {
                Map<String, Object> prMeta = new HashMap<>();
                prMeta.put("prNumber", pr.getPrNumber());
                prMeta.put("author", pr.getAuthor());

                Instant prTime = pr.getMergedAt() != null ? pr.getMergedAt() : deployment.getDeploymentTime();
                timeline.add(new TimelineEventDto(
                        "pr-" + pr.getId(),
                        prTime,
                        TimelineEventType.GITHUB_PR,
                        "PR #" + pr.getPrNumber() + ": " + pr.getTitle(),
                        "Author: " + pr.getAuthor() + " | Diff: " + (pr.getDiffSummary() != null ? pr.getDiffSummary() : "No diff summary"),
                        prMeta,
                        "INFO"
                ));
            }
        }

        // 2. Alerts
        for (Alert alert : alerts) {
            Map<String, Object> alertMeta = new HashMap<>();
            alertMeta.put("source", alert.getSource());
            alertMeta.put("serviceName", alert.getServiceName());
            alertMeta.put("alertType", alert.getAlertType());

            timeline.add(new TimelineEventDto(
                    "alert-" + alert.getId(),
                    alert.getTriggeredAt(),
                    TimelineEventType.ALERT,
                    "Alert Triggered: " + alert.getAlertType(),
                    "Source: " + alert.getSource() + " on " + alert.getServiceName(),
                    alertMeta,
                    "CRITICAL"
            ));
        }

        // 3. Representative Error Logs (limit to top 15 to avoid timeline bloat)
        List<LogEvent> errorLogs = logEventRepository
                .findByWorkspaceIdAndServiceNameAndLogLevelAndTimestampBetweenOrderByTimestampDesc(workspaceId, serviceName, LogLevel.ERROR, windowStart, windowEnd);

        Set<String> seenMessages = new HashSet<>();
        int logCount = 0;
        for (LogEvent logEvent : errorLogs) {
            String shortMsg = truncate(logEvent.getMessage(), 60);
            if (seenMessages.add(shortMsg)) {
                Map<String, Object> logMeta = new HashMap<>();
                logMeta.put("logId", logEvent.getId().toString());
                logMeta.put("serviceName", logEvent.getServiceName());

                timeline.add(new TimelineEventDto(
                        "log-" + logEvent.getId(),
                        logEvent.getTimestamp(),
                        TimelineEventType.LOG_ERROR,
                        "Error Log: " + shortMsg,
                        logEvent.getMessage(),
                        logMeta,
                        "ERROR"
                ));
                logCount++;
                if (logCount >= 15) {
                    break;
                }
            }
        }

        // 4. AI Hypotheses
        List<Hypothesis> hypotheses = hypothesisRepository.findByIncidentIdOrderByConfidenceScoreDesc(id);
        for (Hypothesis hyp : hypotheses) {
            Map<String, Object> hypMeta = new HashMap<>();
            hypMeta.put("hypothesisId", hyp.getId().toString());
            hypMeta.put("confidence", hyp.getConfidenceScore());
            hypMeta.put("status", hyp.getStatus().name());

            int confidencePercent = hyp.getConfidenceScore().multiply(BigDecimal.valueOf(100)).intValue();
            timeline.add(new TimelineEventDto(
                    "hyp-" + hyp.getId(),
                    hyp.getCreatedAt(),
                    TimelineEventType.AI_HYPOTHESIS,
                    "AI Hypothesis (" + confidencePercent + "% Confidence) [" + hyp.getStatus() + "]",
                    hyp.getDescription(),
                    hypMeta,
                    hyp.getStatus() == HypothesisStatus.VERIFIED ? "SUCCESS" : "WARNING"
            ));
        }

        // 5. Incident Notes / Human Actions
        List<IncidentNote> notes = incidentNoteRepository.findByIncidentIdOrderByCreatedAtAsc(id);
        for (IncidentNote note : notes) {
            Map<String, Object> noteMeta = new HashMap<>();
            noteMeta.put("author", note.getAuthor());
            if (note.getHypothesis() != null) {
                noteMeta.put("hypothesisId", note.getHypothesis().getId().toString());
            }

            timeline.add(new TimelineEventDto(
                    "note-" + note.getId(),
                    note.getCreatedAt(),
                    TimelineEventType.HUMAN_ACTION,
                    "Action by " + note.getAuthor(),
                    note.getNote(),
                    noteMeta,
                    "INFO"
            ));
        }

        // Chronologically sort strictly
        timeline.sort(Comparator.comparing(TimelineEventDto::timestamp));
        return timeline;
    }

    public HypothesisDetailDto verifyHypothesis(UUID workspaceId, UUID incidentId, UUID hypothesisId, String author) {
        Hypothesis hypothesis = ownedHypothesis(workspaceId, incidentId, hypothesisId);

        hypothesis.setStatus(HypothesisStatus.VERIFIED);
        hypothesis = hypothesisRepository.save(hypothesis);

        // Record verification audit note
        IncidentNote note = new IncidentNote(
                hypothesis.getIncident(),
                hypothesis,
                author != null && !author.isBlank() ? author : "On-Call Engineer",
                "Verified Root Cause Hypothesis: " + hypothesis.getDescription()
        );
        incidentNoteRepository.save(note);

        log.info("Hypothesis [{}] verified by engineer [{}] for incident [{}]", hypothesisId, author, incidentId);
        return toHypothesisDetailDto(hypothesis);
    }

    public HypothesisDetailDto rejectHypothesis(UUID workspaceId, UUID incidentId, UUID hypothesisId, String author, String reason) {
        Hypothesis hypothesis = ownedHypothesis(workspaceId, incidentId, hypothesisId);

        hypothesis.setStatus(HypothesisStatus.REJECTED);
        hypothesis = hypothesisRepository.save(hypothesis);

        // Record rejection audit note
        String reasonStr = (reason != null && !reason.isBlank()) ? reason : "No rejection reason provided";
        IncidentNote note = new IncidentNote(
                hypothesis.getIncident(),
                hypothesis,
                author != null && !author.isBlank() ? author : "On-Call Engineer",
                "Rejected Hypothesis. Reason: " + reasonStr
        );
        incidentNoteRepository.save(note);

        log.info("Hypothesis [{}] rejected by engineer [{}] for incident [{}]", hypothesisId, author, incidentId);
        return toHypothesisDetailDto(hypothesis);
    }

    public IncidentNoteDetailDto addNote(UUID workspaceId, UUID incidentId, AddNoteRequest request) {
        Incident incident = ownedIncident(workspaceId, incidentId);

        Hypothesis linkedHypothesis = null;
        if (request.hypothesisId() != null) {
            linkedHypothesis = ownedHypothesis(workspaceId, incidentId, request.hypothesisId());
        }

        IncidentNote note = new IncidentNote(incident, linkedHypothesis, request.author(), request.note());
        note = incidentNoteRepository.save(note);

        return toIncidentNoteDetailDto(note);
    }

    public IncidentDetailResponse updateStatus(UUID workspaceId, UUID incidentId, IncidentStatus newStatus, String author) {
        Incident incident = ownedIncident(workspaceId, incidentId);

        IncidentStatus oldStatus = incident.getStatus();
        incident.setStatus(newStatus);

        if (newStatus == IncidentStatus.RESOLVED && incident.getResolutionTime() == null) {
            incident.setResolutionTime(Instant.now());
        } else if (newStatus != IncidentStatus.RESOLVED) {
            incident.setResolutionTime(null);
        }

        incidentRepository.save(incident);

        IncidentNote note = new IncidentNote(
                incident,
                null,
                author,
                "Incident status transitioned from [" + oldStatus + "] to [" + newStatus + "]"
        );
        incidentNoteRepository.save(note);

        log.info("Incident [{}] status updated to [{}]", incidentId, newStatus);
        return getIncident(workspaceId, incidentId);
    }

    @Transactional(readOnly = true)
    public PostmortemResponse generatePostmortem(UUID workspaceId, UUID incidentId) {
        Incident incident = ownedIncident(workspaceId, incidentId);

        List<Alert> alerts = alertRepository.findByIncidentIdOrderByTriggeredAtDesc(incidentId);
        List<Hypothesis> hypotheses = hypothesisRepository.findByIncidentIdOrderByConfidenceScoreDesc(incidentId);
        List<IncidentNote> notes = incidentNoteRepository.findByIncidentIdOrderByCreatedAtAsc(incidentId);
        List<TimelineEventDto> timeline = getTimeline(workspaceId, incidentId);

        String serviceName = resolveServiceName(incident, alerts);

        // Find primary root cause
        Hypothesis primaryHypothesis = hypotheses.stream()
                .filter(h -> h.getStatus() == HypothesisStatus.VERIFIED)
                .findFirst()
                .orElseGet(() -> hypotheses.isEmpty() ? null : hypotheses.get(0));

        // Compute MTTR
        String mttrString = "In Progress";
        if (incident.getResolutionTime() != null) {
            Duration duration = Duration.between(incident.getStartTime(), incident.getResolutionTime());
            long hours = duration.toHours();
            long minutes = duration.toMinutesPart();
            long seconds = duration.toSecondsPart();
            if (hours > 0) {
                mttrString = String.format("%dh %dm %ds", hours, minutes, seconds);
            } else {
                mttrString = String.format("%dm %ds", minutes, seconds);
            }
        }

        StringBuilder md = new StringBuilder();
        md.append("# RootTrace Incident Postmortem: ").append(incident.getTitle()).append("\n\n");

        md.append("## Overview\n\n");
        md.append("| Property | Value |\n");
        md.append("| :--- | :--- |\n");
        md.append("| **Incident ID** | `").append(incident.getId()).append("` |\n");
        md.append("| **Service Impacted** | `").append(serviceName).append("` |\n");
        md.append("| **Severity** | **").append(incident.getSeverityLevel()).append("** |\n");
        md.append("| **Status** | `").append(incident.getStatus()).append("` |\n");
        md.append("| **Triggered At (UTC)** | ").append(UTC_FORMATTER.format(incident.getStartTime())).append(" |\n");
        md.append("| **Resolved At (UTC)** | ").append(incident.getResolutionTime() != null ? UTC_FORMATTER.format(incident.getResolutionTime()) : "Ongoing").append(" |\n");
        md.append("| **Total MTTR / Duration** | ").append(mttrString).append(" |\n\n");

        md.append("## 1. Executive Summary\n\n");
        if (incident.getSummary() != null && !incident.getSummary().isBlank()) {
            md.append(incident.getSummary()).append("\n\n");
        } else if (primaryHypothesis != null) {
            md.append(primaryHypothesis.getDescription()).append("\n\n");
        } else {
            md.append("Incident occurred on service `").append(serviceName).append("` triggering ")
                    .append(alerts.size()).append(" critical alerts.\n\n");
        }

        md.append("## 2. Root Cause Analysis\n\n");
        if (primaryHypothesis != null) {
            int conf = primaryHypothesis.getConfidenceScore().multiply(BigDecimal.valueOf(100)).intValue();
            md.append("**Primary Hypothesis:** ").append(primaryHypothesis.getDescription()).append("\n\n");
            md.append("- **Confidence Score:** ").append(conf).append("%\n");
            md.append("- **Verification Status:** `").append(primaryHypothesis.getStatus()).append("`\n\n");

            md.append("### Grounded Evidence Citations\n\n");
            md.append("```json\n");
            md.append(primaryHypothesis.getEvidenceCitations()).append("\n");
            md.append("```\n\n");
        } else {
            md.append("_No root cause hypothesis generated or verified for this incident._\n\n");
        }

        md.append("## 3. Incident Timeline\n\n");
        md.append("| Timestamp (UTC) | Type | Event | Description |\n");
        md.append("| :--- | :--- | :--- | :--- |\n");
        for (TimelineEventDto event : timeline) {
            String time = UTC_FORMATTER.format(event.timestamp());
            md.append("| ").append(time)
                    .append(" | `").append(event.eventType())
                    .append("` | **").append(escapeMarkdown(event.title()))
                    .append("** | ").append(escapeMarkdown(truncate(event.description(), 120)))
                    .append(" |\n");
        }
        md.append("\n");

        md.append("## 4. Engineer Collaboration & Audit Log\n\n");
        if (notes.isEmpty()) {
            md.append("_No collaboration notes recorded during this incident._\n\n");
        } else {
            for (IncidentNote note : notes) {
                md.append("- **[").append(UTC_FORMATTER.format(note.getCreatedAt()))
                        .append("] (").append(note.getAuthor()).append("):** ")
                        .append(note.getNote()).append("\n");
            }
            md.append("\n");
        }

        md.append("## 5. Prevention & Action Items\n\n");
        md.append("- [ ] Audit deployment rollouts and health check latency for `").append(serviceName).append("`\n");
        md.append("- [ ] Verify alert threshold configurations against production p99 latency SLOs\n");
        md.append("- [ ] Add automated canary regression tests for correlated PR changes\n\n");
        md.append("---\n");
        md.append("_Generated automatically by RootTrace AI Production Incident Investigator on ")
                .append(UTC_FORMATTER.format(Instant.now())).append("_\n");

        return new PostmortemResponse(incidentId, incident.getTitle(), md.toString(), Instant.now());
    }

    private Incident ownedIncident(UUID workspaceId, UUID incidentId) {
        return incidentRepository.findByIdAndWorkspaceId(incidentId, Objects.requireNonNull(workspaceId))
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found"));
    }

    private Hypothesis ownedHypothesis(UUID workspaceId, UUID incidentId, UUID hypothesisId) {
        ownedIncident(workspaceId, incidentId);
        return hypothesisRepository.findByIdAndIncidentId(hypothesisId, incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Hypothesis not found"));
    }

    private String resolveServiceName(Incident incident, List<Alert> alerts) {
        if (alerts != null && !alerts.isEmpty()) {
            return alerts.get(0).getServiceName();
        }
        String title = incident.getTitle();
        if (title != null && title.startsWith("[Alert] ")) {
            int colonIdx = title.indexOf(':', 8);
            if (colonIdx > 8) {
                return title.substring(8, colonIdx).trim();
            }
        }
        return "unknown-service";
    }

    private HypothesisDetailDto toHypothesisDetailDto(Hypothesis h) {
        return new HypothesisDetailDto(
                h.getId(),
                h.getIncident().getId(),
                h.getDescription(),
                h.getConfidenceScore(),
                h.getStatus(),
                h.getEvidenceCitations(),
                h.getCreatedAt(),
                h.getUpdatedAt()
        );
    }

    private IncidentNoteDetailDto toIncidentNoteDetailDto(IncidentNote n) {
        return new IncidentNoteDetailDto(
                n.getId(),
                n.getIncident().getId(),
                n.getHypothesis() != null ? n.getHypothesis().getId() : null,
                n.getAuthor(),
                n.getNote(),
                n.getCreatedAt()
        );
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }

    private String escapeMarkdown(String text) {
        if (text == null) return "";
        return text.replace("|", "\\|").replace("\n", " ");
    }
}
