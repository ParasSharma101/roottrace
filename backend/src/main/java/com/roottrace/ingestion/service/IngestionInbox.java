package com.roottrace.ingestion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class IngestionInbox {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public IngestionInbox(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstDelivery(IngestionEventCodec.Decoded decoded, String type, ConsumerRecord<String,String> record) {
        UUID id = decoded.eventId() != null ? decoded.eventId() : UUID.nameUUIDFromBytes(
                (record.topic() + ":" + record.partition() + ":" + record.offset()).getBytes(StandardCharsets.UTF_8));
        String hash = PayloadDigest.canonicalHash(mapper, decoded.payload());
        int inserted = jdbc.update("insert into ingestion_receipts(workspace_id,event_id,event_type,payload_hash) values (?,?,?,?) on conflict do nothing",
                decoded.workspaceId(), id, type, hash);
        if (inserted == 1) return true;
        var existing = jdbc.queryForMap("select event_type,payload_hash from ingestion_receipts where workspace_id=? and event_id=?", decoded.workspaceId(), id);
        if (!type.equals(existing.get("event_type")) || !hash.equals(existing.get("payload_hash"))) {
            throw new IllegalArgumentException("Event ID reused with different content");
        }
        return false;
    }
}
