package com.projection.error;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Minimal error response format for projection failures.
 * Errors always pass through untouched - this is only for projection-specific errors.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ProjectionErrorResponse {

    private final Error error;

    private ProjectionErrorResponse(Error error) {
        this.error = error;
    }

    public static ProjectionErrorResponse of(String code, String message, String path, String traceId) {
        return new ProjectionErrorResponse(new Error(code, message, path, traceId));
    }

    public Error getError() {
        return error;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Error(
        String code,
        String message,
        String path,
        String traceId
    ) {}
}
