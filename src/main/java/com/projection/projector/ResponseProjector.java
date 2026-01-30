package com.projection.projector;

import com.fasterxml.jackson.databind.JsonNode;
import com.projection.core.FilterContext;
import com.projection.core.ProjectionTree;

/**
 * Internal SPI for response projection.
 * Isolates projection mechanism from library logic.
 * 
 * This is NOT a plugin system - implementations are internal only.
 * The interface exists for clean separation and potential future format support.
 */
public interface ResponseProjector {

    /**
     * Applies projection to the given JSON response.
     * 
     * @param response The full JSON response from the backend
     * @param projection The projection tree specifying which fields to include
     * @param context The filter context with depth/cycle tracking
     * @return The filtered JSON containing only requested fields
     */
    JsonNode project(JsonNode response, ProjectionTree projection, FilterContext context);

    /**
     * Checks if this projector supports the given content type.
     */
    boolean supports(String contentType);
}
