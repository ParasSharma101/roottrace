package com.roottrace.security.service;

import com.roottrace.domain.exception.ResourceNotFoundException;
import com.roottrace.domain.model.WorkspaceIngestionKey;
import com.roottrace.domain.repository.WorkspaceIngestionKeyRepository;
import com.roottrace.domain.repository.WorkspaceMemberRepository;
import com.roottrace.domain.repository.WorkspaceRepository;
import com.roottrace.security.WorkspaceIdentity;
import com.roottrace.security.dto.CreatedIngestionKeyDto;
import com.roottrace.security.dto.IngestionKeyDto;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@Service
public class IngestionKeyService {
    private final WorkspaceIngestionKeyRepository keys;
    private final WorkspaceMemberRepository memberships;
    private final WorkspaceRepository workspaces;
    private final SecureRandom random = new SecureRandom();

    public IngestionKeyService(WorkspaceIngestionKeyRepository keys, WorkspaceMemberRepository memberships,
                               WorkspaceRepository workspaces) {
        this.keys = keys;
        this.memberships = memberships;
        this.workspaces = workspaces;
    }

    @Transactional
    public CreatedIngestionKeyDto create(WorkspaceIdentity identity, String name) {
        UUID workspace = requireAdmin(identity);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String secret = "rt_ing_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        WorkspaceIngestionKey key = keys.save(new WorkspaceIngestionKey(workspace, name.strip(), hash(secret),
                secret.substring(0, 15), identity.userId()));
        return new CreatedIngestionKeyDto(dto(key), secret);
    }

    @Transactional(readOnly = true)
    public List<IngestionKeyDto> list(WorkspaceIdentity identity) {
        return keys.findByWorkspaceIdOrderByCreatedAtDesc(requireAdmin(identity)).stream().map(this::dto).toList();
    }

    @Transactional(readOnly = true)
    public IngestionKeyDto get(WorkspaceIdentity identity, UUID id) {
        return dto(keys.findByIdAndWorkspaceId(id, requireAdmin(identity))
                .orElseThrow(() -> new ResourceNotFoundException("Key not found")));
    }

    @Transactional
    public void revoke(WorkspaceIdentity identity, UUID id) {
        UUID workspace = requireAdmin(identity);
        keys.findByIdAndWorkspaceId(id, workspace).orElseThrow(() -> new ResourceNotFoundException("Key not found"));
        keys.revoke(workspace, id, identity.userId(), Instant.now());
    }

    // Authentication resolver for the following tenant-aware ingestion increment.
    // Deliberately not wired into the old unscoped webhook path yet.
    @Transactional(readOnly = true)
    public Optional<UUID> resolveWorkspace(String secret) {
        if (secret == null || secret.isBlank() || secret.length() > 256) return Optional.empty();
        return keys.findByKeyHashAndRevokedAtIsNull(hash(secret))
                .filter(key -> workspaces.findById(key.getWorkspaceId()).filter(ws -> !ws.isLegacy()).isPresent())
                .map(WorkspaceIngestionKey::getWorkspaceId);
    }

    private UUID requireAdmin(WorkspaceIdentity identity) {
        UUID workspace = identity.requireWorkspace();
        return memberships.findByWorkspaceIdAndUserId(workspace, identity.userId())
                .filter(member -> "ROLE_ADMIN".equals(member.getRole()) && !member.getWorkspace().isLegacy())
                .map(member -> member.getWorkspace().getId())
                .orElseThrow(() -> new AccessDeniedException("Workspace administrator required"));
    }

    private IngestionKeyDto dto(WorkspaceIngestionKey key) {
        return new IngestionKeyDto(key.getId(), key.getName(), key.getKeyPrefix(), key.getCreatedAt(), key.getRevokedAt());
    }

    private static String hash(String secret) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
