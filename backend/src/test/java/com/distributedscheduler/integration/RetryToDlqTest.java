package com.distributedscheduler.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.distributedscheduler.api.DistributedSchedulerApplication;
import com.distributedscheduler.api.domain.Job;
import com.distributedscheduler.api.domain.JobExecution;
import com.distributedscheduler.api.domain.JobStatus;
import com.distributedscheduler.api.domain.Organization;
import com.distributedscheduler.api.domain.Project;
import com.distributedscheduler.api.domain.Queue;
import com.distributedscheduler.api.domain.Worker;
import com.distributedscheduler.api.domain.WorkerHeartbeat;
import com.distributedscheduler.api.repository.DeadLetterQueueEntryRepository;
import com.distributedscheduler.api.repository.JobExecutionRepository;
import com.distributedscheduler.api.repository.JobRepository;
import com.distributedscheduler.api.repository.OrganizationRepository;
import com.distributedscheduler.api.repository.ProjectRepository;
import com.distributedscheduler.api.repository.QueueRepository;
import com.distributedscheduler.api.repository.WorkerHeartbeatRepository;
import com.distributedscheduler.api.repository.WorkerRepository;
import com.distributedscheduler.api.service.WorkerService;

@SpringBootTest(classes = DistributedSchedulerApplication.class)
@ActiveProfiles("test")
public class RetryToDlqTest {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private WorkerRepository workerRepository;

    @Autowired
    private WorkerHeartbeatRepository heartbeatRepository;

    @Autowired
    private JobExecutionRepository jobExecutionRepository;

    @Autowired
    private DeadLetterQueueEntryRepository dlqRepo;

    @Autowired
    private WorkerService workerService;

    @Test
    public void failedJobEventuallyMovesToDlq() throws Exception {
        Organization organization = organizationRepository.save(Organization.builder().name("org1").build());
        Project project = projectRepository.save(Project.builder().name("p1").organization(organization).build());
        Queue queue = queueRepository.save(Queue.builder().name("q1").project(project).priority(1).concurrencyLimit(1).paused(false).build());

        Job job = Job.builder()
                .queue(queue)
                .payload("fail-simulation")
                .status(JobStatus.RUNNING)
                .priority(1)
                .maxRetries(1)
                .retryCount(1)
                .scheduledAt(java.time.LocalDateTime.now())
                .build();
        job = jobRepository.save(job);

        Worker worker = Worker.builder().id("worker-1").name("w1").status("ACTIVE").build();
        workerRepository.save(worker);

        WorkerHeartbeat hb = WorkerHeartbeat.builder().worker(worker).lastPing(java.time.LocalDateTime.now().minusMinutes(10)).build();
        heartbeatRepository.save(hb);

        JobExecution exec = JobExecution.builder().job(job).workerId(worker.getId()).status("RUNNING").build();
        jobExecutionRepository.save(exec);

        // Run watchdog which should detect stale worker and move job to DLQ
        workerService.watchdogCheck();

        boolean present = dlqRepo.findByJobId(job.getId()).isPresent();
        assertTrue(present, "Job should be present in DLQ after watchdog detection");
    }
}
