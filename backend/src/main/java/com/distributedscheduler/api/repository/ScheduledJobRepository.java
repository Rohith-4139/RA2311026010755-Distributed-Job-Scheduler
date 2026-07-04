package com.distributedscheduler.api.repository;

import com.distributedscheduler.api.domain.ScheduledJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ScheduledJobRepository extends JpaRepository<ScheduledJob, Long> {
    List<ScheduledJob> findByActiveTrueAndNextRunAtBefore(LocalDateTime time);
}
