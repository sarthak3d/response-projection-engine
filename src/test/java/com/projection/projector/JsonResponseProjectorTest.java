package com.projection.projector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projection.config.ProjectionProperties;
import com.projection.core.FilterContext;
import com.projection.core.ProjectionTree;
import com.projection.core.ProjectionTreeParser;
import com.projection.exception.MissingFieldException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonResponseProjectorTest {

    private ObjectMapper objectMapper;
    private ProjectionProperties properties;
    private JsonResponseProjector projector;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        properties = new ProjectionProperties();
        projector = new JsonResponseProjector(properties);
    }

    private FilterContext createContext() {
        return FilterContext.builder(properties).build();
    }

    @Nested
    @DisplayName("Simple field projection")
    class SimpleFieldProjection {

        @Test
        void projectSingleField() throws Exception {
            JsonNode input = objectMapper.readTree("""
                {"id": 1, "name": "John", "email": "john@example.com"}
                """);

            ProjectionTree projection = ProjectionTreeParser.parse("id");
            JsonNode result = projector.project(input, projection, createContext());

            assertTrue(result.has("id"));
            assertFalse(result.has("name"));
            assertFalse(result.has("email"));
            assertEquals(1, result.get("id").asInt());
        }

        @Test
        void projectMultipleFields() throws Exception {
            JsonNode input = objectMapper.readTree("""
                {"id": 1, "name": "John", "email": "john@example.com", "age": 30}
                """);

            ProjectionTree projection = ProjectionTreeParser.parse("id,name");
            JsonNode result = projector.project(input, projection, createContext());

            assertTrue(result.has("id"));
            assertTrue(result.has("name"));
            assertFalse(result.has("email"));
            assertFalse(result.has("age"));
        }

        @Test
        void emptyProjectionReturnsOriginal() throws Exception {
            JsonNode input = objectMapper.readTree("""
                {"id": 1, "name": "John"}
                """);

            ProjectionTree projection = ProjectionTree.empty();
            JsonNode result = projector.project(input, projection, createContext());

            assertEquals(input, result);
        }
    }

    @Nested
    @DisplayName("Nested field projection")
    class NestedFieldProjection {

        @Test
        void projectNestedField() throws Exception {
            JsonNode input = objectMapper.readTree("""
                {
                    "id": 1,
                    "profile": {
                        "avatar": "url.jpg",
                        "bio": "Developer",
                        "settings": {"theme": "dark"}
                    }
                }
                """);

            ProjectionTree projection = ProjectionTreeParser.parse("profile(avatar)");
            JsonNode result = projector.project(input, projection, createContext());

            assertTrue(result.has("profile"));
            assertTrue(result.get("profile").has("avatar"));
            assertFalse(result.get("profile").has("bio"));
            assertFalse(result.get("profile").has("settings"));
        }

        @Test
        void projectDeeplyNestedFields() throws Exception {
            JsonNode input = objectMapper.readTree("""
                {
                    "order": {
                        "items": {
                            "product": {
                                "id": 123,
                                "name": "Widget",
                                "price": 9.99
                            }
                        }
                    }
                }
                """);

            ProjectionTree projection = ProjectionTreeParser.parse("order(items(product(name,price)))");
            JsonNode result = projector.project(input, projection, createContext());

            JsonNode product = result.get("order").get("items").get("product");
            assertTrue(product.has("name"));
            assertTrue(product.has("price"));
            assertFalse(product.has("id"));
        }
    }

    @Nested
    @DisplayName("Array projection")
    class ArrayProjection {

        @Test
        void projectArrayElements() throws Exception {
            JsonNode input = objectMapper.readTree("""
                [
                    {"id": 1, "name": "Alice", "role": "admin"},
                    {"id": 2, "name": "Bob", "role": "user"}
                ]
                """);

            ProjectionTree projection = ProjectionTreeParser.parse("id,name");
            JsonNode result = projector.project(input, projection, createContext());

            assertTrue(result.isArray());
            assertEquals(2, result.size());

            for (JsonNode element : result) {
                assertTrue(element.has("id"));
                assertTrue(element.has("name"));
                assertFalse(element.has("role"));
            }
        }

        @Test
        void projectNestedArrays() throws Exception {
            JsonNode input = objectMapper.readTree("""
                {
                    "orders": [
                        {"id": 1, "items": [{"productId": 100, "qty": 2}]},
                        {"id": 2, "items": [{"productId": 200, "qty": 1}]}
                    ]
                }
                """);

            ProjectionTree projection = ProjectionTreeParser.parse("orders(id,items(productId))");
            JsonNode result = projector.project(input, projection, createContext());

            JsonNode orders = result.get("orders");
            assertTrue(orders.isArray());
            
            for (JsonNode order : orders) {
                assertTrue(order.has("id"));
                assertTrue(order.has("items"));
                
                for (JsonNode item : order.get("items")) {
                    assertTrue(item.has("productId"));
                    assertFalse(item.has("qty"));
                }
            }
        }
    }

    @Nested
    @DisplayName("Strict mode - missing fields")
    class StrictModeMissingFields {

        @Test
        void throwsOnMissingField() throws Exception {
            JsonNode input = objectMapper.readTree("""
                {"id": 1, "name": "John"}
                """);

            ProjectionTree projection = ProjectionTreeParser.parse("id,email");

            MissingFieldException exception = assertThrows(
                MissingFieldException.class,
                () -> projector.project(input, projection, createContext())
            );

            assertTrue(exception.getPath().contains("email"));
        }

        @Test
        void throwsOnMissingNestedField() throws Exception {
            JsonNode input = objectMapper.readTree("""
                {
                    "profile": {"avatar": "url.jpg"}
                }
                """);

            ProjectionTree projection = ProjectionTreeParser.parse("profile(bio)");

            MissingFieldException exception = assertThrows(
                MissingFieldException.class,
                () -> projector.project(input, projection, createContext())
            );

            assertTrue(exception.getPath().contains("bio"));
        }

        @Test
        void throwsOnMissingParentField() throws Exception {
            JsonNode input = objectMapper.readTree("""
                {"id": 1}
                """);

            ProjectionTree projection = ProjectionTreeParser.parse("profile(avatar)");

            MissingFieldException exception = assertThrows(
                MissingFieldException.class,
                () -> projector.project(input, projection, createContext())
            );

            assertTrue(exception.getPath().contains("profile"));
        }
    }

    @Nested
    @DisplayName("Null and edge cases")
    class NullAndEdgeCases {

        @Test
        void nullInputReturnsNull() {
            ProjectionTree projection = ProjectionTreeParser.parse("id");
            JsonNode result = projector.project(null, projection, createContext());
            assertNull(result);
        }

        @Test
        void nullFieldValuePreserved() throws Exception {
            JsonNode input = objectMapper.readTree("""
                {"id": 1, "name": null}
                """);

            ProjectionTree projection = ProjectionTreeParser.parse("id,name");
            JsonNode result = projector.project(input, projection, createContext());

            assertTrue(result.has("name"));
            assertTrue(result.get("name").isNull());
        }

        @Test
        void emptyObjectPreserved() throws Exception {
            JsonNode input = objectMapper.readTree("""
                {"id": 1, "metadata": {}}
                """);

            ProjectionTree projection = ProjectionTreeParser.parse("id,metadata");
            JsonNode result = projector.project(input, projection, createContext());

            assertTrue(result.has("metadata"));
            assertTrue(result.get("metadata").isObject());
            assertEquals(0, result.get("metadata").size());
        }

        @Test
        void emptyArrayPreserved() throws Exception {
            JsonNode input = objectMapper.readTree("""
                {"id": 1, "items": []}
                """);

            ProjectionTree projection = ProjectionTreeParser.parse("id,items");
            JsonNode result = projector.project(input, projection, createContext());

            assertTrue(result.has("items"));
            assertTrue(result.get("items").isArray());
            assertEquals(0, result.get("items").size());
        }
    }

    @Nested
    @DisplayName("Content type support")
    class ContentTypeSupport {

        @Test
        void supportsApplicationJson() {
            assertTrue(projector.supports("application/json"));
        }

        @Test
        void supportsApplicationJsonWithCharset() {
            assertTrue(projector.supports("application/json; charset=utf-8"));
        }

        @Test
        void doesNotSupportXml() {
            assertFalse(projector.supports("application/xml"));
        }

        @Test
        void doesNotSupportNull() {
            assertFalse(projector.supports(null));
        }
    }
}
