package com.distributedscheduler.api.service;

import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import com.distributedscheduler.api.domain.*;
import com.distributedscheduler.api.dto.JobRequest;
import com.distributedscheduler.api.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class JobService {

    private final JobRepository jobRepository;
    private final QueueRepository queueRepository;
    private final ScheduledJobRepository scheduledJobRepository;
    private final DeadLetterQueueEntryRepository dlqRepository;
    private final JobExecutionRepository jobExecutionRepository;
    private final JobLogRepository jobLogRepository;

    public JobService(JobRepository jobRepository, QueueRepository queueRepository,
                      ScheduledJobRepository scheduledJobRepository, DeadLetterQueueEntryRepository dlqRepository,
                      JobExecutionRepository jobExecutionRepository, JobLogRepository jobLogRepository) {
        this.jobRepository = jobRepository;
        this.queueRepository = queueRepository;
        this.scheduledJobRepository = scheduledJobRepository;
        this.dlqRepository = dlqRepository;
        this.jobExecutionRepository = jobExecutionRepository;
        this.jobLogRepository = jobLogRepository;
    }

    @Transactional
    public Object createJob(Long queueId, JobRequest request) {
        Queue queue = queueRepository.findById(queueId)
                .orElseThrow(() -> new IllegalArgumentException("Queue not found"));

        if (request.getCronExpression() != null && !request.getCronExpression().isBlank()) {
            LocalDateTime nextRun = calculateNextRun(request.getCronExpression());
            ScheduledJob sj = ScheduledJob.builder()
                    .queue(queue)
                    .cronExpression(request.getCronExpression())
                    .payload(request.getPayload())
                    .nextRunAt(nextRun)
                    .active(true)
                    .build();
            return scheduledJobRepository.save(sj);
        } else {
            LocalDateTime scheduledAt = LocalDateTime.now();
            JobStatus status = JobStatus.QUEUED;

            if (request.getDelaySeconds() != null && request.getDelaySeconds() > 0) {
                scheduledAt = LocalDateTime.now().plusSeconds(request.getDelaySeconds());
                status = JobStatus.SCHEDULED;
            }

            Job job = Job.builder()
                    .queue(queue)
                    .payload(request.getPayload())
                    .priority(request.getPriority() != null ? request.getPriority() : 1)
                    .maxRetries(request.getMaxRetries() != null ? request.getMaxRetries() : 3)
                    .retryCount(0)
                    .scheduledAt(scheduledAt)
                    .status(status)
                    .build();

            return jobRepository.save(job);
        }
    }

    @Transactional
    public List<Job> createJobBatch(Long queueId, List<JobRequest> requests) {
        Queue queue = queueRepository.findById(queueId)
                .orElseThrow(() -> new IllegalArgumentException("Queue not found"));

        List<Job> jobs = requests.stream().map(req -> {
            LocalDateTime scheduledAt = LocalDateTime.now();
            JobStatus status = JobStatus.QUEUED;

            if (req.getDelaySeconds() != null && req.getDelaySeconds() > 0) {
                scheduledAt = LocalDateTime.now().plusSeconds(req.getDelaySeconds());
                status = JobStatus.SCHEDULED;
            }

            return Job.builder()
                    .queue(queue)
                    .payload(req.getPayload())
                    .priority(req.getPriority() != null ? req.getPriority() : 1)
                    .maxRetries(req.getMaxRetries() != null ? req.getMaxRetries() : 3)
                    .retryCount(0)
                    .scheduledAt(scheduledAt)
                    .status(status)
                    .build();
        }).collect(Collectors.toList());

        return jobRepository.saveAll(jobs);
    }

    @Transactional
    public Job retryDlqJob(Long dlqEntryId) {
        DeadLetterQueueEntry dlqEntry = dlqRepository.findById(dlqEntryId)
                .orElseThrow(() -> new IllegalArgumentException("DLQ Entry not found"));

        Job originalJob = dlqEntry.getJob();
        if (originalJob == null) {
            throw new IllegalArgumentException("Original job was deleted, cannot retry");
        }

        originalJob.setStatus(JobStatus.QUEUED);
        originalJob.setRetryCount(0);
        originalJob.setScheduledAt(LocalDateTime.now());
        Job updatedJob = jobRepository.save(originalJob);

        dlqRepository.delete(dlqEntry);
        return updatedJob;
    }

    @Transactional
    public List<Job> claimJobs(Long queueId, String workerId, int limit) {
        // Lock Queue first to prevent race conditions on concurrency limit check
        Queue queue = queueRepository.findByIdForUpdate(queueId)
                .orElseThrow(() -> new IllegalArgumentException("Queue not found"));

        if (queue.getPaused()) {
            return Collections.emptyList();
        }

        // Count running jobs in the queue
        long runningJobsCount = jobRepository.countByQueueIdAndStatusIn(
                queueId, Collections.singletonList(JobStatus.RUNNING));

        int availableCapacity = queue.getConcurrencyLimit() - (int) runningJobsCount;
        if (availableCapacity <= 0) {
            return Collections.emptyList();
        }

        int claimLimit = Math.min(limit, availableCapacity);
        if (claimLimit <= 0) {
            return Collections.emptyList();
        }

        // Fetch eligible jobs using SKIP LOCKED
        List<Job> eligibleJobs = jobRepository.claimJobsForUpdate(queueId, LocalDateTime.now(), claimLimit);
        for (Job job : eligibleJobs) {
            job.setStatus(JobStatus.RUNNING); // Transition straight to RUNNING (or CLAIMED then RUNNING)
            jobRepository.save(job);

            // Record execution start
            JobExecution execution = JobExecution.builder()
                    .job(job)
                    .workerId(workerId)
                    .status("RUNNING")
                    .build();
            jobExecutionRepository.save(execution);
        }

        return eligibleJobs;
    }

    public Page<Job> getJobsFiltered(Long queueId, JobStatus status, LocalDateTime start, LocalDateTime end, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        LocalDateTime from = start != null ? start : LocalDateTime.now().minusDays(7);
        LocalDateTime to = end != null ? end : LocalDateTime.now().plusDays(1);

        if (queueId != null && status != null) {
            return jobRepository.findByStatusAndQueueIdAndCreatedAtBetween(status, queueId, from, to, pageable);
        } else if (queueId != null) {
            return jobRepository.findByQueueIdAndCreatedAtBetween(queueId, from, to, pageable);
        } else if (status != null) {
            return jobRepository.findByStatusAndCreatedAtBetween(status, from, to, pageable);
        } else {
            return jobRepository.findByCreatedAtBetween(from, to, pageable);
        }
    }

    public Page<DeadLetterQueueEntry> getDlqEntries(int page, int size) {
        return dlqRepository.findAllByOrderByFailedAtDesc(PageRequest.of(page, size));
    }

    public List<JobExecution> getActiveExecutionsForWorker(String workerId) {
        return jobExecutionRepository.findByWorkerIdAndStatus(workerId, "RUNNING");
    }

    public LocalDateTime calculateNextRun(String cronExpression) {
        try {
            CronParser parser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.SPRING));
            ExecutionTime executionTime = ExecutionTime.forCron(parser.parse(cronExpression));
            return executionTime.nextExecution(ZonedDateTime.now(ZoneId.systemDefault()))
                    .map(zdt -> zdt.toLocalDateTime())
                    .orElse(LocalDateTime.now().plusHours(1));
        } catch (Exception e) {
            try {
                CronParser parser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));
                ExecutionTime executionTime = ExecutionTime.forCron(parser.parse(cronExpression));
                return executionTime.nextExecution(ZonedDateTime.now(ZoneId.systemDefault()))
                        .map(zdt -> zdt.toLocalDateTime())
                        .orElse(LocalDateTime.now().plusHours(1));
            } catch (Exception ex) {
                throw new IllegalArgumentException("Invalid cron expression: " + cronExpression, ex);
            }
        }
    }
}
