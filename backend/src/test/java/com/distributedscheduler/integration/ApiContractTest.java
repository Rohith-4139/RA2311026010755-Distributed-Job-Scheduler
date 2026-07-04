package com.distributedscheduler.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.distributedscheduler.api.DistributedSchedulerApplication;

@SpringBootTest(classes = DistributedSchedulerApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ApiContractTest {

    @Autowired
    private MockMvc mvc;

    @Test
    public void registerEndpoint_returns200() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"testuser\",\"password\":\"pwd123\"}"))
                .andExpect(status().isOk());
    }
}
