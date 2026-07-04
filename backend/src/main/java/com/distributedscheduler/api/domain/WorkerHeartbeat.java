package com.distributedscheduler.api.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "worker_heartbeats", indexes = {
    @Index(name = "idx_worker_heartbeats_worker_id", columnList = "worker_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkerHeartbeat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "worker_id", nullable = false)
    private Worker worker;

    @Column(name = "last_ping", nullable = false)
    private LocalDateTime lastPing;

    @PrePersist
    @PreUpdate
    protected void onPing() {
        this.lastPing = LocalDateTime.now();
    }
}
