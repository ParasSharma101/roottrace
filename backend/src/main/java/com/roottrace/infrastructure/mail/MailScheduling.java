package com.roottrace.infrastructure.mail;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name={"roottrace.mail.enabled","roottrace.mail.scheduling.enabled"},havingValue="true")
public class MailScheduling {
    private final MailWorker worker;
    public MailScheduling(MailWorker worker) { this.worker=worker; }
    @Bean
    public ThreadPoolTaskScheduler mailTaskScheduler() {
        var scheduler=new ThreadPoolTaskScheduler(); scheduler.setPoolSize(1); scheduler.setThreadNamePrefix("mail-delivery-"); return scheduler;
    }
    @Scheduled(fixedDelayString="${roottrace.mail.delivery-delay-ms:1000}",scheduler="mailTaskScheduler")
    public void deliver() { for(int i=0;i<10 && worker.deliverOne();i++) { } }
}
