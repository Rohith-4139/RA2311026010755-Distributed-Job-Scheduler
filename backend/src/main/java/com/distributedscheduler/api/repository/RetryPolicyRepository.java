package com.distributedscheduler.api.repository;

import com.distributedscheduler.api.domain.RetryPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RetryPolicyRepository extends JpaRepository<RetryPolicy, Long> {
    Optional<RetryPolicy> findByQueueId(Long queueId);
}
