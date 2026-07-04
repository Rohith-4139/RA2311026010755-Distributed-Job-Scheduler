package com.distributedscheduler.api;

import com.distributedscheduler.api.domain.DeadLetterQueueEntry;
import com.distributedscheduler.api.domain.Job;
import com.distributedscheduler.api.domain.JobStatus;
import com.distributedscheduler.api.domain.Project;
import com.distributedscheduler.api.domain.Queue;
import com.distributedscheduler.api.dto.QueueRequest;
import com.distributedscheduler.api.repository.DeadLetterQueueEntryRepository;
import com.distributedscheduler.api.repository.JobRepository;
import com.distributedscheduler.api.repository.QueueRepository;
import com.distributedscheduler.api.service.JobService;
import com.distributedscheduler.api.service.ProjectService;
import com.distributedscheduler.api.service.QueueService;
import com.distributedscheduler.api.worker.WorkerNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class RetryEscalationTest {

    @Autowired
    private JobService jobService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private QueueService queueService;

    @Autowired
    private WorkerNode workerNode;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private DeadLetterQueueEntryRepository dlqRepository;

    @Test
    public void testJobFailureAndEscalationToDlq() throws InterruptedException {
        // 1. Create project and queue
        Project project = projectService.getOrCreateDefault();
        
        QueueRequest queueReq = new QueueRequest();
        queueReq.setName("Retry-Test-Queue-" + UUID.randomUUID().toString().substring(0, 5));
        queueReq.setProjectId(project.getId());
        queueReq.setConcurrencyLimit(5);
        queueReq.setPriority(1);
        queueReq.setPaused(false);
        Queue queue = queueService.createQueue(queueReq);

        // 2. Create a job that is guaranteed to fail ("fail" payload) with maxRetries = 1
        com.distributedscheduler.api.dto.JobRequest jobReq = new com.distributedscheduler.api.dto.JobRequest();
        jobReq.setPayload("fail-simulation");
        jobReq.setPriority(1);
        jobReq.setMaxRetries(1); // 1 retry allowed (total 2 execution attempts)
        Job job = (Job) jobService.createJob(queue.getId(), jobReq);

        // 3. First execution attempt (runs, fails, schedules retry)
        workerNode.pollAndExecute();
        // Wait briefly for the async executor pool to process the execution
        Thread.sleep(1500);

        Job firstCheckJob = jobRepository.findById(job.getId()).orElseThrow();
        assertEquals(JobStatus.QUEUED, firstCheckJob.getStatus(), "Job should be QUEUED again for retry");
        assertEquals(1, firstCheckJob.getRetryCount(), "Retry count should be incremented to 1");

        // 4. Second execution attempt (runs, fails, exceeds maxRetries -> goes to DLQ)
        workerNode.pollAndExecute();
        // Wait briefly for the async executor pool to finish
        Thread.sleep(1500);

        Job finalCheckJob = jobRepository.findById(job.getId()).orElseThrow();
        assertEquals(JobStatus.DEAD_LETTER, finalCheckJob.getStatus(), "Job status should be DEAD_LETTER");

        // 5. Verify Dead Letter Queue entry
        List<DeadLetterQueueEntry> dlqEntries = dlqRepository.findAll();
        DeadLetterQueueEntry match = dlqEntries.stream()
                .filter(e -> e.getJob() != null && e.getJob().getId().equals(job.getId()))
                .findFirst()
                .orElse(null);

        assertNotNull(match, "Dead Letter Queue entry should be created");
        assertTrue(match.getErrorMessage().contains("Simulated job failure"), "DLQ error message should capture the exception");

        // Clean up
        dlqRepository.deleteAll();
        jobRepository.deleteAll();
        queueRepository.delete(queue);
    }
}
