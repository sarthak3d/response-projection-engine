package com.projection.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares cache eviction for projection cache entries after successful execution.
 * Applied to write operations (POST, PUT, DELETE) that modify data
 * cached by @Projectable endpoints.
 * 
 * Path variables are resolved at runtime using method parameters.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface InvalidateProjectionCache {

    /**
     * Cache paths to invalidate after successful method execution.
     * Supports path variable placeholders that are resolved at runtime.
     * 
     * Examples:
     * - Fixed path: {"/users"}
     * - With variable: {"/users/{id}"}
     * - Multiple paths: {"/users/{id}", "/users"}
     */
    String[] paths();
}
