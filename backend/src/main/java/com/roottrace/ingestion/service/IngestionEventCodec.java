package com.roottrace.ingestion.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roottrace.domain.model.Workspace;
import com.roottrace.domain.repository.WorkspaceRepository;
import org.springframework.stereotype.Component;
import java.util.UUID;

/** Broker metadata is trusted only from application producers on a restricted broker. */
@Component
public class IngestionEventCodec {
    private final ObjectMapper mapper;
    private final WorkspaceRepository workspaces;

    public IngestionEventCodec(ObjectMapper mapper, WorkspaceRepository workspaces) {
        this.mapper = mapper;
        this.workspaces = workspaces;
    }

    public String encode(UUID workspaceId, String eventType, Object payload) throws JsonProcessingException {
        return mapper.writeValueAsString(new Envelope(1, eventType, workspaceId, mapper.valueToTree(payload)));
    }

    public String encode(UUID workspaceId, String eventType, UUID eventId, Object payload) throws JsonProcessingException {
        return mapper.writeValueAsString(new EnvelopeV2(2, eventType, workspaceId, eventId, mapper.valueToTree(payload)));
    }

    public Decoded decode(String json, String expectedType) throws JsonProcessingException {
        JsonNode root = mapper.readTree(json);
        if (root == null || !root.isObject()) throw new IllegalArgumentException("Invalid event object");
        if (!root.has("eventVersion")) {
            // Historical raw payload workspace fields never establish tenant ownership.
            return new Decoded(Workspace.LEGACY_WORKSPACE_ID, null, root);
        }
        if (!root.path("eventVersion").isIntegralNumber() || !root.path("eventVersion").canConvertToInt()
                || (root.path("eventVersion").intValue() != 1 && root.path("eventVersion").intValue() != 2)
                || !expectedType.equals(root.path("eventType").asText()) || !root.path("payload").isObject()) {
            throw new IllegalArgumentException("Unsupported ingestion envelope");
        }
        UUID workspaceId = UUID.fromString(root.path("workspaceId").asText());
        if (!workspaces.existsById(workspaceId)) throw new IllegalArgumentException("Unknown event workspace");
        UUID eventId = root.path("eventVersion").intValue() == 2 ? UUID.fromString(root.path("eventId").asText()) : null;
        return new Decoded(workspaceId, eventId, root.get("payload"));
    }

    private record Envelope(int eventVersion, String eventType, UUID workspaceId, JsonNode payload) {}
    private record EnvelopeV2(int eventVersion, String eventType, UUID workspaceId, UUID eventId, JsonNode payload) {}
    public record Decoded(UUID workspaceId, UUID eventId, JsonNode payload) {}
}
