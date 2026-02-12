package com.projection.config;

import com.projection.interceptor.ProjectionCacheInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration for the Response Projection Framework.
 * Registers the cache interceptor to handle cache hits before controller execution.
 */
public class ProjectionWebMvcConfigurer implements WebMvcConfigurer {

    private final ProjectionCacheInterceptor cacheInterceptor;

    public ProjectionWebMvcConfigurer(ProjectionCacheInterceptor cacheInterceptor) {
        this.cacheInterceptor = cacheInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(cacheInterceptor)
                .addPathPatterns("/**");
    }
}
