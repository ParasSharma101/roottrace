package com.roottrace.security;

import com.roottrace.infrastructure.mail.MailScheduling;
import com.roottrace.infrastructure.mail.MailWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

class MailSchedulingTest {
    @Test
    void automaticDeliveryRunsOnDedicatedSchedulerOnlyWhenEnabled() {
        MailWorker worker=mock(MailWorker.class);
        AtomicReference<String> thread=new AtomicReference<>();
        when(worker.deliverOne()).thenAnswer(call -> { thread.set(Thread.currentThread().getName()); return false; });
        var runner=new ApplicationContextRunner().withUserConfiguration(MailScheduling.class)
                .withBean(com.roottrace.security.service.RecoveryRequestWorker.class,() -> mock(com.roottrace.security.service.RecoveryRequestWorker.class))
                .withBean(MailWorker.class,() -> worker);
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(MailScheduling.class);
            verifyNoInteractions(worker);
        });
        runner.withPropertyValues("roottrace.mail.enabled=true","roottrace.mail.scheduling.enabled=true",
                "roottrace.mail.delivery-delay-ms=20").run(context -> {
            assertThat(context).hasNotFailed();
            await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(thread.get()).startsWith("mail-delivery-"));
        });
    }
}
