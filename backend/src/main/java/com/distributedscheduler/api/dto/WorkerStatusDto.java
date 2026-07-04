package com.distributedscheduler.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkerStatusDto {
    private String id;
    private String name;
    private String status;
    private LocalDateTime registeredAt;
    private LocalDateTime lastPing;
    private long activeExecutions;
}
