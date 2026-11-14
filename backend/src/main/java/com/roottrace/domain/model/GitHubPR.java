package com.roottrace.domain.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "github_prs")
public class GitHubPR {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deployment_id", nullable = false)
    private Deployment deployment;

    @Column(name = "pr_number", nullable = false)
    private Integer prNumber;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "author", nullable = false, length = 128)
    private String author;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "files_changed", nullable = false, columnDefinition = "jsonb")
    private String filesChanged = "[]";

    @Column(name = "diff_summary", columnDefinition = "TEXT")
    private String diffSummary;

    @Column(name = "merged_at")
    private Instant mergedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public GitHubPR() {}

    public GitHubPR(Deployment deployment, Integer prNumber, String title, String author, String filesChanged, String diffSummary, Instant mergedAt) {
        this.deployment = deployment;
        this.prNumber = prNumber;
        this.title = title;
        this.author = author;
        this.filesChanged = filesChanged != null ? filesChanged : "[]";
        this.diffSummary = diffSummary;
        this.mergedAt = mergedAt;
        this.createdAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (filesChanged == null) filesChanged = "[]";
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Deployment getDeployment() { return deployment; }
    public void setDeployment(Deployment deployment) { this.deployment = deployment; }

    public Integer getPrNumber() { return prNumber; }
    public void setPrNumber(Integer prNumber) { this.prNumber = prNumber; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getFilesChanged() { return filesChanged; }
    public void setFilesChanged(String filesChanged) { this.filesChanged = filesChanged; }

    public String getDiffSummary() { return diffSummary; }
    public void setDiffSummary(String diffSummary) { this.diffSummary = diffSummary; }

    public Instant getMergedAt() { return mergedAt; }
    public void setMergedAt(Instant mergedAt) { this.mergedAt = mergedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
