package com.distributedscheduler.api.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "jobs", indexes = {
    @Index(name = "idx_jobs_queue_status_scheduled", columnList = "queue_id, status, scheduled_at"),
    @Index(name = "idx_jobs_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Job {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "queue_id", nullable = false)
    private Queue queue;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private JobStatus status;

    @Column(nullable = false)
    private Integer priority;

    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Version
    private Integer version;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.retryCount == null) this.retryCount = 0;
        if (this.maxRetries == null) this.maxRetries = 3;
        if (this.status == null) this.status = JobStatus.QUEUED;
        if (this.priority == null) this.priority = 1;
        if (this.scheduledAt == null) this.scheduledAt = LocalDateTime.now();
    }
}
