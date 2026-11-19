package com.roottrace.ingestion;

import com.roottrace.infrastructure.jobs.DurableTaskScheduling;
import com.roottrace.infrastructure.jobs.DurableTaskWorker;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DurableTaskSchedulingTest {
    @Test
    void defaultEnabledSchedulerDrivesBothQueues() {
        var worker = mock(DurableTaskWorker.class);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.setEnvironment(new MockEnvironment().withProperty("roottrace.jobs.publish-delay-ms","10")
                    .withProperty("roottrace.jobs.investigation-delay-ms","10"));
            context.registerBean(DurableTaskWorker.class, () -> worker);
            context.register(DurableTaskScheduling.class);
            context.refresh();
            verify(worker, timeout(2000).atLeastOnce()).publishOne();
            verify(worker, timeout(2000).atLeastOnce()).investigateOne();
            assertThat(context.getBean(ThreadPoolTaskScheduler.class).getPoolSize()).isEqualTo(2);
        }
    }

    @Test
    void disablingSchedulingLeavesQueueWorkersUndispatched() {
        var worker = mock(DurableTaskWorker.class);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.setEnvironment(new MockEnvironment().withProperty("roottrace.jobs.scheduling.enabled","false"));
            context.registerBean(DurableTaskWorker.class, () -> worker);
            context.register(DurableTaskScheduling.class);
            context.refresh();
            assertThat(context.getBeansOfType(ThreadPoolTaskScheduler.class)).isEmpty();
            verifyNoInteractions(worker);
        }
    }
}
