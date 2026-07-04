package com.distributedscheduler.api.controller;

import com.distributedscheduler.api.domain.Queue;
import com.distributedscheduler.api.dto.QueueRequest;
import com.distributedscheduler.api.service.QueueService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/queues")
public class QueueController {

    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    @PostMapping
    public ResponseEntity<Queue> createQueue(@Valid @RequestBody QueueRequest request) {
        Queue queue = queueService.createQueue(request);
        return ResponseEntity.ok(queue);
    }

    @GetMapping
    public ResponseEntity<List<Queue>> getAllQueues() {
        return ResponseEntity.ok(queueService.getAllQueues());
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<Queue>> getQueuesByProject(@PathVariable Long projectId) {
        return ResponseEntity.ok(queueService.getQueuesByProject(projectId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Queue> updateQueue(@PathVariable Long id, @Valid @RequestBody QueueRequest request) {
        Queue queue = queueService.updateQueue(id, request);
        return ResponseEntity.ok(queue);
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<Queue> pauseQueue(@PathVariable Long id) {
        Queue queue = queueService.pauseQueue(id);
        return ResponseEntity.ok(queue);
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<Queue> resumeQueue(@PathVariable Long id) {
        Queue queue = queueService.resumeQueue(id);
        return ResponseEntity.ok(queue);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteQueue(@PathVariable Long id) {
        queueService.deleteQueue(id);
        return ResponseEntity.ok("Queue deleted successfully");
    }
}
