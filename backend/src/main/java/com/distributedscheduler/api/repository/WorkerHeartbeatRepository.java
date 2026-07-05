package com.distributedscheduler.api.repository;

import com.distributedscheduler.api.domain.WorkerHeartbeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkerHeartbeatRepository extends JpaRepository<WorkerHeartbeat, Long> {
    List<WorkerHeartbeat> findByWorkerId(String workerId);
    Optional<WorkerHeartbeat> findFirstByWorkerIdOrderByLastPingDesc(String workerId);
    void deleteByWorkerId(String workerId);
    List<WorkerHeartbeat> findByLastPingBefore(LocalDateTime threshold);
}
