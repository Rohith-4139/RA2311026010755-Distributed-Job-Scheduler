package com.distributedscheduler.api.domain;

public enum JobStatus {
    QUEUED,
    SCHEDULED,
    CLAIMED,
    RUNNING,
    COMPLETED,
    FAILED,
    DEAD_LETTER
}
