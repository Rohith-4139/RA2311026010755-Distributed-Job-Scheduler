package com.distributedscheduler.api.repository;

import com.distributedscheduler.api.domain.Job;
import com.distributedscheduler.api.domain.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface JobRepository extends JpaRepository<Job, Long> {

    @Query(value = "SELECT * FROM jobs j " +
            "WHERE j.queue_id = :queueId AND j.status IN ('QUEUED', 'SCHEDULED') AND j.scheduled_at <= :now " +
            "ORDER BY j.priority DESC, j.created_at ASC " +
            "LIMIT :limitCount FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<Job> claimJobsForUpdate(@Param("queueId") Long queueId, @Param("now") LocalDateTime now, @Param("limitCount") int limitCount);

    long countByQueueIdAndStatusIn(Long queueId, List<JobStatus> statuses);

    long countByQueueIdAndStatus(Long queueId, JobStatus status);

    long countByStatus(JobStatus status);

    List<Job> findByStatus(JobStatus status);

    Page<Job> findByStatusAndQueueIdAndCreatedAtBetween(
            JobStatus status, Long queueId, LocalDateTime start, LocalDateTime end, Pageable pageable);

    Page<Job> findByStatusAndCreatedAtBetween(
            JobStatus status, LocalDateTime start, LocalDateTime end, Pageable pageable);

    Page<Job> findByQueueIdAndCreatedAtBetween(
            Long queueId, LocalDateTime start, LocalDateTime end, Pageable pageable);

    Page<Job> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);
}
