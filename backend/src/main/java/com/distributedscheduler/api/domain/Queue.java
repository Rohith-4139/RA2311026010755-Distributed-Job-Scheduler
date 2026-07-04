package com.distributedscheduler.api.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "queues", indexes = {
    @Index(name = "idx_queues_project_id", columnList = "project_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"jobs", "retryPolicies"})
public class Queue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false)
    private Integer priority; // higher runs first

    @Column(name = "concurrency_limit", nullable = false)
    private Integer concurrencyLimit;

    @Column(nullable = false)
    private Boolean paused;

    @OneToMany(mappedBy = "queue", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<Job> jobs;

    @OneToMany(mappedBy = "queue", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<RetryPolicy> retryPolicies;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.paused == null) this.paused = false;
        if (this.priority == null) this.priority = 1;
        if (this.concurrencyLimit == null) this.concurrencyLimit = 5;
    }
}
