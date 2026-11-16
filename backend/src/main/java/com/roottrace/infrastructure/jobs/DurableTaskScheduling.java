package com.roottrace.infrastructure.jobs;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "roottrace.jobs.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class DurableTaskScheduling {
    private final DurableTaskWorker worker;
    public DurableTaskScheduling(DurableTaskWorker worker) { this.worker = worker; }

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("durable-worker-");
        return scheduler;
    }

    @Scheduled(fixedDelayString = "${roottrace.jobs.publish-delay-ms:500}")
    public void publish() { for (int i = 0; i < 20 && worker.publishOne(); i++) { /* bounded work per poll */ } }

    @Scheduled(fixedDelayString = "${roottrace.jobs.investigation-delay-ms:1000}")
    public void investigate() { worker.investigateOne(); }
}
