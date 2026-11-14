package com.roottrace.domain.repository;

import com.roottrace.domain.model.Incident;
import com.roottrace.domain.model.IncidentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, UUID> {
    List<Incident> findByStatusOrderByStartTimeDesc(IncidentStatus status);
    List<Incident> findAllByOrderByStartTimeDesc();

    @Query("""
        SELECT DISTINCT i FROM Incident i 
        JOIN Alert a ON a.incident = i 
        WHERE i.status IN :statuses 
          AND a.serviceName = :serviceName 
          AND i.startTime >= :since 
        ORDER BY i.startTime DESC
    """)
    List<Incident> findActiveIncidentsForService(
        @Param("statuses") List<IncidentStatus> statuses,
        @Param("serviceName") String serviceName,
        @Param("since") Instant since
    );
}
