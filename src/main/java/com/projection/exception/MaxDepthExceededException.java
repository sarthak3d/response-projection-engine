package com.projection.exception;

/**
 * Thrown when projection traversal exceeds the configured maximum depth.
 * Prevents stack overflow and excessive processing on deeply nested structures.
 */
public class MaxDepthExceededException extends ProjectionException {

    private static final String ERROR_CODE = "MAX_DEPTH_EXCEEDED";
    private final int maxDepth;
    private final int actualDepth;
    private final int httpStatus;

    public MaxDepthExceededException(String path, int maxDepth, int actualDepth, int httpStatus) {
        super(
            String.format("Projection depth %d exceeds maximum allowed depth of %d at path: %s", 
                actualDepth, maxDepth, path),
            path,
            ERROR_CODE
        );
        this.maxDepth = maxDepth;
        this.actualDepth = actualDepth;
        this.httpStatus = httpStatus;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public int getActualDepth() {
        return actualDepth;
    }

    @Override
    public int getHttpStatus() {
        return httpStatus;
    }
}
