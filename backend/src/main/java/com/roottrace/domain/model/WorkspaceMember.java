package com.roottrace.domain.model;

import com.roottrace.security.UserRole;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "workspace_members",
        uniqueConstraints = @UniqueConstraint(name = "uq_workspace_member", columnNames = {"workspace_id", "user_id"})
)
public class WorkspaceMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "role", nullable = false, length = 32)
    private String role = UserRole.ROLE_SRE.name();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Version
    @Column(name="version",nullable=false)
    private long version;
    public long getVersion() { return version; }

    public WorkspaceMember() {}

    public WorkspaceMember(Workspace workspace, User user, String role) {
        this.workspace = workspace;
        this.user = user;
        this.role = role != null ? role : UserRole.ROLE_SRE.name();
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Workspace getWorkspace() {
        return workspace;
    }

    public void setWorkspace(Workspace workspace) {
        this.workspace = workspace;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
