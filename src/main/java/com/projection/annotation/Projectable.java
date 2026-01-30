package com.projection.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an endpoint as eligible for response projection and caching.
 * When applied to a controller method, the library will:
 * 1. Check for projection headers in the request
 * 2. Apply field filtering based on the projection DSL
 * 3. Cache the full response for subsequent requests
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Projectable {

    /**
     * TTL override for this specific endpoint's cache entries.
     * If not specified, the global default TTL from configuration is used.
     * Value in seconds. Use -1 to inherit from configuration.
     */
    int ttlSeconds() default -1;

    /**
     * Whether this endpoint returns a collection.
     * Collections may have different TTL settings.
     */
    boolean collection() default false;
}
