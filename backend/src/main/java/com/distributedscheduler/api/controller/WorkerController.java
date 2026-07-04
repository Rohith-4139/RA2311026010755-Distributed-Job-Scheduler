package com.distributedscheduler.api.controller;

import com.distributedscheduler.api.domain.Worker;
import com.distributedscheduler.api.dto.WorkerStatusDto;
import com.distributedscheduler.api.service.DashboardService;
import com.distributedscheduler.api.service.WorkerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workers")
public class WorkerController {

    private final WorkerService workerService;
    private final DashboardService dashboardService;

    public WorkerController(WorkerService workerService, DashboardService dashboardService) {
        this.workerService = workerService;
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public ResponseEntity<List<Worker>> getAllWorkers() {
        return ResponseEntity.ok(workerService.getAllWorkers());
    }

    @GetMapping("/status")
    public ResponseEntity<List<WorkerStatusDto>> getWorkerStatuses() {
        return ResponseEntity.ok(dashboardService.getWorkerStatuses());
    }
}
