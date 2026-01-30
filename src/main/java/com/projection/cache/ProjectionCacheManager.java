package com.projection.cache;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.projection.config.ProjectionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Manages caching of full backend responses.
 * 
 * Responsibilities:
 * - TTL-based expiration
 * - ETag generation and validation
 * - Manual eviction by path pattern
 * - Thread-safe cache operations
 */
public class ProjectionCacheManager {

    private static final Logger log = LoggerFactory.getLogger(ProjectionCacheManager.class);

    private final ProjectionProperties properties;
    private final Cache<String, CachedResponse> cache;
    private final boolean conditionalEnabled;

    public ProjectionCacheManager(ProjectionProperties properties) {
        this.properties = properties;
        this.conditionalEnabled = properties.getCache().getConditional().isEnabled();
        
        this.cache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofSeconds(properties.getCache().getDefaultTtlSeconds() * 2L))
            .build();
    }

    public Optional<CachedResponse> get(CacheKey key) {
        if (!properties.getCache().isEnabled()) {
            return Optional.empty();
        }

        CachedResponse cached = cache.getIfPresent(key.getKey());
        if (cached == null) {
            return Optional.empty();
        }

        if (cached.isExpired()) {
            cache.invalidate(key.getKey());
            log.debug("Cache entry expired: {}", key);
            return Optional.empty();
        }

        log.debug("Cache hit: {}", key);
        return Optional.of(cached);
    }

    public void put(CacheKey key, JsonNode response, int ttlSeconds, boolean isCollection) {
        if (!properties.getCache().isEnabled()) {
            return;
        }

        int effectiveTtl = ttlSeconds > 0 ? ttlSeconds : 
            (isCollection ? properties.getCache().getCollectionTtlSeconds() 
                         : properties.getCache().getDefaultTtlSeconds());

        CachedResponse.Builder builder = CachedResponse.builder()
            .fullResponse(response)
            .cachedAt(Instant.now())
            .ttlSeconds(effectiveTtl);

        if (conditionalEnabled) {
            String etag = generateEtag(response);
            builder.etag(etag).lastModified(Instant.now());
        }

        cache.put(key.getKey(), builder.build());
        log.debug("Cached response: {} (TTL: {}s)", key, effectiveTtl);
    }

    public boolean validateEtag(CacheKey key, String clientEtag) {
        if (!conditionalEnabled || clientEtag == null) {
            return false;
        }

        return get(key)
            .map(CachedResponse::getEtag)
            .map(serverEtag -> serverEtag.equals(normalizeEtag(clientEtag)))
            .orElse(false);
    }

    public boolean validateLastModified(CacheKey key, Instant clientLastModified) {
        if (!conditionalEnabled || clientLastModified == null) {
            return false;
        }

        return get(key)
            .map(CachedResponse::getLastModified)
            .map(serverLastModified -> !clientLastModified.isBefore(serverLastModified))
            .orElse(false);
    }

    public void evict(CacheKey key) {
        if (!properties.getCache().isEnabled()) {
            return;
        }

        cache.invalidate(key.getKey());
        log.debug("Evicted cache entry: {}", key);
    }

    public void evictByPathPattern(String pathPattern) {
        if (!properties.getCache().isEnabled() || 
            !properties.getCache().getManualEviction().isEnabled()) {
            return;
        }

        String regexPattern = pathPattern
            .replace("{", "(?<")
            .replace("}", ">[^/]+)")
            .replace("/", "\\/");

        cache.asMap().keySet().removeIf(key -> {
            String pathPart = extractPathFromKey(key);
            boolean matches = pathPart.matches(regexPattern);
            if (matches) {
                log.debug("Evicted by pattern '{}': {}", pathPattern, key);
            }
            return matches;
        });
    }

    public void evictAll() {
        cache.invalidateAll();
        log.debug("Evicted all cache entries");
    }

    public long size() {
        return cache.estimatedSize();
    }

    private String generateEtag(JsonNode response) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(response.toString().getBytes());
            return "\"" + HexFormat.of().formatHex(hash) + "\"";
        } catch (NoSuchAlgorithmException e) {
            return "\"" + response.hashCode() + "\"";
        }
    }

    private String normalizeEtag(String etag) {
        if (etag == null) {
            return null;
        }
        String normalized = etag.trim();
        if (normalized.startsWith("W/")) {
            normalized = normalized.substring(2);
        }
        return normalized;
    }

    private String extractPathFromKey(String key) {
        int colonIndex = key.indexOf(':');
        if (colonIndex < 0) {
            return key;
        }
        String pathAndQuery = key.substring(colonIndex + 1);
        int queryIndex = pathAndQuery.indexOf('?');
        return queryIndex > 0 ? pathAndQuery.substring(0, queryIndex) : pathAndQuery;
    }
}
