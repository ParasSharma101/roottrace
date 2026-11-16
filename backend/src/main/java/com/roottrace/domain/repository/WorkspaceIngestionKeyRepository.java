package com.roottrace.domain.repository;

import com.roottrace.domain.model.WorkspaceIngestionKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceIngestionKeyRepository extends JpaRepository<WorkspaceIngestionKey, UUID> {
    List<WorkspaceIngestionKey> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);
    Optional<WorkspaceIngestionKey> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
    Optional<WorkspaceIngestionKey> findByKeyHashAndRevokedAtIsNull(String keyHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update WorkspaceIngestionKey k set k.revokedAt=:now, k.revokedByUserId=:actor " +
            "where k.id=:id and k.workspaceId=:workspace and k.revokedAt is null")
    int revoke(@Param("workspace") UUID workspace, @Param("id") UUID id,
               @Param("actor") UUID actor, @Param("now") Instant now);
}
