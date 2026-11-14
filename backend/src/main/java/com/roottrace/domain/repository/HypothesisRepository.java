package com.roottrace.domain.repository;

import com.roottrace.domain.model.Hypothesis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface HypothesisRepository extends JpaRepository<Hypothesis, UUID> {
    List<Hypothesis> findByIncidentIdOrderByConfidenceScoreDesc(UUID incidentId);
}
