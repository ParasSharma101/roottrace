package com.roottrace.domain.repository;

import com.roottrace.domain.model.GitHubPR;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GitHubPRRepository extends JpaRepository<GitHubPR, UUID> {
    List<GitHubPR> findByDeploymentId(UUID deploymentId);
}
