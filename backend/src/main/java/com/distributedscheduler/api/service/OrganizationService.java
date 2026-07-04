package com.distributedscheduler.api.service;

import com.distributedscheduler.api.domain.Organization;
import com.distributedscheduler.api.repository.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    public OrganizationService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    @Transactional
    public Organization createOrganization(String name) {
        Organization org = Organization.builder().name(name).build();
        return organizationRepository.save(org);
    }

    public List<Organization> getAllOrganizations() {
        return organizationRepository.findAll();
    }

    public Organization getOrCreateDefault() {
        List<Organization> orgs = organizationRepository.findAll();
        if (orgs.isEmpty()) {
            return createOrganization("Default Organization");
        }
        return orgs.get(0);
    }
}
