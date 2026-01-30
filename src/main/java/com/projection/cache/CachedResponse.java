package com.projection.cache;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * Cached response entry containing the full backend response.
 * Projected variants are never cached - only the complete response.
 */
public final class CachedResponse {

    private final JsonNode fullResponse;
    private final String etag;
    private final Instant lastModified;
    private final Instant cachedAt;
    private final Instant expiresAt;

    private CachedResponse(Builder builder) {
        this.fullResponse = builder.fullResponse;
        this.etag = builder.etag;
        this.lastModified = builder.lastModified;
        this.cachedAt = builder.cachedAt != null ? builder.cachedAt : Instant.now();
        this.expiresAt = builder.expiresAt;
    }

    public JsonNode getFullResponse() {
        return fullResponse;
    }

    public String getEtag() {
        return etag;
    }

    public Instant getLastModified() {
        return lastModified;
    }

    public Instant getCachedAt() {
        return cachedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired() {
        if (expiresAt == null) {
            return false;
        }
        return Instant.now().isAfter(expiresAt);
    }

    public boolean hasConditionalHeaders() {
        return etag != null || lastModified != null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private JsonNode fullResponse;
        private String etag;
        private Instant lastModified;
        private Instant cachedAt;
        private Instant expiresAt;

        public Builder fullResponse(JsonNode fullResponse) {
            this.fullResponse = fullResponse;
            return this;
        }

        public Builder etag(String etag) {
            this.etag = etag;
            return this;
        }

        public Builder lastModified(Instant lastModified) {
            this.lastModified = lastModified;
            return this;
        }

        public Builder cachedAt(Instant cachedAt) {
            this.cachedAt = cachedAt;
            return this;
        }

        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder ttlSeconds(int ttlSeconds) {
            this.expiresAt = Instant.now().plusSeconds(ttlSeconds);
            return this;
        }

        public CachedResponse build() {
            return new CachedResponse(this);
        }
    }
}
