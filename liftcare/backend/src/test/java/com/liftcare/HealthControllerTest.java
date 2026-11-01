package com.liftcare;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/health should return 200 OK with system status details")
    void shouldReturnHealthStatus() throws Exception {
        mockMvc.perform(get("/api/health")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.service", containsString("LiftCare")))
                .andExpect(jsonPath("$.currentModule", containsString("Module 1")))
                .andExpect(jsonPath("$.nodeId", notNullValue()))
                .andExpect(jsonPath("$.database.status", notNullValue()))
                .andExpect(jsonPath("$.capabilities.module7Status", containsString("Complete")))
                .andExpect(jsonPath("$.capabilities.module8Status", containsString("Complete")))
                .andExpect(jsonPath("$.capabilities.offlineResilience", containsString("ENABLED")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    @DisplayName("GET non-existent endpoint should return 404 with standard error JSON structure")
    void shouldReturnStandardErrorOn404() throws Exception {
        mockMvc.perform(get("/api/public/non-existent-route")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.path", is("/api/public/non-existent-route")));
    }
}
