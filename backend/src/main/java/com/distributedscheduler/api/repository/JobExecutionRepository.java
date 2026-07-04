package com.distributedscheduler.api.repository;

import com.distributedscheduler.api.domain.JobExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface JobExecutionRepository extends JpaRepository<JobExecution, Long> {
    List<JobExecution> findByJobId(Long jobId);
    List<JobExecution> findByWorkerId(String workerId);
    List<JobExecution> findByWorkerIdAndStatus(String workerId, String status);
    List<JobExecution> findFirst10ByOrderByStartedAtDesc();

    @Query(value = "SELECT date_trunc('hour', completed_at)::text AS hour_bucket, COUNT(*) " +
            "FROM job_executions " +
            "WHERE status = 'COMPLETED' AND completed_at >= :since " +
            "GROUP BY hour_bucket ORDER BY hour_bucket", nativeQuery = true)
    List<Object[]> countCompletedByHourSince(@Param("since") LocalDateTime since);
}
