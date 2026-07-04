package com.distributedscheduler.api.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "job_executions", indexes = {
    @Index(name = "idx_job_executions_job_id", columnList = "job_id"),
    @Index(name = "idx_job_executions_worker_id", columnList = "worker_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobExecution {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "job_id", nullable = true, foreignKey = @ForeignKey(foreignKeyDefinition = "FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE SET NULL"))
    private Job job;

    @Column(name = "worker_id", nullable = false)
    private String workerId;

    @Column(nullable = false, length = 30)
    private String status; // e.g. "RUNNING", "COMPLETED", "FAILED"

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "log_summary", columnDefinition = "TEXT")
    private String logSummary;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @PrePersist
    protected void onCreate() {
        this.startedAt = LocalDateTime.now();
    }
}
