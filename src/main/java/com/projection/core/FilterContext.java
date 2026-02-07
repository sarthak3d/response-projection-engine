package com.projection.core;

import com.projection.config.ProjectionProperties;
import com.projection.exception.CycleDetectedException;
import com.projection.exception.MaxDepthExceededException;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Per-request execution context for projection operations.
 * Tracks path, depth, visited nodes, and provides guardrails during traversal.
 * 
 * Uses lazy path construction to avoid string concatenation during successful traversals.
 * The full path string is only materialized when needed for error messages.
 */
public final class FilterContext {

    private final String traceId;
    private final int maxDepth;
    private final boolean cycleDetectionEnabled;
    private final ProjectionProperties properties;

    private final Deque<String> pathStack;
    private final Set<String> visitedPaths;

    private FilterContext(Builder builder) {
        this.traceId = builder.traceId;
        this.maxDepth = builder.maxDepth;
        this.cycleDetectionEnabled = builder.cycleDetectionEnabled;
        this.properties = builder.properties;
        this.pathStack = new ArrayDeque<>();
        this.visitedPaths = new HashSet<>();
    }

    public String getTraceId() {
        return traceId;
    }

    /**
     * Materializes the current path from the stack.
     * This is the "lazy" part - we only build the string when requested.
     */
    public String getCurrentPath() {
        return materializePath();
    }

    public int getCurrentDepth() {
        return pathStack.size();
    }

    public ProjectionProperties getProperties() {
        return properties;
    }

    /**
     * Descends into a nested field, pushing it onto the path stack.
     * Validates depth and cycle constraints before any state mutation.
     */
    public void descend(String fieldName) {
        if (fieldName == null || fieldName.trim().isEmpty()) {
            throw new IllegalArgumentException("fieldName must not be null or blank");
        }

        int newDepth = pathStack.size() + 1;
        
        if (newDepth > maxDepth) {
            String errorPath = buildPath(fieldName);
            throw new MaxDepthExceededException(
                errorPath, 
                maxDepth, 
                newDepth, 
                properties.getError().getMaxDepth().getStatus()
            );
        }

        if (cycleDetectionEnabled) {
            String prospectivePath = buildPath(fieldName);
            if (visitedPaths.contains(prospectivePath)) {
                throw new CycleDetectedException(
                    prospectivePath, 
                    properties.getError().getCycle().getStatus()
                );
            }
            visitedPaths.add(prospectivePath);
        }

        pathStack.push(fieldName);
    }

    /**
     * Ascends from a nested field, popping it from the path stack.
     */
    public void ascend() {
        if (pathStack.isEmpty()) {
            return;
        }

        if (cycleDetectionEnabled) {
            String currentPath = materializePath();
            visitedPaths.remove(currentPath);
        }

        pathStack.pop();
    }

    /**
     * Builds a full path by appending the given field name to the current path.
     * This is used for error messages when a field is missing.
     * The path is computed immediately to preserve context before any state changes.
     * 
     * @throws IllegalArgumentException if fieldName is null or blank
     */
    public String buildPath(String fieldName) {
        if (fieldName == null || fieldName.trim().isEmpty()) {
            throw new IllegalArgumentException("fieldName must not be null or blank");
        }
        if (pathStack.isEmpty()) {
            return fieldName;
        }
        return materializePath() + "." + fieldName;
    }

    /**
     * Materializes the path stack into a dot-separated string.
     * Uses a StringBuilder for efficient concatenation.
     * Returns an empty string if the stack is empty.
     */
    private String materializePath() {
        if (pathStack.isEmpty()) {
            return "";
        }

        Object[] segments = pathStack.toArray();
        int length = segments.length;
        
        StringBuilder sb = new StringBuilder();
        for (int i = length - 1; i >= 0; i--) {
            if (sb.length() > 0) {
                sb.append('.');
            }
            sb.append(segments[i]);
        }
        return sb.toString();
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
            if (properties == null) {
                throw new IllegalArgumentException("properties must not be null");
            }
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
