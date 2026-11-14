package com.roottrace.domain.repository;

import com.roottrace.domain.model.Incident;
import com.roottrace.domain.model.IncidentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, UUID> {
    List<Incident> findByStatusOrderByStartTimeDesc(IncidentStatus status);
    List<Incident> findAllByOrderByStartTimeDesc();
}
