package com.distributedscheduler.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueueHealthDto {
    private Long id;
    private String name;
    private Integer priority;
    private Integer concurrencyLimit;
    private Boolean paused;
    private Long projectId;
    private Map<String, Long> statusCounts;
    private Long runningCount;
    private String health; // HEALTHY, PAUSED, BACKLOG, AT_CAPACITY
}
