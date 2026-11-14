package com.roottrace.domain.repository;

import com.roottrace.domain.model.IncidentNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IncidentNoteRepository extends JpaRepository<IncidentNote, UUID> {
    List<IncidentNote> findByIncidentIdOrderByCreatedAtAsc(UUID incidentId);
}
