package com.roottrace.domain.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "hypotheses")
public class Hypothesis {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "confidence_score", nullable = false, precision = 3, scale = 2)
    private BigDecimal confidenceScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private HypothesisStatus status = HypothesisStatus.PENDING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_citations", nullable = false, columnDefinition = "jsonb")
    private String evidenceCitations = "[]";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Hypothesis() {}

    public Hypothesis(Incident incident, String description, BigDecimal confidenceScore, HypothesisStatus status, String evidenceCitations) {
        this.incident = incident;
        this.description = description;
        this.confidenceScore = confidenceScore;
        this.status = status != null ? status : HypothesisStatus.PENDING;
        this.evidenceCitations = evidenceCitations != null ? evidenceCitations : "[]";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
        if (evidenceCitations == null) evidenceCitations = "[]";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Incident getIncident() { return incident; }
    public void setIncident(Incident incident) { this.incident = incident; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(BigDecimal confidenceScore) { this.confidenceScore = confidenceScore; }

    public HypothesisStatus getStatus() { return status; }
    public void setStatus(HypothesisStatus status) { this.status = status; }

    public String getEvidenceCitations() { return evidenceCitations; }
    public void setEvidenceCitations(String evidenceCitations) { this.evidenceCitations = evidenceCitations; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
