package com.distributedscheduler.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class QueueRequest {
    @NotBlank(message = "Queue name is required")
    private String name;

    @NotNull(message = "Project ID is required")
    private Long projectId;

    private Integer priority;

    private Integer concurrencyLimit;

    private Boolean paused;
}
