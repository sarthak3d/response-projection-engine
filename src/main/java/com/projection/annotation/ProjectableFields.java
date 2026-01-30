package com.projection.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Restricts which fields can be projected from an endpoint's response.
 * When present, only the specified fields are allowed in projection requests.
 * When absent, all fields in the response are projectable.
 * 
 * This provides an allowlist mechanism for sensitive endpoints where
 * certain fields should never be exposed regardless of client requests.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ProjectableFields {

    /**
     * Array of allowed field paths that clients can request.
     * Uses the same DSL syntax as the projection header.
     * 
     * Examples:
     * - Simple fields: {"id", "name", "email"}
     * - Nested fields: {"profile(avatar,bio)", "orders(id,total)"}
     */
    String[] value();
}
