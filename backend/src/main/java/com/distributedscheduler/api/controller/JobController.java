package com.distributedscheduler.api.controller;

import com.distributedscheduler.api.domain.DeadLetterQueueEntry;
import com.distributedscheduler.api.domain.Job;
import com.distributedscheduler.api.domain.JobStatus;
import com.distributedscheduler.api.dto.JobRequest;
import com.distributedscheduler.api.repository.JobRepository;
import com.distributedscheduler.api.repository.QueueRepository;
import com.distributedscheduler.api.repository.WorkerRepository;
import com.distributedscheduler.api.service.JobService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    private final JobService jobService;
    private final JobRepository jobRepository;
    private final QueueRepository queueRepository;
    private final WorkerRepository workerRepository;

    public JobController(JobService jobService, JobRepository jobRepository,
                         QueueRepository queueRepository, WorkerRepository workerRepository) {
        this.jobService = jobService;
        this.jobRepository = jobRepository;
        this.queueRepository = queueRepository;
        this.workerRepository = workerRepository;
    }

    @PostMapping("/queue/{queueId}")
    public ResponseEntity<?> createJob(@PathVariable Long queueId, @Valid @RequestBody JobRequest request) {
        Object result = jobService.createJob(queueId, request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/queue/{queueId}/batch")
    public ResponseEntity<List<Job>> createJobBatch(@PathVariable Long queueId, @Valid @RequestBody List<JobRequest> requests) {
        List<Job> jobs = jobService.createJobBatch(queueId, requests);
        return ResponseEntity.ok(jobs);
    }

    @GetMapping
    public ResponseEntity<Page<Job>> getJobs(
            @RequestParam(required = false) Long queueId,
            @RequestParam(required = false) JobStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<Job> jobs = jobService.getJobsFiltered(queueId, status, start, end, page, size);
        return ResponseEntity.ok(jobs);
    }

    @GetMapping("/dlq")
    public ResponseEntity<Page<DeadLetterQueueEntry>> getDlqEntries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<DeadLetterQueueEntry> entries = jobService.getDlqEntries(page, size);
        return ResponseEntity.ok(entries);
    }

    @PostMapping("/dlq/{dlqEntryId}/retry")
    public ResponseEntity<Job> retryDlqJob(@PathVariable Long dlqEntryId) {
        Job job = jobService.retryDlqJob(dlqEntryId);
        return ResponseEntity.ok(job);
    }

    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        // Status counts
        Map<String, Long> statusCounts = new HashMap<>();
        for (JobStatus status : JobStatus.values()) {
            statusCounts.put(status.name(), jobRepository.countByStatus(status));
        }
        metrics.put("statusCounts", statusCounts);

        // General counts
        metrics.put("queueCount", queueRepository.count());
        metrics.put("workerCount", workerRepository.count());

        // System load / throughput summary
        long activeWorkers = workerRepository.findByStatus("ACTIVE").size();
        metrics.put("activeWorkers", activeWorkers);

        return ResponseEntity.ok(metrics);
    }
}
