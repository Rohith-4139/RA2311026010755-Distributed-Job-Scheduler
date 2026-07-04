package com.distributedscheduler.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProjectRequest {
    @NotBlank(message = "Project name is required")
    private String name;

    @NotNull(message = "Organization ID is required")
    private Long organizationId;
}
