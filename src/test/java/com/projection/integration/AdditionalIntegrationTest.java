package com.projection.integration;

import com.projection.example.ExampleApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = ExampleApplication.class)
@AutoConfigureMockMvc
class AdditionalIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("Complex Parsing and Depth")
    class ComplexParsing {

        @Test
        void projectsArrayInsideObject() throws Exception {
            // User(profile(settings(notifications))) - 3 levels, valid
            // Using /api/users (which returns a list) to bypass strict @ProjectableFields on /api/users/{id}
            mockMvc.perform(get("/api/users")
                    .header("X-Response-Fields", "profile(settings(notifications))"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].profile.settings.notifications").exists())
                .andExpect(jsonPath("$[0].profile.settings.theme").doesNotExist());
        }

        @Test
        void projectsArrayInsideArray3Levels() throws Exception {
            // orders -> items -> productId (3 levels)
            mockMvc.perform(get("/api/users/1/orders")
                    .header("X-Response-Fields", "items(productId)"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].items[0].productId").exists())
                .andExpect(jsonPath("$[0].total").doesNotExist());
        }
        
    }

    @Nested
    @DisplayName("Edge Cases Extra")
    class EdgeCasesExtra {

        @Test
        void handlesWhitespaceOnlyHeader() throws Exception {
            mockMvc.perform(get("/api/users/1")
                    .header("X-Response-Fields", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.profile").exists());
        }
    }
}
