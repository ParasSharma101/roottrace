package com.roottrace.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workspace_ingestion_keys")
public class WorkspaceIngestionKey {
    @Id private UUID id;
    @Column(name = "workspace_id", nullable = false) private UUID workspaceId;
    @Column(nullable = false, length = 128) private String name;
    @Column(name = "key_hash", nullable = false, unique = true, length = 64) private String keyHash;
    @Column(name = "key_prefix", nullable = false, length = 16) private String keyPrefix;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "created_by_user_id") private UUID createdByUserId;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "revoked_by_user_id") private UUID revokedByUserId;

    protected WorkspaceIngestionKey() {}

    public WorkspaceIngestionKey(UUID workspaceId, String name, String keyHash, String keyPrefix, UUID createdByUserId) {
        this.id = UUID.randomUUID();
        this.workspaceId = workspaceId;
        this.name = name;
        this.keyHash = keyHash;
        this.keyPrefix = keyPrefix;
        this.createdAt = Instant.now();
        this.createdByUserId = createdByUserId;
    }

    public UUID getId() { return id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public String getName() { return name; }
    @JsonIgnore public String getKeyHash() { return keyHash; }
    public String getKeyPrefix() { return keyPrefix; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getRevokedAt() { return revokedAt; }
}
