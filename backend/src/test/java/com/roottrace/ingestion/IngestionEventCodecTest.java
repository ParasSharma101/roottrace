package com.roottrace.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roottrace.domain.model.Workspace;
import com.roottrace.domain.repository.WorkspaceRepository;
import com.roottrace.ingestion.service.IngestionEventCodec;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IngestionEventCodecTest {
    private final WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final IngestionEventCodec codec = new IngestionEventCodec(mapper, workspaces);

    @Test
    void envelopeMustHaveSupportedVersionTypeAndExistingOwner() throws Exception {
        UUID workspace = UUID.randomUUID();
        when(workspaces.existsById(workspace)).thenReturn(true);
        String json = codec.encode(workspace, "LOG", Map.of("workspaceId", UUID.randomUUID().toString()));
        assertThat(codec.decode(json, "LOG").workspaceId()).isEqualTo(workspace);
        for (String invalid : new String[]{json.replace("\"eventVersion\":1", "\"eventVersion\":2"),
                json.replace("\"eventVersion\":1", "\"eventVersion\":4294967297"),
                json.replace("\"LOG\"", "\"ALERT\""),
                json.replace(workspace.toString(), UUID.randomUUID().toString()),
                json.replace(workspace.toString(), "invalid"), "{\"eventVersion\":1}", "null", "[]"}) {
            assertThatThrownBy(() -> codec.decode(invalid, "LOG")).isInstanceOf(IllegalArgumentException.class);
        }
        // Transient database errors must not be classified as malformed events.
        when(workspaces.existsById(workspace)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("unavailable"));
        assertThatThrownBy(() -> codec.decode(json, "LOG")).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test
    void historicalPayloadOwnershipIsAlwaysQuarantined() throws Exception {
        var decoded = codec.decode(mapper.writeValueAsString(Map.of("workspaceId", UUID.randomUUID().toString(), "serviceName", "shared")), "LOG");
        assertThat(decoded.workspaceId()).isEqualTo(Workspace.LEGACY_WORKSPACE_ID);
        verifyNoInteractions(workspaces);
    }
}
