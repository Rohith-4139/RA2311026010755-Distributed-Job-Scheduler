package com.distributedscheduler.api.controller;

import com.distributedscheduler.api.dto.QueueHealthDto;
import com.distributedscheduler.api.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/queues/health")
    public ResponseEntity<List<QueueHealthDto>> getQueueHealth() {
        return ResponseEntity.ok(dashboardService.getQueueHealth());
    }

    @GetMapping("/throughput")
    public ResponseEntity<List<java.util.Map<String, Object>>> getThroughput(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "24") int hours) {
        return ResponseEntity.ok(dashboardService.getThroughput(hours));
    }
}
