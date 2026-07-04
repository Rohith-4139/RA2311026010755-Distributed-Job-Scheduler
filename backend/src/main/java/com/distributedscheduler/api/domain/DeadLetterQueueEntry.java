package com.distributedscheduler.api.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "dead_letter_queue_entries", indexes = {
    @Index(name = "idx_dlq_job_id", columnList = "job_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeadLetterQueueEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "job_id", nullable = true, foreignKey = @ForeignKey(foreignKeyDefinition = "FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE SET NULL"))
    private Job job;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "failed_at", nullable = false)
    private LocalDateTime failedAt;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @PrePersist
    protected void onCreate() {
        this.failedAt = LocalDateTime.now();
    }
}
