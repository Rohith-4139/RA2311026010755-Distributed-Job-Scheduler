package com.distributedscheduler.api.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "scheduled_jobs", indexes = {
    @Index(name = "idx_scheduled_jobs_queue_id", columnList = "queue_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduledJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "queue_id", nullable = false)
    private Queue queue;

    @Column(name = "cron_expression", nullable = false)
    private String cronExpression;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;

    @Column(name = "next_run_at")
    private LocalDateTime nextRunAt;

    @Column(nullable = false)
    private Boolean active;

    @PrePersist
    protected void onCreate() {
        if (this.active == null) this.active = true;
    }
}
