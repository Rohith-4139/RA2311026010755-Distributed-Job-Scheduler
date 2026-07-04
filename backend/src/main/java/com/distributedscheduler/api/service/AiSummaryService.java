package com.distributedscheduler.api.service;

import com.distributedscheduler.api.domain.DeadLetterQueueEntry;

public interface AiSummaryService {
    void generateFailureSummary(DeadLetterQueueEntry entry, String errorLog);
}
