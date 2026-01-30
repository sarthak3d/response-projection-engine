package com.projection.core;

import com.projection.config.ProjectionProperties;
import com.projection.exception.CycleDetectedException;
import com.projection.exception.MaxDepthExceededException;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Per-request execution context for projection operations.
 * Tracks path, depth, visited nodes, and provides guardrails during traversal.
 * 
 * This class is intentionally free of HTTP, Spring, or security dependencies
 * to keep it testable and reusable.
 */
public final class FilterContext {

    private final String traceId;
    private final int maxDepth;
    private final boolean cycleDetectionEnabled;
    private final ProjectionProperties properties;

    private String currentPath;
    private int currentDepth;
    private final Set<String> visitedPaths;

    private FilterContext(Builder builder) {
        this.traceId = builder.traceId;
        this.maxDepth = builder.maxDepth;
        this.cycleDetectionEnabled = builder.cycleDetectionEnabled;
        this.properties = builder.properties;
        this.currentPath = "";
        this.currentDepth = 0;
        this.visitedPaths = new HashSet<>();
    }

    public String getTraceId() {
        return traceId;
    }

    public String getCurrentPath() {
        return currentPath;
    }

    public int getCurrentDepth() {
        return currentDepth;
    }

    public ProjectionProperties getProperties() {
        return properties;
    }

    public void descend(String fieldName) {
        currentDepth++;
        
        if (currentPath.isEmpty()) {
            currentPath = fieldName;
        } else {
            currentPath = currentPath + "." + fieldName;
        }

        if (currentDepth > maxDepth) {
            throw new MaxDepthExceededException(
                currentPath, 
                maxDepth, 
                currentDepth, 
                properties.getError().getMaxDepth().getStatus()
            );
        }

        if (cycleDetectionEnabled) {
            if (visitedPaths.contains(currentPath)) {
                throw new CycleDetectedException(
                    currentPath, 
                    properties.getError().getCycle().getStatus()
                );
            }
            visitedPaths.add(currentPath);
        }
    }

    public void ascend() {
        if (cycleDetectionEnabled && !currentPath.isEmpty()) {
            visitedPaths.remove(currentPath);
        }

        if (currentDepth > 0) {
            currentDepth--;
        }

        int lastDot = currentPath.lastIndexOf('.');
        if (lastDot > 0) {
            currentPath = currentPath.substring(0, lastDot);
        } else {
            currentPath = "";
        }
    }

    public String buildPath(String fieldName) {
        if (currentPath.isEmpty()) {
            return fieldName;
        }
        return currentPath + "." + fieldName;
    }

    public static Builder builder(ProjectionProperties properties) {
        return new Builder(properties);
    }

    public static class Builder {
        private final ProjectionProperties properties;
        private String traceId;
        private int maxDepth;
        private boolean cycleDetectionEnabled;

        private Builder(ProjectionProperties properties) {
            this.properties = properties;
            this.maxDepth = properties.getMaxDepth();
            this.cycleDetectionEnabled = properties.getCycleDetection().isEnabled();
            this.traceId = properties.getTraceId().isEnabled() 
                ? UUID.randomUUID().toString().substring(0, 8) 
                : null;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder maxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
            return this;
        }

        public Builder cycleDetectionEnabled(boolean enabled) {
            this.cycleDetectionEnabled = enabled;
            return this;
        }

        public FilterContext build() {
            return new FilterContext(this);
        }
    }
}
