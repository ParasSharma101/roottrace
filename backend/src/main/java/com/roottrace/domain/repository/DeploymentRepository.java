package com.roottrace.domain.repository;

import com.roottrace.domain.model.Deployment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface DeploymentRepository extends JpaRepository<Deployment, UUID> {
    List<Deployment> findByServiceNameAndDeploymentTimeBetweenOrderByDeploymentTimeDesc(String serviceName, Instant start, Instant end);
    List<Deployment> findByDeploymentTimeBetweenOrderByDeploymentTimeDesc(Instant start, Instant end);
}
