package com.roottrace.domain.repository;

import com.roottrace.domain.model.LogEvent;
import com.roottrace.domain.model.LogLevel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LogEventRepository extends JpaRepository<LogEvent, UUID> {
    List<LogEvent> findByServiceNameAndTimestampBetweenOrderByTimestampDesc(String serviceName, Instant start, Instant end);
    List<LogEvent> findByServiceNameAndLogLevelAndTimestampBetweenOrderByTimestampDesc(String serviceName, LogLevel logLevel, Instant start, Instant end);
    List<LogEvent> findByTimestampBetweenOrderByTimestampDesc(Instant start, Instant end);
}
