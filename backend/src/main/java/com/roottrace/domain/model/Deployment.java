package com.roottrace.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "deployments")
public class Deployment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "service_name", nullable = false, length = 128)
    private String serviceName;

    @Column(name = "deployment_time", nullable = false)
    private Instant deploymentTime;

    @Column(name = "version_tag", nullable = false, length = 64)
    private String versionTag;

    @Column(name = "github_commit_hash", nullable = false, length = 64)
    private String githubCommitHash;

    @Column(name = "environment", nullable = false, length = 32)
    private String environment = "production";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Deployment() {}

    public Deployment(String serviceName, Instant deploymentTime, String versionTag, String githubCommitHash, String environment) {
        this.serviceName = serviceName;
        this.deploymentTime = deploymentTime != null ? deploymentTime : Instant.now();
        this.versionTag = versionTag;
        this.githubCommitHash = githubCommitHash;
        this.environment = environment != null ? environment : "production";
        this.createdAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (deploymentTime == null) deploymentTime = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public Instant getDeploymentTime() { return deploymentTime; }
    public void setDeploymentTime(Instant deploymentTime) { this.deploymentTime = deploymentTime; }

    public String getVersionTag() { return versionTag; }
    public void setVersionTag(String versionTag) { this.versionTag = versionTag; }

    public String getGithubCommitHash() { return githubCommitHash; }
    public void setGithubCommitHash(String githubCommitHash) { this.githubCommitHash = githubCommitHash; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
