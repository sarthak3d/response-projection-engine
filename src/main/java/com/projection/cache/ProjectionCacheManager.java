package com.projection.cache;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.projection.config.ProjectionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

        Pattern compiledPattern = buildPathRegex(pathPattern);

        cache.asMap().keySet().removeIf(key -> {
            String pathPart = extractPathFromKey(key);
            boolean matches = compiledPattern.matcher(pathPart).matches();
            if (matches) {
                log.debug("Evicted by pattern '{}': {}", pathPattern, key);
            }
            return matches;
        });
    }

    /**
     * Builds a regex pattern from a path template, properly escaping literal segments
     * and converting {param} placeholders to capturing groups.
     * 
     * Example: "/users/{id}/orders" becomes "^\Q/users/\E(?<id>[^/]+)\Q/orders\E$"
     */
    private Pattern buildPathRegex(String pathPattern) {
        StringBuilder regex = new StringBuilder("^");
        Matcher matcher = Pattern.compile("\\{([^}]+)}").matcher(pathPattern);
        int lastEnd = 0;

        while (matcher.find()) {
            // Quote the literal segment before this placeholder
            if (matcher.start() > lastEnd) {
                String literal = pathPattern.substring(lastEnd, matcher.start());
                regex.append(Pattern.quote(literal));
            }
            
            // Add capturing group for the parameter
            String paramName = matcher.group(1);
            String sanitizedName = sanitizeGroupName(paramName);
            
            if (sanitizedName != null) {
                // Use named capturing group with sanitized name
                regex.append("(?<").append(sanitizedName).append(">[^/]+)");
            } else {
                // Fall back to anonymous group if name is invalid
                regex.append("([^/]+)");
            }
            
            lastEnd = matcher.end();
        }

        // Quote any remaining literal segment after the last placeholder
        if (lastEnd < pathPattern.length()) {
            String literal = pathPattern.substring(lastEnd);
            regex.append(Pattern.quote(literal));
        }

        regex.append("$");
        return Pattern.compile(regex.toString());
    }

    /**
     * Sanitizes a parameter name for use as a regex named capturing group.
     * Java regex named groups must match [a-zA-Z][a-zA-Z0-9]*.
     * 
     * @param name the original parameter name
     * @return sanitized name valid for regex groups, or null if cannot be sanitized
     */
    private String sanitizeGroupName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        
        // Remove invalid characters (keep only ASCII letters and digits)
        StringBuilder sanitized = new StringBuilder();
        for (char c : name.toCharArray()) {
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                sanitized.append(c);
            }
        }
        
        if (sanitized.isEmpty()) {
            return null;
        }
        
        // Ensure first character is an ASCII letter
        char first = sanitized.charAt(0);
        if (!((first >= 'A' && first <= 'Z') || (first >= 'a' && first <= 'z'))) {
            sanitized.insert(0, 'p'); // prefix with 'p' for "param"
        }
        
        String result = sanitized.toString();
        
        // Final validation against ASCII pattern
        // Java regex named groups must match [a-zA-Z][a-zA-Z0-9]*
        if (!result.matches("[A-Za-z][A-Za-z0-9]*")) {
            return null;
        }
        
        return result;
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
            byte[] hash = digest.digest(response.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(response.hashCode());
        }
    }

    private String normalizeEtag(String etag) {
        if (etag == null || etag.isBlank()) {
            return null;
        }
        
        String normalized = etag.trim();
        
        // Remove weak validator prefix if present
        if (normalized.startsWith("W/")) {
            normalized = normalized.substring(2).trim();
        }
        
        // Strip surrounding double quotes if present
        if (normalized.length() >= 2 
                && normalized.startsWith("\"") 
                && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
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
