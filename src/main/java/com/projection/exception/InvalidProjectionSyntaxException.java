package com.projection.exception;

/**
 * Thrown when the projection DSL syntax is invalid.
 * Provides detailed error location for debugging.
 */
public class InvalidProjectionSyntaxException extends ProjectionException {

    private static final String ERROR_CODE = "INVALID_PROJECTION_SYNTAX";
    private final String projection;
    private final int position;

    public InvalidProjectionSyntaxException(String projection, int position, String details) {
        super(
            String.format("Invalid projection syntax at position %d: %s. Input: %s", 
                position, details, projection),
            "",
            ERROR_CODE
        );
        this.projection = projection;
        this.position = position;
    }

    public String getProjection() {
        return projection;
    }

    public int getPosition() {
        return position;
    }

    @Override
    public int getHttpStatus() {
        return 400;
    }
}
