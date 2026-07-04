package com.distributedscheduler.api.service;

import com.distributedscheduler.api.domain.Organization;
import com.distributedscheduler.api.domain.Project;
import com.distributedscheduler.api.dto.ProjectRequest;
import com.distributedscheduler.api.repository.OrganizationRepository;
import com.distributedscheduler.api.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationService organizationService;

    public ProjectService(ProjectRepository projectRepository, OrganizationRepository organizationRepository,
                          OrganizationService organizationService) {
        this.projectRepository = projectRepository;
        this.organizationRepository = organizationRepository;
        this.organizationService = organizationService;
    }

    @Transactional
    public Project createProject(ProjectRequest request) {
        Organization organization = organizationRepository.findById(request.getOrganizationId())
                .orElseThrow(() -> new IllegalArgumentException("Organization not found"));

        Project project = Project.builder()
                .name(request.getName())
                .organization(organization)
                .build();
        return projectRepository.save(project);
    }

    public List<Project> getAllProjects() {
        return projectRepository.findAll();
    }

    public Project getOrCreateDefault() {
        List<Project> projects = projectRepository.findAll();
        if (projects.isEmpty()) {
            Organization defaultOrg = organizationService.getOrCreateDefault();
            Project project = Project.builder()
                    .name("Default Project")
                    .organization(defaultOrg)
                    .build();
            return projectRepository.save(project);
        }
        return projects.get(0);
    }

    @Transactional
    public void deleteProject(Long id) {
        projectRepository.deleteById(id);
    }
}
