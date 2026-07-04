package com.distributedscheduler.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.distributedscheduler.api.DistributedSchedulerApplication;
import com.distributedscheduler.api.domain.Job;
import com.distributedscheduler.api.domain.JobStatus;
import com.distributedscheduler.api.domain.Organization;
import com.distributedscheduler.api.domain.Project;
import com.distributedscheduler.api.domain.Queue;
import com.distributedscheduler.api.repository.JobRepository;
import com.distributedscheduler.api.repository.OrganizationRepository;
import com.distributedscheduler.api.repository.ProjectRepository;
import com.distributedscheduler.api.repository.QueueRepository;
import com.distributedscheduler.api.service.JobService;

@SpringBootTest(classes = DistributedSchedulerApplication.class)
@ActiveProfiles("test")
public class ConcurrentClaimTest {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JobService jobService;

    @Test
    public void concurrentClaims_doNotDoubleClaim() throws Exception {
        Organization organization = organizationRepository.save(Organization.builder().name("test-org").build());
        Project project = projectRepository.save(Project.builder().name("test-proj").organization(organization).build());
        Queue queue = queueRepository.save(Queue.builder()
                .name("test-queue")
                .project(project)
                .priority(1)
                .concurrencyLimit(10)
                .paused(false)
                .build());

        // create 10 queued jobs in test DB
        for (int i = 0; i < 10; i++) {
            Job j = Job.builder()
                    .queue(queue)
                    .payload("payload-" + i)
                    .status(JobStatus.QUEUED)
                    .priority(1)
                    .maxRetries(3)
                    .retryCount(0)
                    .scheduledAt(java.time.LocalDateTime.now())
                    .build();
            jobRepository.save(j);
        }

        int workerCount = 5;
        ExecutorService exec = Executors.newFixedThreadPool(workerCount);
        CountDownLatch latch = new CountDownLatch(workerCount);
        List<Integer> claimedCounts = new ArrayList<>();

        for (int w = 0; w < workerCount; w++) {
            exec.submit(() -> {
                try {
                    List<Job> claimed = jobService.claimJobs(queue.getId(), "worker-" + Thread.currentThread().getId(), 5);
                    synchronized (claimedCounts) {
                        claimedCounts.add(claimed.size());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        exec.shutdownNow();

        int totalClaimed = claimedCounts.stream().mapToInt(Integer::intValue).sum();
        assertEquals(10, totalClaimed, "All 10 jobs should be claimed exactly once");
    }
}
