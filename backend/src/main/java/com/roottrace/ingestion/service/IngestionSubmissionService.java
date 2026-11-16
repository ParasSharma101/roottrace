package com.roottrace.ingestion.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roottrace.infrastructure.jobs.DurableTaskStore;
import com.roottrace.ingestion.dto.IngestionResponse;
import com.roottrace.domain.exception.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
public class IngestionSubmissionService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final IngestionEventCodec codec;
    private final DurableTaskStore tasks;
    public IngestionSubmissionService(JdbcTemplate jdbc, ObjectMapper mapper, IngestionEventCodec codec, DurableTaskStore tasks) {
        this.jdbc = jdbc; this.mapper = mapper; this.codec = codec; this.tasks = tasks;
    }

    @Transactional(rollbackFor = Exception.class)
    public IngestionResponse submit(UUID workspace, String kind, String idempotencyKey, List<Event> events) throws JsonProcessingException {
        if (idempotencyKey != null && (idempotencyKey.isEmpty() || idempotencyKey.length()>128
                || idempotencyKey.chars().anyMatch(c -> c<33 || c>126))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key must contain 1-128 visible ASCII characters");
        }
        if (events.isEmpty() || events.size()>500) throw new IllegalArgumentException("Invalid event count");
        UUID id = UUID.randomUUID();
        String keyHash = idempotencyKey == null ? null : PayloadDigest.hash(idempotencyKey);
        String bodyHash = PayloadDigest.canonicalHash(mapper, events);
        int inserted = jdbc.update("""
            insert into ingestion_submissions(id,workspace_id,request_kind,idempotency_hash,payload_hash,item_count)
            values (?,?,?,?,?,?) on conflict (workspace_id,request_kind,idempotency_hash) do nothing
            """, id, workspace, kind, keyHash, bodyHash, events.size());
        if (inserted == 0) {
            var existing = jdbc.queryForMap("select id,payload_hash,item_count,received_at from ingestion_submissions where workspace_id=? and request_kind=? and idempotency_hash=?",
                    workspace, kind, keyHash);
            if (!bodyHash.equals(existing.get("payload_hash"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency-Key was used with a different payload");
            return response(existing);
        }
        for (Event event : events) {
            UUID eventId = UUID.randomUUID();
            tasks.enqueuePublish(eventId, workspace, id, event.topic(), workspace + ":" + event.service(),
                    codec.encode(workspace, event.type(), eventId, event.payload()));
        }
        return response(jdbc.queryForMap("select id,item_count,received_at from ingestion_submissions where id=?", id));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> status(UUID workspace, UUID id) {
        var submissions = jdbc.queryForList("select id,item_count,received_at from ingestion_submissions where id=? and workspace_id=?", id, workspace);
        if (submissions.isEmpty()) throw new ResourceNotFoundException("Ingestion request not found");
        int count = ((Number)submissions.getFirst().get("item_count")).intValue();
        var counts = jdbc.queryForMap("""
            select count(*) filter (where t.status='SUCCEEDED') as published,
                   count(*) filter (where t.status='FAILED') as failed,
                   count(r.event_id) as processed
            from durable_tasks t left join ingestion_receipts r on r.workspace_id=t.workspace_id and r.event_id=t.id
            where t.workspace_id=? and t.submission_id=?
            """, workspace, id);
        long published = ((Number)counts.get("published")).longValue();
        long failed = ((Number)counts.get("failed")).longValue();
        long processed = ((Number)counts.get("processed")).longValue();
        String status = processed==count ? "PROCESSED" : failed>0 ? "FAILED" : published==count ? "PUBLISHED" : "PENDING";
        return Map.of("trackingId", id, "count", count, "published", published, "processed", processed, "failed", failed, "status", status);
    }

    private IngestionResponse response(Map<String, Object> row) {
        return new IngestionResponse("ACCEPTED", (UUID)row.get("id"), ((Number)row.get("item_count")).intValue(),
                ((java.sql.Timestamp)row.get("received_at")).toInstant());
    }
    public record Event(String topic, String type, String service, Object payload) {}
}
