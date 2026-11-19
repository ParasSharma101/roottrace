package com.roottrace.domain.repository;

import com.roottrace.domain.model.Incident;
import com.roottrace.domain.model.IncidentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Incident i where i.id=:id and i.workspace.id=:workspaceId")
    java.util.Optional<Incident> lockByIdAndWorkspaceId(@Param("id") UUID id, @Param("workspaceId") UUID workspaceId);
    java.util.Optional<Incident> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
    List<Incident> findByWorkspaceIdOrderByStartTimeDesc(UUID workspaceId);
    List<Incident> findByStatusOrderByStartTimeDesc(IncidentStatus status);
    List<Incident> findAllByOrderByStartTimeDesc();

    @Query("""
        SELECT DISTINCT i FROM Incident i 
        JOIN Alert a ON a.incident = i 
        WHERE i.status IN :statuses 
          AND i.workspace.id = :workspaceId
          AND a.serviceName = :serviceName 
          AND i.startTime >= :since 
        ORDER BY i.startTime DESC
    """)
    List<Incident> findActiveIncidentsForService(
        @Param("workspaceId") UUID workspaceId,
        @Param("statuses") List<IncidentStatus> statuses,
        @Param("serviceName") String serviceName,
        @Param("since") Instant since
    );
}
