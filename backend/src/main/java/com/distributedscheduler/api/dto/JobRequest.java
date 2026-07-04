package com.distributedscheduler.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class JobRequest {
    @NotBlank(message = "Job payload cannot be blank")
    private String payload;

    private Integer priority;

    private Integer maxRetries;

    private Integer delaySeconds; // optional, for delayed jobs

    private String cronExpression; // optional, for cron scheduled jobs
}
