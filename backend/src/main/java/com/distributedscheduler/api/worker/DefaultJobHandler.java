package com.distributedscheduler.api.worker;

import com.distributedscheduler.api.domain.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class DefaultJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(DefaultJobHandler.class);
    private final RestTemplate restTemplate;

    public DefaultJobHandler() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public void handle(Job job) throws Exception {
        String payload = job.getPayload();
        log.info("Processing job ID={}, payload='{}'", job.getId(), payload);

        // 1. Check if payload is an HTTP webhook URL
        if (payload != null && (payload.startsWith("http://") || payload.startsWith("https://"))) {
            log.info("Executing webhook HTTP POST request to: {}", payload);
            restTemplate.postForObject(payload, job, String.class);
            return;
        }

        // 2. Otherwise, parse simulation directives in payload
        if (payload != null) {
            if (payload.contains("fail")) {
                throw new RuntimeException("Simulated job failure based on payload parameter.");
            }
            if (payload.contains("sleep:")) {
                try {
                    // e.g. payload "sleep:2000" sleeps for 2 seconds
                    String[] parts = payload.split("sleep:");
                    int sleepMs = Integer.parseInt(parts[1].trim().split(" ")[0]);
                    log.info("Simulating execution delay of {} ms", sleepMs);
                    Thread.sleep(sleepMs);
                } catch (Exception e) {
                    Thread.sleep(1000); // fallback
                }
            } else {
                // Default short sleep for simulation
                Thread.sleep(1000);
            }
        } else {
            Thread.sleep(1000);
        }

        log.info("Job ID={} execution completed successfully", job.getId());
    }
}
