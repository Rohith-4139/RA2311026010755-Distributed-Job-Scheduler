package com.distributedscheduler.api.worker;

import com.distributedscheduler.api.domain.Job;

public interface JobHandler {
    void handle(Job job) throws Exception;
}
