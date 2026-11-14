package com.roottrace.domain.repository;

import com.roottrace.domain.model.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AlertRepository extends JpaRepository<Alert, UUID> {
    List<Alert> findByIncidentIdOrderByTriggeredAtDesc(UUID incidentId);
    List<Alert> findByServiceNameAndTriggeredAtBetweenOrderByTriggeredAtDesc(String serviceName, Instant start, Instant end);
}
