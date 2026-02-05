package com.projection.integration;

import com.projection.example.ExampleApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = ExampleApplication.class)
@AutoConfigureMockMvc
class ProjectionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("Projection header behavior")
    class ProjectionHeaderBehavior {

        @Test
        void withoutHeaderReturnsFullResponse() throws Exception {
            mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").exists())
                .andExpect(jsonPath("$.email").exists())
                .andExpect(jsonPath("$.profile").exists())
                .andExpect(jsonPath("$.orders").exists());
        }

        @Test
        void withHeaderProjectsFields() throws Exception {
            mockMvc.perform(get("/api/users/1")
                    .header("X-Response-Fields", "id,name"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Alice Johnson"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.profile").doesNotExist())
                .andExpect(jsonPath("$.orders").doesNotExist());
        }

        @Test
        void projectsNestedFields() throws Exception {
            mockMvc.perform(get("/api/users/1")
                    .header("X-Response-Fields", "id,profile(avatar)"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.profile.avatar").exists())
                .andExpect(jsonPath("$.profile.bio").doesNotExist())
                .andExpect(jsonPath("$.profile.settings").doesNotExist())
                .andExpect(jsonPath("$.name").doesNotExist());
        }

        @Test
        void projectsArrayElements() throws Exception {
            mockMvc.perform(get("/api/users")
                    .header("X-Response-Fields", "id,name"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].name").exists())
                .andExpect(jsonPath("$[0].email").doesNotExist());
        }

        @Test
        void projectsNestedArrays() throws Exception {
            mockMvc.perform(get("/api/users/1/orders")
                    .header("X-Response-Fields", "id,total,items(productId)"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].total").exists())
                .andExpect(jsonPath("$[0].items[0].productId").exists())
                .andExpect(jsonPath("$[0].items[0].productName").doesNotExist())
                .andExpect(jsonPath("$[0].status").doesNotExist());
        }
    }

    @Nested
    @DisplayName("Strict mode errors")
    class StrictModeErrors {

        @Test
        void missingFieldReturnsError() throws Exception {
            mockMvc.perform(get("/api/users/1/orders")
                    .header("X-Response-Fields", "id,nonexistent"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MISSING_FIELD"))
                .andExpect(jsonPath("$.error.path").value("nonexistent"))
                .andExpect(jsonPath("$.error.traceId").exists());
        }

        @Test
        void missingNestedFieldReturnsError() throws Exception {
            mockMvc.perform(get("/api/users/1/orders")
                    .header("X-Response-Fields", "items(productId,secret)"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MISSING_FIELD"))
                .andExpect(jsonPath("$.error.path", containsString("secret")));
        }

        @Test
        void invalidSyntaxReturnsError() throws Exception {
            mockMvc.perform(get("/api/users/1")
                    .header("X-Response-Fields", "id,name("))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PROJECTION_SYNTAX"));
        }
    }

    @Nested
    @DisplayName("Allowlist validation")
    class AllowlistValidation {

        @Test
        void allowlistAllowsPermittedFields() throws Exception {
            mockMvc.perform(get("/api/users/1")
                    .header("X-Response-Fields", "id,name,profile(avatar)"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").exists())
                .andExpect(jsonPath("$.profile.avatar").exists());
        }

        @Test
        void allowlistRejectsNonPermittedFields() throws Exception {
            mockMvc.perform(get("/api/users/1")
                    .header("X-Response-Fields", "id,orders"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("FIELD_NOT_ALLOWED"));
        }

        @Test
        void allowlistRejectsNonPermittedNestedFields() throws Exception {
            mockMvc.perform(get("/api/users/1")
                    .header("X-Response-Fields", "profile(avatar,settings)"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("FIELD_NOT_ALLOWED"));
        }
    }

    @Nested
    @DisplayName("Error passthrough")
    class ErrorPassthrough {

        @Test
        void notFoundPassesThrough() throws Exception {
            mockMvc.perform(get("/api/users/999")
                    .header("X-Response-Fields", "id,name"))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Cache eviction")
    class CacheEviction {

        @Test
        void createUserEvictsListCache() throws Exception {
            mockMvc.perform(get("/api/users")
                    .header("X-Response-Fields", "id"))
                .andExpect(status().isOk());

            mockMvc.perform(post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Test\",\"email\":\"test@example.com\"}"))
                .andExpect(status().isOk());

            mockMvc.perform(get("/api/users")
                    .header("X-Response-Fields", "id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThan(2))));
        }

        @Test
        void deleteUserEvictsCaches() throws Exception {
            // Create a user and capture the ID
            String responseJson = mockMvc.perform(post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"ToDelete\",\"email\":\"delete@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

            // Extract the ID from response
            Integer id = com.jayway.jsonpath.JsonPath.read(responseJson, "$.id");
            long userId = id.longValue();

            // Verify the user exists
            mockMvc.perform(get("/api/users/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("ToDelete"));

            // Delete the user
            mockMvc.perform(delete("/api/users/" + userId))
                .andExpect(status().isNoContent());

            // Verify cache was evicted - user should no longer exist
            mockMvc.perform(get("/api/users/" + userId))
                .andExpect(status().isNotFound());
        }
    }
}
