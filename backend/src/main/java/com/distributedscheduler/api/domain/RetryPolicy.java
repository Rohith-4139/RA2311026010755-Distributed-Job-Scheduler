package com.distributedscheduler.api.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "retry_policies", indexes = {
    @Index(name = "idx_retry_policies_queue_id", columnList = "queue_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RetryPolicy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "queue_id", nullable = false)
    private Queue queue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RetryType type; // FIXED, LINEAR, EXPONENTIAL

    @Column(name = "delay_seconds", nullable = false)
    private Integer delaySeconds;

    @Column(name = "backoff_multiplier", nullable = false)
    private Double backoffMultiplier; // used for linear/exponential multiplier

    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries;
}
