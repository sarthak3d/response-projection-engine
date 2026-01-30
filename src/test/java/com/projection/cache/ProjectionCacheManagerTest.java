package com.projection.cache;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projection.config.ProjectionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ProjectionCacheManagerTest {

    private ObjectMapper objectMapper;
    private ProjectionProperties properties;
    private ProjectionCacheManager cacheManager;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        properties = new ProjectionProperties();
        properties.getCache().setDefaultTtlSeconds(60);
        cacheManager = new ProjectionCacheManager(properties);
    }

    @Nested
    @DisplayName("Basic cache operations")
    class BasicOperations {

        @Test
        void putAndGet() throws Exception {
            JsonNode response = objectMapper.readTree("""
                {"id": 1, "name": "Test"}
                """);

            CacheKey key = CacheKey.of("GET", "/users/1", null);
            cacheManager.put(key, response, -1, false);

            Optional<CachedResponse> cached = cacheManager.get(key);
            assertTrue(cached.isPresent());
            assertEquals(response, cached.get().getFullResponse());
        }

        @Test
        void getMissingKeyReturnsEmpty() {
            CacheKey key = CacheKey.of("GET", "/nonexistent", null);
            Optional<CachedResponse> cached = cacheManager.get(key);
            assertFalse(cached.isPresent());
        }

        @Test
        void evictRemovesEntry() throws Exception {
            JsonNode response = objectMapper.readTree("""
                {"id": 1}
                """);

            CacheKey key = CacheKey.of("GET", "/users/1", null);
            cacheManager.put(key, response, -1, false);
            
            cacheManager.evict(key);
            
            Optional<CachedResponse> cached = cacheManager.get(key);
            assertFalse(cached.isPresent());
        }

        @Test
        void evictAllClearsCache() throws Exception {
            JsonNode response = objectMapper.readTree("""
                {"id": 1}
                """);

            cacheManager.put(CacheKey.of("GET", "/users/1", null), response, -1, false);
            cacheManager.put(CacheKey.of("GET", "/users/2", null), response, -1, false);
            
            cacheManager.evictAll();
            
            assertEquals(0, cacheManager.size());
        }
    }

    @Nested
    @DisplayName("Cache key normalization")
    class CacheKeyNormalization {

        @Test
        void normalizesSortedQueryParams() {
            CacheKey key1 = CacheKey.of("GET", "/users", "b=2&a=1");
            CacheKey key2 = CacheKey.of("GET", "/users", "a=1&b=2");
            
            assertEquals(key1.getKey(), key2.getKey());
        }

        @Test
        void methodIsCaseInsensitive() {
            CacheKey key1 = CacheKey.of("get", "/users", null);
            CacheKey key2 = CacheKey.of("GET", "/users", null);
            
            assertEquals(key1.getKey(), key2.getKey());
        }

        @Test
        void pathNormalizesTrailingSlash() {
            CacheKey key1 = CacheKey.of("GET", "/users/", null);
            CacheKey key2 = CacheKey.of("GET", "/users", null);
            
            assertEquals(key1.getKey(), key2.getKey());
        }
    }

    @Nested
    @DisplayName("TTL expiration")
    class TtlExpiration {

        @Test
        void expiredEntryNotReturned() throws Exception {
            properties.getCache().setDefaultTtlSeconds(0);
            cacheManager = new ProjectionCacheManager(properties);

            JsonNode response = objectMapper.readTree("""
                {"id": 1}
                """);

            CacheKey key = CacheKey.of("GET", "/users/1", null);
            cacheManager.put(key, response, 0, false);

            Thread.sleep(50);

            Optional<CachedResponse> cached = cacheManager.get(key);
            assertFalse(cached.isPresent());
        }

        @Test
        void collectionUsesCollectionTtl() throws Exception {
            properties.getCache().setCollectionTtlSeconds(10);
            cacheManager = new ProjectionCacheManager(properties);

            JsonNode response = objectMapper.readTree("""
                [{"id": 1}]
                """);

            CacheKey key = CacheKey.of("GET", "/users", null);
            cacheManager.put(key, response, -1, true);

            Optional<CachedResponse> cached = cacheManager.get(key);
            assertTrue(cached.isPresent());
        }
    }

    @Nested
    @DisplayName("Pattern-based eviction")
    class PatternBasedEviction {

        @Test
        void evictByExactPath() throws Exception {
            JsonNode response = objectMapper.readTree("""
                {"id": 1}
                """);

            cacheManager.put(CacheKey.of("GET", "/users/1", null), response, -1, false);
            cacheManager.put(CacheKey.of("GET", "/users/2", null), response, -1, false);
            
            cacheManager.evictByPathPattern("/users/1");
            
            assertFalse(cacheManager.get(CacheKey.of("GET", "/users/1", null)).isPresent());
            assertTrue(cacheManager.get(CacheKey.of("GET", "/users/2", null)).isPresent());
        }
    }

    @Nested
    @DisplayName("ETag support")
    class EtagSupport {

        @Test
        void generatesEtag() throws Exception {
            JsonNode response = objectMapper.readTree("""
                {"id": 1}
                """);

            CacheKey key = CacheKey.of("GET", "/users/1", null);
            cacheManager.put(key, response, -1, false);

            Optional<CachedResponse> cached = cacheManager.get(key);
            assertTrue(cached.isPresent());
            assertNotNull(cached.get().getEtag());
        }

        @Test
        void validateMatchingEtag() throws Exception {
            JsonNode response = objectMapper.readTree("""
                {"id": 1}
                """);

            CacheKey key = CacheKey.of("GET", "/users/1", null);
            cacheManager.put(key, response, -1, false);

            String etag = cacheManager.get(key).get().getEtag();
            assertTrue(cacheManager.validateEtag(key, etag));
        }

        @Test
        void nonMatchingEtagReturnsFalse() throws Exception {
            JsonNode response = objectMapper.readTree("""
                {"id": 1}
                """);

            CacheKey key = CacheKey.of("GET", "/users/1", null);
            cacheManager.put(key, response, -1, false);

            assertFalse(cacheManager.validateEtag(key, "\"wrong-etag\""));
        }
    }

    @Nested
    @DisplayName("Cache disabled")
    class CacheDisabled {

        @Test
        void putDoesNothingWhenDisabled() throws Exception {
            properties.getCache().setEnabled(false);
            cacheManager = new ProjectionCacheManager(properties);

            JsonNode response = objectMapper.readTree("""
                {"id": 1}
                """);

            CacheKey key = CacheKey.of("GET", "/users/1", null);
            cacheManager.put(key, response, -1, false);

            assertEquals(0, cacheManager.size());
        }

        @Test
        void getReturnsEmptyWhenDisabled() {
            properties.getCache().setEnabled(false);
            cacheManager = new ProjectionCacheManager(properties);

            CacheKey key = CacheKey.of("GET", "/users/1", null);
            Optional<CachedResponse> cached = cacheManager.get(key);
            
            assertFalse(cached.isPresent());
        }
    }
}
