package com.projection.exception;

/**
 * Thrown when a requested field is not in the allowlist defined by @Projectable.allowedFields.
 * Prevents exposure of fields that the endpoint author has explicitly disallowed.
 */
public class FieldNotAllowedException extends ProjectionException {

    private static final String ERROR_CODE = "FIELD_NOT_ALLOWED";

    public FieldNotAllowedException(String fieldPath) {
        super(
            String.format("Field is not allowed for projection: %s", fieldPath),
            fieldPath,
            ERROR_CODE
        );
    }

    @Override
    public int getHttpStatus() {
        return 400;
    }
}
