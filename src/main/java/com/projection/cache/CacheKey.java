package com.projection.cache;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Normalized cache key for projection cache entries.
 * Format: METHOD:/path?sortedQueryParams
 * 
 * Query parameters are sorted alphabetically to ensure consistent cache hits
 * regardless of parameter order in the original request.
 */
public final class CacheKey {

    private final String method;
    private final String path;
    private final String normalizedQuery;
    private final String key;

    private CacheKey(String method, String path, String queryString) {
        this.method = method.toUpperCase();
        this.path = normalizePath(path);
        this.normalizedQuery = normalizeQuery(queryString);
        this.key = buildKey();
    }

    public static CacheKey of(String method, String path, String queryString) {
        return new CacheKey(method, path, queryString);
    }

    public String getKey() {
        return key;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        String normalized = path.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String normalizeQuery(String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return "";
        }

        return Arrays.stream(queryString.split("&"))
            .filter(param -> !param.isBlank())
            .sorted()
            .collect(Collectors.joining("&"));
    }

    private String buildKey() {
        StringBuilder sb = new StringBuilder();
        sb.append(method).append(":").append(path);
        if (!normalizedQuery.isEmpty()) {
            sb.append("?").append(normalizedQuery);
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CacheKey cacheKey = (CacheKey) o;
        return Objects.equals(key, cacheKey.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }

    @Override
    public String toString() {
        return key;
    }
}
