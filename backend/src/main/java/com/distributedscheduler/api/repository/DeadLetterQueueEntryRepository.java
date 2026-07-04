package com.distributedscheduler.api.repository;

import com.distributedscheduler.api.domain.DeadLetterQueueEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DeadLetterQueueEntryRepository extends JpaRepository<DeadLetterQueueEntry, Long> {
    Optional<DeadLetterQueueEntry> findByJobId(Long jobId);
    Page<DeadLetterQueueEntry> findAllByOrderByFailedAtDesc(Pageable pageable);
}
