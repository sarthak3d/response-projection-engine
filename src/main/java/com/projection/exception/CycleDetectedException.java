package com.projection.exception;

/**
 * Thrown when a cyclic reference is detected during projection traversal.
 * Prevents infinite loops in object graphs with circular references.
 */
public class CycleDetectedException extends ProjectionException {

    private static final String ERROR_CODE = "CYCLE_DETECTED";
    private final int httpStatus;

    public CycleDetectedException(String cyclePath, int httpStatus) {
        super(
            String.format("Cyclic reference detected at path: %s", cyclePath),
            cyclePath,
            ERROR_CODE
        );
        this.httpStatus = httpStatus;
    }

    @Override
    public int getHttpStatus() {
        return httpStatus;
    }
}
