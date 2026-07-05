package com.distributedscheduler.api.service;

import com.distributedscheduler.api.domain.*;
import com.distributedscheduler.api.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class WorkerService {

    private static final Logger log = LoggerFactory.getLogger(WorkerService.class);

    private final WorkerRepository workerRepository;
    private final WorkerHeartbeatRepository heartbeatRepository;
    private final JobExecutionRepository jobExecutionRepository;
    private final JobRepository jobRepository;
    private final JobLogRepository jobLogRepository;
    private final DeadLetterQueueEntryRepository dlqRepository;
    private final AiSummaryService aiSummaryService;

    @Value("${scheduler.watchdog.enabled:true}")
    private boolean watchdogEnabled;

    public WorkerService(WorkerRepository workerRepository, WorkerHeartbeatRepository heartbeatRepository,
                         JobExecutionRepository jobExecutionRepository, JobRepository jobRepository,
                         JobLogRepository jobLogRepository, DeadLetterQueueEntryRepository dlqRepository,
                         AiSummaryService aiSummaryService) {
        this.workerRepository = workerRepository;
        this.heartbeatRepository = heartbeatRepository;
        this.jobExecutionRepository = jobExecutionRepository;
        this.jobRepository = jobRepository;
        this.jobLogRepository = jobLogRepository;
        this.dlqRepository = dlqRepository;
        this.aiSummaryService = aiSummaryService;
    }

    @Transactional
    public Worker registerWorker(String id, String name) {
        Worker worker = workerRepository.findById(id).orElse(null);
        if (worker == null) {
            worker = Worker.builder()
                    .id(id)
                    .name(name)
                    .status("ACTIVE")
                    .build();
        } else {
            worker.setStatus("ACTIVE");
        }
        worker = workerRepository.save(worker);

        List<WorkerHeartbeat> heartbeats = heartbeatRepository.findByWorkerId(id);
        WorkerHeartbeat heartbeat;
        if (heartbeats.isEmpty()) {
            heartbeat = WorkerHeartbeat.builder()
                    .worker(worker)
                    .lastPing(LocalDateTime.now())
                    .build();
        } else {
            heartbeat = heartbeats.stream()
                    .max(Comparator.comparing(WorkerHeartbeat::getLastPing))
                    .orElseThrow();
            if (heartbeats.size() > 1) {
                heartbeats.remove(heartbeat);
                heartbeatRepository.deleteAll(heartbeats);
            }
            heartbeat.setWorker(worker);
            heartbeat.setLastPing(LocalDateTime.now());
        }
        heartbeatRepository.save(heartbeat);

        log.info("Worker registered/re-activated: id={}, name={}", id, name);
        return worker;
    }

    @Transactional
    public void pingHeartbeat(String workerId) {
        List<WorkerHeartbeat> heartbeats = heartbeatRepository.findByWorkerId(workerId);
        if (heartbeats.isEmpty()) {
            throw new IllegalArgumentException("Worker not registered");
        }

        WorkerHeartbeat heartbeat = heartbeats.stream()
                .max(Comparator.comparing(WorkerHeartbeat::getLastPing))
                .orElseThrow();
        if (heartbeats.size() > 1) {
            heartbeats.remove(heartbeat);
            heartbeatRepository.deleteAll(heartbeats);
        }

        heartbeat.setLastPing(LocalDateTime.now());
        heartbeatRepository.save(heartbeat);

        // Also ensure worker is marked active if it was dead/inactive
        Worker worker = heartbeat.getWorker();
        if (!"ACTIVE".equals(worker.getStatus())) {
            worker.setStatus("ACTIVE");
            workerRepository.save(worker);
        }
    }

    @Scheduled(fixedDelayString = "${scheduler.watchdog-interval-ms:10000}")
    @Transactional
    public void watchdogCheck() {
        if (!watchdogEnabled) {
            return;
        }

        LocalDateTime threshold = LocalDateTime.now().minusSeconds(30);
        List<WorkerHeartbeat> staleHeartbeats = heartbeatRepository.findByLastPingBefore(threshold);

        for (WorkerHeartbeat hb : staleHeartbeats) {
            Worker worker = hb.getWorker();
            if ("ACTIVE".equals(worker.getStatus())) {
                log.warn("Watchdog detected stale worker: id={}, lastPing={}", worker.getId(), hb.getLastPing());
                worker.setStatus("DEAD");
                workerRepository.save(worker);

                // Recover jobs running on this worker
                List<JobExecution> activeExecutions = jobExecutionRepository.findByWorkerIdAndStatus(worker.getId(), "RUNNING");
                for (JobExecution exec : activeExecutions) {
                    exec.setStatus("FAILED");
                    exec.setCompletedAt(LocalDateTime.now());
                    exec.setErrorMessage("Worker heartbeat lost. Assumed dead.");
                    jobExecutionRepository.save(exec);

                    JobLog jobLog = JobLog.builder()
                            .jobExecution(exec)
                            .logContent("Worker heartbeat lost. Job execution aborted.")
                            .build();
                    jobLogRepository.save(jobLog);

                    Job job = exec.getJob();
                    if (job != null && (job.getStatus() == JobStatus.RUNNING || job.getStatus() == JobStatus.CLAIMED)) {
                        if (job.getRetryCount() < job.getMaxRetries()) {
                            job.setRetryCount(job.getRetryCount() + 1);
                            job.setStatus(JobStatus.QUEUED);
                            job.setScheduledAt(LocalDateTime.now());
                            jobRepository.save(job);
                            log.info("Job ID={} rescheduled for retry. Attempt {}", job.getId(), job.getRetryCount());
                        } else {
                            job.setStatus(JobStatus.DEAD_LETTER);
                            jobRepository.save(job);

                            // Avoid creating duplicate DLQ entries for the same job
                            Optional<DeadLetterQueueEntry> existing = dlqRepository.findByJobId(job.getId());
                            DeadLetterQueueEntry dlqEntry;
                            if (existing.isPresent()) {
                                dlqEntry = existing.get();
                            } else {
                                dlqEntry = DeadLetterQueueEntry.builder()
                                        .job(job)
                                        .errorMessage("Worker heartbeat lost. Retries exhausted.")
                                        .build();
                                dlqEntry = dlqRepository.save(dlqEntry);
                                // Trigger AI summary generation asynchronously
                                aiSummaryService.generateFailureSummary(dlqEntry, "Worker heartbeat lost. Retries exhausted.");
                            }

                            log.warn("Job ID={} moved to Dead Letter Queue (DLQ). Retries exhausted.", job.getId());
                        }
                    }
                }
            }
        }
    }

    public List<Worker> getAllWorkers() {
        return workerRepository.findAll();
    }
}
