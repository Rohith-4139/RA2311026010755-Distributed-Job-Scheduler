package com.distributedscheduler.api.repository;

import com.distributedscheduler.api.domain.JobLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobLogRepository extends JpaRepository<JobLog, Long> {
    List<JobLog> findByJobExecutionId(Long jobExecutionId);
}
