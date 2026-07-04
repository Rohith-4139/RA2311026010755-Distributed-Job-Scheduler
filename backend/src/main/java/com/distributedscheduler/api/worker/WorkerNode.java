package com.distributedscheduler.api.worker;

import com.distributedscheduler.api.domain.*;
import com.distributedscheduler.api.repository.DeadLetterQueueEntryRepository;
import com.distributedscheduler.api.repository.JobExecutionRepository;
import com.distributedscheduler.api.repository.JobLogRepository;
import com.distributedscheduler.api.repository.JobRepository;
import com.distributedscheduler.api.repository.RetryPolicyRepository;
import com.distributedscheduler.api.service.AiSummaryService;
import com.distributedscheduler.api.service.JobService;
import com.distributedscheduler.api.service.QueueService;
import com.distributedscheduler.api.service.WorkerService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class WorkerNode {

    private static final Logger log = LoggerFactory.getLogger(WorkerNode.class);

    private final String workerId = UUID.randomUUID().toString();
    private final String workerName = "Worker-" + workerId.substring(0, 8);

    private final WorkerService workerService;
    private final QueueService queueService;
    private final JobService jobService;
    private final JobHandler jobHandler;
    private final RetryPolicyRepository retryPolicyRepository;
    private final JobRepository jobRepository;
    private final JobExecutionRepository jobExecutionRepository;
    private final JobLogRepository jobLogRepository;
    private final DeadLetterQueueEntryRepository dlqRepository;
    private final AiSummaryService aiSummaryService;
    private final TransactionTemplate transactionTemplate;

    private ExecutorService executorService;

    @Value("${scheduler.worker.enabled:true}")
    private boolean enabled;

    @Value("${scheduler.claim-batch-size:10}")
    private int batchSize;

    public WorkerNode(WorkerService workerService, QueueService queueService, JobService jobService,
                      JobHandler jobHandler, RetryPolicyRepository retryPolicyRepository,
                      JobRepository jobRepository, JobExecutionRepository jobExecutionRepository,
                      JobLogRepository jobLogRepository, DeadLetterQueueEntryRepository dlqRepository,
                      AiSummaryService aiSummaryService,
                      TransactionTemplate transactionTemplate) {
        this.workerService = workerService;
        this.queueService = queueService;
        this.jobService = jobService;
        this.jobHandler = jobHandler;
        this.retryPolicyRepository = retryPolicyRepository;
        this.jobRepository = jobRepository;
        this.jobExecutionRepository = jobExecutionRepository;
        this.jobLogRepository = jobLogRepository;
        this.dlqRepository = dlqRepository;
        this.aiSummaryService = aiSummaryService;
        this.transactionTemplate = transactionTemplate;
    }

    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("Worker execution node is disabled by configuration.");
            return;
        }

        // Initialize local executor service
        this.executorService = Executors.newFixedThreadPool(20);

        // Register worker
        workerService.registerWorker(workerId, workerName);
    }

    @PreDestroy
    public void destroy() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    @Scheduled(fixedDelayString = "${scheduler.worker.heartbeat-interval-ms:5000}")
    public void sendHeartbeat() {
        if (!enabled) return;
        try {
            workerService.pingHeartbeat(workerId);
        } catch (Exception e) {
            log.error("Failed to send heartbeat for worker {}: {}", workerId, e.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${scheduler.worker.poll-interval-ms:3000}")
    public void pollAndExecute() {
        if (!enabled) return;

        List<Queue> queues = queueService.getAllQueues();
        // Sort queues by priority (higher first)
        queues.sort((q1, q2) -> q2.getPriority().compareTo(q1.getPriority()));

        for (Queue queue : queues) {
            try {
                // Claim jobs from this queue atomically
                List<Job> claimedJobs = jobService.claimJobs(queue.getId(), workerId, batchSize);
                if (!claimedJobs.isEmpty()) {
                    log.info("Worker {} claimed {} jobs from queue '{}'", workerName, claimedJobs.size(), queue.getName());
                    for (Job job : claimedJobs) {
                        executorService.submit(() -> executeJob(job));
                    }
                }
            } catch (Exception e) {
                log.error("Error polling queue '{}': {}", queue.getName(), e.getMessage());
            }
        }
    }

    private void executeJob(Job job) {
        log.info("Starting execution of job ID={}", job.getId());
        LocalDateTime startedAt = LocalDateTime.now();

        // 1. Fetch active JobExecution
        JobExecution execution = transactionTemplate.execute(status -> {
            List<JobExecution> execs = jobExecutionRepository.findByJobId(job.getId());
            return execs.stream()
                    .filter(e -> "RUNNING".equals(e.getStatus()))
                    .findFirst()
                    .orElse(null);
        });

        if (execution == null) {
            log.error("No active RUNNING execution record found for job ID={}", job.getId());
            return;
        }

        try {
            // Run pluggable job handler
            jobHandler.handle(job);

            // 2. Mark complete on success
            transactionTemplate.executeWithoutResult(status -> {
                Job currentJob = jobRepository.findById(job.getId()).orElse(null);
                if (currentJob != null) {
                    currentJob.setStatus(JobStatus.COMPLETED);
                    currentJob.setCompletedAt(LocalDateTime.now());
                    jobRepository.save(currentJob);
                }

                JobExecution currentExec = jobExecutionRepository.findById(execution.getId()).orElse(null);
                if (currentExec != null) {
                    currentExec.setStatus("COMPLETED");
                    currentExec.setCompletedAt(LocalDateTime.now());
                    currentExec.setLogSummary("Execution succeeded.");
                    jobExecutionRepository.save(currentExec);

                    JobLog jobLog = JobLog.builder()
                            .jobExecution(currentExec)
                            .logContent("Job execution succeeded at " + LocalDateTime.now())
                            .build();
                    jobLogRepository.save(jobLog);
                }
            });

        } catch (Throwable t) {
            log.error("Job ID={} execution failed: {}", job.getId(), t.getMessage());
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            String stackTrace = sw.toString();

            // 3. Mark failed and calculate retry
            transactionTemplate.executeWithoutResult(status -> {
                Job currentJob = jobRepository.findById(job.getId()).orElse(null);
                if (currentJob == null) return;

                JobExecution currentExec = jobExecutionRepository.findById(execution.getId()).orElse(null);
                if (currentExec != null) {
                    currentExec.setStatus("FAILED");
                    currentExec.setCompletedAt(LocalDateTime.now());
                    currentExec.setErrorMessage(t.getMessage());
                    currentExec.setLogSummary(stackTrace.length() > 500 ? stackTrace.substring(0, 500) : stackTrace);
                    jobExecutionRepository.save(currentExec);

                    JobLog jobLog = JobLog.builder()
                            .jobExecution(currentExec)
                            .logContent("Job execution failed. Exception: " + t.getMessage())
                            .build();
                    jobLogRepository.save(jobLog);
                }

                // Check retry policy
                Optional<RetryPolicy> policyOpt = retryPolicyRepository.findByQueueId(currentJob.getQueue().getId());
                RetryPolicy policy = policyOpt.orElse(null);

                int maxRetries = policy != null ? policy.getMaxRetries() : currentJob.getMaxRetries();
                int currentRetry = currentJob.getRetryCount();

                if (currentRetry < maxRetries) {
                    // Retry linear/exponential/fixed backoff with a short default so tests and demos can observe retries quickly.
                    int delaySeconds = 1;
                    if (policy != null) {
                        int baseDelay = policy.getDelaySeconds();
                        double multiplier = policy.getBackoffMultiplier() != null ? policy.getBackoffMultiplier() : 1.0;

                        if (policy.getType() == RetryType.FIXED) {
                            delaySeconds = Math.max(1, baseDelay);
                        } else if (policy.getType() == RetryType.LINEAR) {
                            delaySeconds = Math.max(1, baseDelay * (currentRetry + 1));
                        } else if (policy.getType() == RetryType.EXPONENTIAL) {
                            delaySeconds = Math.max(1, (int) (baseDelay * Math.pow(multiplier, currentRetry)));
                        }
                    }

                    currentJob.setRetryCount(currentRetry + 1);
                    currentJob.setStatus(JobStatus.QUEUED);
                    currentJob.setScheduledAt(LocalDateTime.now().plusSeconds(delaySeconds));
                    jobRepository.save(currentJob);

                    log.info("Job ID={} scheduled for retry in {} seconds (Attempt {}/{})",
                            currentJob.getId(), delaySeconds, currentJob.getRetryCount(), maxRetries);
                } else {
                    // Retries exhausted, move to DLQ
                    currentJob.setStatus(JobStatus.DEAD_LETTER);
                    jobRepository.save(currentJob);

                    // Avoid duplicate DLQ entries for the same job (concurrent workers might both try to escalate)
                    Optional<DeadLetterQueueEntry> existing = dlqRepository.findByJobId(currentJob.getId());
                    DeadLetterQueueEntry dlqEntry;
                    if (existing.isPresent()) {
                        dlqEntry = existing.get();
                    } else {
                        dlqEntry = DeadLetterQueueEntry.builder()
                                .job(currentJob)
                                .errorMessage(t.getMessage() != null ? t.getMessage() : t.getClass().getName())
                                .build();
                        dlqEntry = dlqRepository.save(dlqEntry);
                        aiSummaryService.generateFailureSummary(dlqEntry, stackTrace);
                    }
                    log.warn("Job ID={} retries exhausted. Moved to Dead Letter Queue (DLQ)", currentJob.getId());
                }
            });
        }
    }

    public String getWorkerId() {
        return workerId;
    }

    public String getWorkerName() {
        return workerName;
    }
}
