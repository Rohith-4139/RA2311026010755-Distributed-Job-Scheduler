package com.distributedscheduler.api.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "job_logs", indexes = {
    @Index(name = "idx_job_logs_execution_id", columnList = "job_execution_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "job_execution_id", nullable = false)
    private JobExecution jobExecution;

    @Column(name = "log_content", nullable = false, columnDefinition = "TEXT")
    private String logContent;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
