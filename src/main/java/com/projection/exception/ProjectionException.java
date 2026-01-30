package com.projection.exception;

/**
 * Base exception for all projection-related errors.
 * All projection exceptions are unchecked to allow clean propagation
 * through the ResponseBodyAdvice chain.
 */
public abstract class ProjectionException extends RuntimeException {

    private final String path;
    private final String errorCode;

    protected ProjectionException(String message, String path, String errorCode) {
        super(message);
        this.path = path;
        this.errorCode = errorCode;
    }

    protected ProjectionException(String message, String path, String errorCode, Throwable cause) {
        super(message, cause);
        this.path = path;
        this.errorCode = errorCode;
    }

    public String getPath() {
        return path;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public abstract int getHttpStatus();
}
