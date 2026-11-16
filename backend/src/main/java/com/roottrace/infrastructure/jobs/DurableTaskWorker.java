package com.roottrace.infrastructure.jobs;

import com.roottrace.ai.service.AiInvestigationService;
import com.roottrace.infrastructure.kafka.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import java.util.concurrent.TimeUnit;

@Service
public class DurableTaskWorker {
    private static final Logger log = LoggerFactory.getLogger(DurableTaskWorker.class);
    private final DurableTaskStore tasks;
    private final EventPublisher publisher;
    private final AiInvestigationService investigations;
    private final TransactionTemplate transaction;

    public DurableTaskWorker(DurableTaskStore tasks, EventPublisher publisher, AiInvestigationService investigations,
                             PlatformTransactionManager manager) {
        this.tasks = tasks;
        this.publisher = publisher;
        this.investigations = investigations;
        this.transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public boolean publishOne() {
        var claimed = tasks.claim("PUBLISH");
        if (claimed.isEmpty()) return false;
        var task = claimed.get();
        try {
            publisher.publish(task.topic(), task.key(), task.payload()).get(10, TimeUnit.SECONDS);
            tasks.completePublish(task);
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            tasks.fail(task, ex);
            log.warn("Publication task {} failed on attempt {}", task.id(), task.attempts());
        }
        return true;
    }

    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public boolean investigateOne() {
        var claimed = tasks.claim("INVESTIGATE");
        if (claimed.isEmpty()) return false;
        var task = claimed.get();
        try {
            // Context read and provider call finish before the result-write transaction begins.
            var result = investigations.evaluate(task.workspaceId(), task.incidentId());
            transaction.executeWithoutResult(status -> {
                if (!tasks.lockCurrentClaim(task)) return;
                investigations.applyResult(task.workspaceId(), task.incidentId(), result);
                tasks.complete(task);
            });
        } catch (Exception ex) {
            tasks.fail(task, ex);
            log.warn("Investigation task {} failed on attempt {}", task.id(), task.attempts());
        }
        return true;
    }
}
