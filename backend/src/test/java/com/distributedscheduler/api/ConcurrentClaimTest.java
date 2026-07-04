package com.distributedscheduler.api;

import com.distributedscheduler.api.domain.Job;
import com.distributedscheduler.api.domain.Project;
import com.distributedscheduler.api.domain.Queue;
import com.distributedscheduler.api.dto.ProjectRequest;
import com.distributedscheduler.api.dto.QueueRequest;
import com.distributedscheduler.api.repository.JobRepository;
import com.distributedscheduler.api.repository.QueueRepository;
import com.distributedscheduler.api.service.JobService;
import com.distributedscheduler.api.service.ProjectService;
import com.distributedscheduler.api.service.QueueService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class ConcurrentClaimTest {

    @Autowired
    private JobService jobService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private QueueService queueService;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private QueueRepository queueRepository;

    @Test
    public void testAtomicConcurrentClaimAndConcurrencyLimit() throws InterruptedException {
        // 1. Create project and queue with concurrency limit of 5
        Project project = projectService.getOrCreateDefault();
        
        QueueRequest queueReq = new QueueRequest();
        queueReq.setName("Concurrency-Test-Queue-" + UUID.randomUUID().toString().substring(0, 5));
        queueReq.setProjectId(project.getId());
        queueReq.setConcurrencyLimit(5);
        queueReq.setPriority(10);
        queueReq.setPaused(false);
        Queue queue = queueService.createQueue(queueReq);

        // 2. Create 15 jobs in the queue
        for (int i = 0; i < 15; i++) {
            com.distributedscheduler.api.dto.JobRequest jobReq = new com.distributedscheduler.api.dto.JobRequest();
            jobReq.setPayload("Job #" + i);
            jobReq.setPriority(1);
            jobService.createJob(queue.getId(), jobReq);
        }

        // 3. Spin up 5 concurrent workers attempting to claim jobs at the same time
        ExecutorService executor = Executors.newFixedThreadPool(5);
        Set<Long> claimedJobIds = ConcurrentHashMap.newKeySet();
        List<List<Job>> workerClaims = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            final String workerId = "Worker-Thread-" + i;
            executor.submit(() -> {
                // Try to claim 5 jobs
                List<Job> claimed = jobService.claimJobs(queue.getId(), workerId, 5);
                synchronized (workerClaims) {
                    workerClaims.add(claimed);
                }
                for (Job job : claimed) {
                    boolean added = claimedJobIds.add(job.getId());
                    // Assert that no two workers got the same job ID (atomic claim verification)
                    assertTrue(added, "Duplicate claim detected: Job ID " + job.getId() + " claimed twice!");
                }
            });
        }

        executor.shutdown();
        boolean finished = executor.awaitTermination(10, TimeUnit.SECONDS);
        assertTrue(finished, "Claim tasks did not finish in time");

        // 4. Assertions
        int totalClaimed = claimedJobIds.size();
        // Since concurrency limit is 5, at most 5 jobs should be claimed/running in total
        assertTrue(totalClaimed <= 5, "Concurrency limit violated! Total claimed: " + totalClaimed + ", limit: 5");
        
        // Clean up
        jobRepository.deleteAll();
        queueRepository.delete(queue);
    }
}
