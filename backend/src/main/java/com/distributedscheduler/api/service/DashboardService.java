package com.distributedscheduler.api.service;

import com.distributedscheduler.api.domain.JobStatus;
import com.distributedscheduler.api.domain.Queue;
import com.distributedscheduler.api.domain.Worker;
import com.distributedscheduler.api.domain.WorkerHeartbeat;
import com.distributedscheduler.api.dto.QueueHealthDto;
import com.distributedscheduler.api.dto.WorkerStatusDto;
import com.distributedscheduler.api.repository.JobExecutionRepository;
import com.distributedscheduler.api.repository.JobRepository;
import com.distributedscheduler.api.repository.QueueRepository;
import com.distributedscheduler.api.repository.WorkerHeartbeatRepository;
import com.distributedscheduler.api.repository.WorkerRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final QueueRepository queueRepository;
    private final JobRepository jobRepository;
    private final WorkerRepository workerRepository;
    private final WorkerHeartbeatRepository heartbeatRepository;
    private final JobExecutionRepository jobExecutionRepository;

    public DashboardService(QueueRepository queueRepository, JobRepository jobRepository,
                            WorkerRepository workerRepository, WorkerHeartbeatRepository heartbeatRepository,
                            JobExecutionRepository jobExecutionRepository) {
        this.queueRepository = queueRepository;
        this.jobRepository = jobRepository;
        this.workerRepository = workerRepository;
        this.heartbeatRepository = heartbeatRepository;
        this.jobExecutionRepository = jobExecutionRepository;
    }

    public List<QueueHealthDto> getQueueHealth() {
        List<Queue> queues = queueRepository.findAll();
        List<QueueHealthDto> result = new ArrayList<>();

        for (Queue queue : queues) {
            Map<String, Long> statusCounts = new HashMap<>();
            for (JobStatus status : JobStatus.values()) {
                long count = jobRepository.countByQueueIdAndStatus(queue.getId(), status);
                statusCounts.put(status.name(), count);
            }

            long running = statusCounts.getOrDefault(JobStatus.RUNNING.name(), 0L);
            long queued = statusCounts.getOrDefault(JobStatus.QUEUED.name(), 0L)
                    + statusCounts.getOrDefault(JobStatus.SCHEDULED.name(), 0L);

            String health;
            if (Boolean.TRUE.equals(queue.getPaused())) {
                health = "PAUSED";
            } else if (running >= queue.getConcurrencyLimit()) {
                health = "AT_CAPACITY";
            } else if (queued > 10) {
                health = "BACKLOG";
            } else {
                health = "HEALTHY";
            }

            result.add(QueueHealthDto.builder()
                    .id(queue.getId())
                    .name(queue.getName())
                    .priority(queue.getPriority())
                    .concurrencyLimit(queue.getConcurrencyLimit())
                    .paused(queue.getPaused())
                    .projectId(queue.getProject().getId())
                    .statusCounts(statusCounts)
                    .runningCount(running)
                    .health(health)
                    .build());
        }

        return result;
    }

    public List<WorkerStatusDto> getWorkerStatuses() {
        List<Worker> workers = workerRepository.findAll();
        return workers.stream().map(worker -> {
            WorkerHeartbeat hb = heartbeatRepository.findByWorkerId(worker.getId()).orElse(null);
            long active = jobExecutionRepository.findByWorkerIdAndStatus(worker.getId(), "RUNNING").size();
            return WorkerStatusDto.builder()
                    .id(worker.getId())
                    .name(worker.getName())
                    .status(worker.getStatus())
                    .registeredAt(worker.getRegisteredAt())
                    .lastPing(hb != null ? hb.getLastPing() : null)
                    .activeExecutions(active)
                    .build();
        }).collect(Collectors.toList());
    }

    public List<Map<String, Object>> getThroughput(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        List<Object[]> rows = jobExecutionRepository.countCompletedByHourSince(since);

        Map<String, Long> bucketCounts = new LinkedHashMap<>();
        for (int i = hours - 1; i >= 0; i--) {
            LocalDateTime bucket = LocalDateTime.now().minusHours(i).withMinute(0).withSecond(0).withNano(0);
            bucketCounts.put(bucket.toString(), 0L);
        }

        for (Object[] row : rows) {
            if (row[0] != null && row[1] != null) {
                bucketCounts.put(row[0].toString(), ((Number) row[1]).longValue());
            }
        }

        List<Map<String, Object>> series = new ArrayList<>();
        for (Map.Entry<String, Long> entry : bucketCounts.entrySet()) {
            Map<String, Object> point = new HashMap<>();
            point.put("hour", entry.getKey());
            point.put("completed", entry.getValue());
            series.add(point);
        }
        return series;
    }
}
