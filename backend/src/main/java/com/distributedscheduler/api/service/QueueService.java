package com.distributedscheduler.api.service;

import com.distributedscheduler.api.domain.Project;
import com.distributedscheduler.api.domain.Queue;
import com.distributedscheduler.api.dto.QueueRequest;
import com.distributedscheduler.api.repository.ProjectRepository;
import com.distributedscheduler.api.repository.QueueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class QueueService {

    private final QueueRepository queueRepository;
    private final ProjectRepository projectRepository;

    public QueueService(QueueRepository queueRepository, ProjectRepository projectRepository) {
        this.queueRepository = queueRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional
    public Queue createQueue(QueueRequest request) {
        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        Queue queue = Queue.builder()
                .name(request.getName())
                .project(project)
                .priority(request.getPriority() != null ? request.getPriority() : 1)
                .concurrencyLimit(request.getConcurrencyLimit() != null ? request.getConcurrencyLimit() : 5)
                .paused(request.getPaused() != null ? request.getPaused() : false)
                .build();

        return queueRepository.save(queue);
    }

    public List<Queue> getQueuesByProject(Long projectId) {
        return queueRepository.findByProjectId(projectId);
    }

    public List<Queue> getAllQueues() {
        return queueRepository.findAll();
    }

    @Transactional
    public Queue updateQueue(Long id, QueueRequest request) {
        Queue queue = queueRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Queue not found"));

        if (request.getName() != null) {
            queue.setName(request.getName());
        }
        if (request.getPriority() != null) {
            queue.setPriority(request.getPriority());
        }
        if (request.getConcurrencyLimit() != null) {
            queue.setConcurrencyLimit(request.getConcurrencyLimit());
        }
        if (request.getPaused() != null) {
            queue.setPaused(request.getPaused());
        }

        return queueRepository.save(queue);
    }

    @Transactional
    public Queue pauseQueue(Long id) {
        Queue queue = queueRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Queue not found"));
        queue.setPaused(true);
        return queueRepository.save(queue);
    }

    @Transactional
    public Queue resumeQueue(Long id) {
        Queue queue = queueRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Queue not found"));
        queue.setPaused(false);
        return queueRepository.save(queue);
    }

    @Transactional
    public void deleteQueue(Long id) {
        queueRepository.deleteById(id);
    }
}
