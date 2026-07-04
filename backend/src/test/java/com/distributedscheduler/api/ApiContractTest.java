package com.distributedscheduler.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.distributedscheduler.api.dto.AuthRequest;
import com.distributedscheduler.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class ApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private String jwtToken;
    private final String username = "testuser_" + UUID.randomUUID().toString().substring(0, 5);
    private final String password = "password123";

    @BeforeEach
    public void setup() throws Exception {
        userRepository.deleteAll();

        // 1. Register User
        AuthRequest registerReq = new AuthRequest();
        registerReq.setUsername(username);
        registerReq.setPassword(password);

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isOk());

        // 2. Login User to get JWT token
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        Map<?, ?> responseMap = objectMapper.readValue(responseContent, Map.class);
        this.jwtToken = responseMap.get("token").toString();
    }

    @Test
    public void testUnauthorizedAccess() throws Exception {
        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testAuthorizedAccessAndValidationErrorShape() throws Exception {
        // 1. Check authorized access
        mockMvc.perform(get("/api/v1/projects")
                .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk());

        // 2. Try creating a job with empty payload to trigger validation constraints
        com.distributedscheduler.api.dto.JobRequest invalidJob = new com.distributedscheduler.api.dto.JobRequest();
        invalidJob.setPayload(""); // fails @NotBlank constraint

        mockMvc.perform(post("/api/v1/jobs/queue/999")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidJob)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.message", containsString("payload")))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.path", is("/api/v1/jobs/queue/999")));
    }
}
