package com.roottrace.correlation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roottrace.correlation.model.*;
import com.roottrace.domain.model.Alert;
import com.roottrace.domain.model.Deployment;
import com.roottrace.domain.model.GitHubPR;
import com.roottrace.domain.model.Incident;
import com.roottrace.domain.model.LogEvent;
import com.roottrace.domain.repository.AlertRepository;
import com.roottrace.domain.repository.DeploymentRepository;
import com.roottrace.domain.repository.GitHubPRRepository;
import com.roottrace.domain.repository.IncidentRepository;
import com.roottrace.domain.repository.LogEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CorrelationService {

    private static final Logger log = LoggerFactory.getLogger(CorrelationService.class);

    private static final int WINDOW_BEFORE_MINUTES = 30;
    private static final int WINDOW_AFTER_MINUTES = 5;

    private final IncidentRepository incidentRepository;
    private final AlertRepository alertRepository;
    private final DeploymentRepository deploymentRepository;
    private final GitHubPRRepository gitHubPRRepository;
    private final LogEventRepository logEventRepository;
    private final LogFingerprinter logFingerprinter;
    private final ObjectMapper objectMapper;

    public CorrelationService(IncidentRepository incidentRepository,
                              AlertRepository alertRepository,
                              DeploymentRepository deploymentRepository,
                              GitHubPRRepository gitHubPRRepository,
                              LogEventRepository logEventRepository,
                              LogFingerprinter logFingerprinter,
                              ObjectMapper objectMapper) {
        this.incidentRepository = incidentRepository;
        this.alertRepository = alertRepository;
        this.deploymentRepository = deploymentRepository;
        this.gitHubPRRepository = gitHubPRRepository;
        this.logEventRepository = logEventRepository;
        this.logFingerprinter = logFingerprinter;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public IncidentContextBundle buildContextBundle(UUID incidentId) {
        log.info("Building correlation context bundle for incident [{}]", incidentId);

        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("Incident not found: " + incidentId));

        if (incident.getWorkspace() == null) {
            throw new IllegalStateException("Incident has no workspace: " + incidentId);
        }
        UUID workspaceId = incident.getWorkspace().getId();

        List<Alert> alerts = alertRepository.findByIncidentIdOrderByTriggeredAtDesc(incidentId);

        String serviceName;
        if (!alerts.isEmpty()) {
            serviceName = alerts.get(0).getServiceName();
        } else {
            serviceName = extractServiceNameFromTitle(incident.getTitle());
        }

        Instant alertTime = incident.getStartTime();
        Instant windowStart = alertTime.minus(WINDOW_BEFORE_MINUTES, ChronoUnit.MINUTES);
        Instant windowEnd = alertTime.plus(WINDOW_AFTER_MINUTES, ChronoUnit.MINUTES);

        // 1. Fetch deployments in window
        List<Deployment> deployments = deploymentRepository
                .findByWorkspaceIdAndServiceNameAndDeploymentTimeBetweenOrderByDeploymentTimeDesc(workspaceId, serviceName, windowStart, windowEnd);

        List<DeploymentSummary> deploymentSummaries = new ArrayList<>();
        for (Deployment deployment : deployments) {
            List<GitHubPR> prs = gitHubPRRepository.findByDeploymentId(deployment.getId());
            List<PRSummary> prSummaries = prs.stream()
                    .map(pr -> new PRSummary(
                            pr.getId(),
                            pr.getPrNumber(),
                            pr.getTitle(),
                            pr.getAuthor(),
                            pr.getDiffSummary(),
                            pr.getFilesChanged(),
                            pr.getMergedAt()
                    ))
                    .toList();

            deploymentSummaries.add(new DeploymentSummary(
                    deployment.getId(),
                    deployment.getServiceName(),
                    deployment.getDeploymentTime(),
                    deployment.getVersionTag(),
                    deployment.getGithubCommitHash(),
                    deployment.getEnvironment(),
                    prSummaries
            ));
        }

        // 2. Fetch and compress raw logs in window
        List<LogEvent> rawLogs = logEventRepository
                .findByWorkspaceIdAndServiceNameAndTimestampBetweenOrderByTimestampDesc(workspaceId, serviceName, windowStart, windowEnd);

        List<CompressedLogSignature> compressedLogs = logFingerprinter.compress(rawLogs);

        // 3. Map alerts
        List<AlertSummary> alertSummaries = alerts.stream()
                .map(a -> new AlertSummary(
                        a.getId(),
                        a.getSource(),
                        a.getAlertType(),
                        a.getServiceName(),
                        a.getTriggeredAt(),
                        a.getRawPayload()
                ))
                .toList();

        IncidentContextBundle bundle = new IncidentContextBundle(
                workspaceId,
                incident.getId(),
                incident.getTitle(),
                incident.getSeverityLevel(),
                incident.getStatus(),
                serviceName,
                incident.getStartTime(),
                windowStart,
                windowEnd,
                alertSummaries,
                deploymentSummaries,
                compressedLogs,
                rawLogs.size(),
                Instant.now()
        );

        log.info("Built bundle for incident [{}]: {} deployments, {} raw logs compressed into {} signatures",
                incidentId, deploymentSummaries.size(), rawLogs.size(), compressedLogs.size());

        return bundle;
    }

    @Transactional(readOnly = true)
    public String buildContextBundleAsJson(UUID incidentId) throws JsonProcessingException {
        IncidentContextBundle bundle = buildContextBundle(incidentId);
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(bundle);
    }

    private String extractServiceNameFromTitle(String title) {
        if (title != null && title.startsWith("[Alert] ") && title.contains(":")) {
            int start = "[Alert] ".length();
            int end = title.indexOf(":", start);
            if (end > start) {
                return title.substring(start, end).trim();
            }
        }
        return "unknown-service";
    }
}
