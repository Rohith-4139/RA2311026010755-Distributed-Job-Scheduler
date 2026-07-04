package com.distributedscheduler.api.scheduler;

import com.distributedscheduler.api.domain.Job;
import com.distributedscheduler.api.domain.JobStatus;
import com.distributedscheduler.api.domain.ScheduledJob;
import com.distributedscheduler.api.repository.JobRepository;
import com.distributedscheduler.api.repository.ScheduledJobRepository;
import com.distributedscheduler.api.service.JobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class CronDispatcherScheduler {

    private static final Logger log = LoggerFactory.getLogger(CronDispatcherScheduler.class);

    private final ScheduledJobRepository scheduledJobRepository;
    private final JobRepository jobRepository;
    private final JobService jobService;

    public CronDispatcherScheduler(ScheduledJobRepository scheduledJobRepository,
                                   JobRepository jobRepository,
                                   JobService jobService) {
        this.scheduledJobRepository = scheduledJobRepository;
        this.jobRepository = jobRepository;
        this.jobService = jobService;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void dispatchCronJobs() {
        LocalDateTime now = LocalDateTime.now();
        List<ScheduledJob> eligibleCronJobs = scheduledJobRepository.findByActiveTrueAndNextRunAtBefore(now);

        for (ScheduledJob sj : eligibleCronJobs) {
            log.info("Dispatching scheduled cron job: ID={}, cron='{}'", sj.getId(), sj.getCronExpression());

            // Create concrete Job instance from scheduled template
            Job job = Job.builder()
                    .queue(sj.getQueue())
                    .payload(sj.getPayload())
                    .priority(1) // default priority
                    .maxRetries(3) // default retries
                    .retryCount(0)
                    .scheduledAt(now)
                    .status(JobStatus.QUEUED)
                    .build();

            jobRepository.save(job);

            // Update ScheduledJob times
            sj.setLastRunAt(now);
            sj.setNextRunAt(jobService.calculateNextRun(sj.getCronExpression()));
            scheduledJobRepository.save(sj);
        }
    }
}
