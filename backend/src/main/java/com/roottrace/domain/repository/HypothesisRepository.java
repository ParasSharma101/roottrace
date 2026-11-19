package com.roottrace.domain.repository;

import com.roottrace.domain.model.Hypothesis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HypothesisRepository extends JpaRepository<Hypothesis, UUID> {
    java.util.Optional<Hypothesis> findByIdAndIncidentId(UUID id, UUID incidentId);
    List<Hypothesis> findByIncidentIdOrderByConfidenceScoreDesc(UUID incidentId);
}
