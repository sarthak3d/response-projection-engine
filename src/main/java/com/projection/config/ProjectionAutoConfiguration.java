package com.projection.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projection.advice.CacheEvictionAspect;
import com.projection.advice.ProjectionResponseBodyAdvice;
import com.projection.cache.ProjectionCacheManager;
import com.projection.interceptor.ProjectionCacheInterceptor;
import com.projection.projector.JsonResponseProjector;
import com.projection.projector.ResponseProjector;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Auto-configuration for the Response Projection Framework.
 * Activated when response.projection.enabled=true (default).
 * 
 * Architecture:
 *   - ProjectionCacheInterceptor: Handles cache hits (bypasses controller)
 *   - ProjectionResponseBodyAdvice: Handles cache misses (caches response after controller)
 */
@AutoConfiguration
@EnableConfigurationProperties(ProjectionProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "response.projection", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableAspectJAutoProxy
public class ProjectionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ProjectionCacheManager projectionCacheManager(ProjectionProperties properties) {
        return new ProjectionCacheManager(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ResponseProjector jsonResponseProjector(ProjectionProperties properties) {
        return new JsonResponseProjector(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProjectionCacheInterceptor projectionCacheInterceptor(
            ProjectionProperties properties,
            ObjectMapper objectMapper,
            ResponseProjector projector,
            ProjectionCacheManager cacheManager) {
        return new ProjectionCacheInterceptor(properties, objectMapper, projector, cacheManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProjectionWebMvcConfigurer projectionWebMvcConfigurer(
            ProjectionCacheInterceptor cacheInterceptor) {
        return new ProjectionWebMvcConfigurer(cacheInterceptor);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProjectionResponseBodyAdvice projectionResponseBodyAdvice(
            ProjectionProperties properties,
            ObjectMapper objectMapper,
            ResponseProjector projector,
            ProjectionCacheManager cacheManager) {
        return new ProjectionResponseBodyAdvice(properties, objectMapper, projector, cacheManager);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "response.projection.cache.manual-eviction", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CacheEvictionAspect cacheEvictionAspect(
            ProjectionCacheManager cacheManager,
            ProjectionProperties properties) {
        return new CacheEvictionAspect(cacheManager, properties);
    }
}
