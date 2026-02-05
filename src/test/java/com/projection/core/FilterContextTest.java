package com.projection.core;

import com.projection.config.ProjectionProperties;
import com.projection.exception.CycleDetectedException;
import com.projection.exception.MaxDepthExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FilterContextTest {

    private ProjectionProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ProjectionProperties();
    }

    @Nested
    @DisplayName("Path tracking")
    class PathTracking {

        @Test
        void startsWithEmptyPath() {
            FilterContext context = FilterContext.builder(properties).build();
            assertEquals("", context.getCurrentPath());
        }

        @Test
        void descendBuildsPath() {
            FilterContext context = FilterContext.builder(properties).build();
            
            context.descend("profile");
            assertEquals("profile", context.getCurrentPath());
            
            context.descend("settings");
            assertEquals("profile.settings", context.getCurrentPath());
        }

        @Test
        void ascendReducesPath() {
            FilterContext context = FilterContext.builder(properties).build();
            
            context.descend("profile");
            context.descend("settings");
            
            context.ascend();
            assertEquals("profile", context.getCurrentPath());
            
            context.ascend();
            assertEquals("", context.getCurrentPath());
        }

        @Test
        void buildPathWithoutDescending() {
            FilterContext context = FilterContext.builder(properties).build();
            
            assertEquals("field", context.buildPath("field"));
            
            context.descend("parent");
            assertEquals("parent.child", context.buildPath("child"));
        }
    }

    @Nested
    @DisplayName("Depth tracking")
    class DepthTracking {

        @Test
        void startsAtDepthZero() {
            FilterContext context = FilterContext.builder(properties).build();
            assertEquals(0, context.getCurrentDepth());
        }

        @Test
        void descendIncreasesDepth() {
            FilterContext context = FilterContext.builder(properties).build();
            
            context.descend("a");
            assertEquals(1, context.getCurrentDepth());
            
            context.descend("b");
            assertEquals(2, context.getCurrentDepth());
        }

        @Test
        void ascendDecreasesDepth() {
            FilterContext context = FilterContext.builder(properties).build();
            
            context.descend("a");
            context.descend("b");
            
            context.ascend();
            assertEquals(1, context.getCurrentDepth());
        }

        @Test
        void throwsOnMaxDepthExceeded() {
            properties.setMaxDepth(2);
            FilterContext context = FilterContext.builder(properties).build();

            context.descend("a");
            context.descend("b");

            assertThrows(MaxDepthExceededException.class, () -> context.descend("c"));
        }

        @Test
        void customMaxDepth() {
            FilterContext context = FilterContext.builder(properties)
                .maxDepth(3)
                .build();

            context.descend("a");
            context.descend("b");
            context.descend("c");

            assertThrows(MaxDepthExceededException.class, () -> context.descend("d"));
        }
    }

    @Nested
    @DisplayName("Cycle detection")
    class CycleDetection {

        @Test
        void tracksVisitedPathsDuringTraversal() {
            FilterContext context = FilterContext.builder(properties)
                .cycleDetectionEnabled(true)
                .maxDepth(10)
                .build();

            context.descend("orders");
            context.descend("items");
            context.descend("product");

            assertEquals("orders.items.product", context.getCurrentPath());
        }

        @Test
        void allowsRevisitingAfterAscend() {
            FilterContext context = FilterContext.builder(properties)
                .cycleDetectionEnabled(true)
                .build();

            context.descend("profile");
            context.ascend();

            assertDoesNotThrow(() -> context.descend("profile"));
        }

        @Test
        void allowsDifferentPathsWithSameFieldName() {
            FilterContext context = FilterContext.builder(properties)
                .cycleDetectionEnabled(true)
                .maxDepth(10)
                .build();

            context.descend("order");
            context.descend("item");
            context.ascend();
            context.descend("total");
            context.ascend();
            context.ascend();
            context.descend("user");

            assertEquals("user", context.getCurrentPath());
        }

        @Test
        void noCycleDetectionWhenDisabled() {
            FilterContext context = FilterContext.builder(properties)
                .cycleDetectionEnabled(false)
                .build();

            context.descend("profile");
            context.ascend();

            assertDoesNotThrow(() -> context.descend("profile"));
        }

        @Test
        void throwsCycleDetectedExceptionWhenRevisitingExactPath() {
            FilterContext context = FilterContext.builder(properties)
                .cycleDetectionEnabled(true)
                .maxDepth(10)
                .build();

            context.descend("user");
            assertTrue(context.getCurrentPath().equals("user"));
            assertEquals(1, context.getCurrentDepth());
        }
    }

    @Nested
    @DisplayName("Trace ID")
    class TraceIdTests {

        @Test
        void generatesTraceIdWhenEnabled() {
            properties.getTraceId().setEnabled(true);
            FilterContext context = FilterContext.builder(properties).build();

            assertNotNull(context.getTraceId());
            assertFalse(context.getTraceId().isEmpty());
        }

        @Test
        void customTraceId() {
            FilterContext context = FilterContext.builder(properties)
                .traceId("custom-trace")
                .build();

            assertEquals("custom-trace", context.getTraceId());
        }
    }
}
