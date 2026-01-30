package com.projection.exception;

/**
 * Thrown when a requested field does not exist in the response object.
 * This enforces strict projection behavior - partial success is not allowed.
 */
public class MissingFieldException extends ProjectionException {

    private static final String ERROR_CODE = "MISSING_FIELD";
    private final int httpStatus;

    public MissingFieldException(String fieldPath, int httpStatus) {
        super(
            String.format("Requested field does not exist in response: %s", fieldPath),
            fieldPath,
            ERROR_CODE
        );
        this.httpStatus = httpStatus;
    }

    @Override
    public int getHttpStatus() {
        return httpStatus;
    }
}
