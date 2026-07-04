package com.distributedscheduler.api.service;

import com.distributedscheduler.api.domain.DeadLetterQueueEntry;
import com.distributedscheduler.api.repository.DeadLetterQueueEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiSummaryServiceImpl implements AiSummaryService {

    private static final Logger log = LoggerFactory.getLogger(AiSummaryServiceImpl.class);

    private final DeadLetterQueueEntryRepository dlqRepository;
    private final RestTemplate restTemplate;

    @Value("${llm.api-key:}")
    private String apiKey;

    @Value("${llm.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    @Value("${llm.model:gpt-4o-mini}")
    private String model;

    public AiSummaryServiceImpl(DeadLetterQueueEntryRepository dlqRepository) {
        this.dlqRepository = dlqRepository;
        this.restTemplate = new RestTemplate();
    }

    @Override
    @Async
    public void generateFailureSummary(DeadLetterQueueEntry entry, String errorLog) {
        String summary = getFallbackSummary(errorLog);

        if (apiKey != null && !apiKey.isBlank()) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBearerAuth(apiKey);

                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("model", model);

                Map<String, String> systemMessage = new HashMap<>();
                systemMessage.put("role", "system");
                systemMessage.put("content", "You are a senior backend developer. Summarize the following execution failure trace in one short, plain-English sentence (max 20 words). Keep it user-friendly and actionable.");

                Map<String, String> userMessage = new HashMap<>();
                userMessage.put("role", "user");
                userMessage.put("content", errorLog);

                requestBody.put("messages", List.of(systemMessage, userMessage));
                requestBody.put("max_tokens", 80);

                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
                ResponseEntity<Map> response = restTemplate.postForEntity(
                        baseUrl + "/chat/completions", entity, Map.class);

                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    List choices = (List) response.getBody().get("choices");
                    if (choices != null && !choices.isEmpty()) {
                        Map choice = (Map) choices.get(0);
                        Map message = (Map) choice.get("message");
                        if (message != null && message.get("content") != null) {
                            summary = message.get("content").toString().trim();
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to generate AI failure summary via LLM API, falling back to static logic. Error: {}", e.getMessage());
            }
        } else {
            log.info("No LLM API Key configured. Using static fallback failure summary.");
        }

        entry.setAiSummary(summary);
        dlqRepository.save(entry);
    }

    private String getFallbackSummary(String errorLog) {
        if (errorLog == null || errorLog.isBlank()) {
            return "Job failed with an unknown error.";
        }
        // Extract first line of error or truncate
        String firstLine = errorLog.split("\n")[0];
        if (firstLine.length() > 80) {
            firstLine = firstLine.substring(0, 77) + "...";
        }
        return "Failed due to: " + firstLine;
    }
}
