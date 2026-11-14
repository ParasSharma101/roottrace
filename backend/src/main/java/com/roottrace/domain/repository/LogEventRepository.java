package com.roottrace.domain.repository;

import com.roottrace.domain.model.LogEvent;
import com.roottrace.domain.model.LogLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface LogEventRepository extends JpaRepository<LogEvent, UUID> {
    List<LogEvent> findByServiceNameAndTimestampBetweenOrderByTimestampDesc(String serviceName, Instant start, Instant end);
    List<LogEvent> findByServiceNameAndLogLevelAndTimestampBetweenOrderByTimestampDesc(String serviceName, LogLevel logLevel, Instant start, Instant end);
    List<LogEvent> findByTimestampBetweenOrderByTimestampDesc(Instant start, Instant end);
}
