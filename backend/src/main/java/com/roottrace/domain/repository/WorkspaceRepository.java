package com.roottrace.domain.repository;

import com.roottrace.domain.model.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {
    Optional<Workspace> findByApiKey(String apiKey);
    Optional<Workspace> findBySlug(String slug);
    boolean existsBySlug(String slug);
    boolean existsByApiKey(String apiKey);
}
