package com.projection.projector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.projection.config.ProjectionProperties;
import com.projection.core.FilterContext;
import com.projection.core.ProjectionTree;
import com.projection.exception.MissingFieldException;

import java.util.Iterator;

/**
 * JSON response projector using Jackson Tree Model.
 * 
 * Traverses the JSON structure and applies the projection tree as a whitelist filter.
 * Strict mode means any missing field causes immediate failure.
 */
public class JsonResponseProjector implements ResponseProjector {

    private static final String JSON_CONTENT_TYPE = "application/json";

    private final ProjectionProperties properties;

    public JsonResponseProjector(ProjectionProperties properties) {
        this.properties = properties;
    }

    @Override
    public JsonNode project(JsonNode response, ProjectionTree projection, FilterContext context) {
        if (response == null || response.isNull()) {
            return response;
        }

        if (projection.isEmpty()) {
            return response;
        }

        return projectNode(response, projection, context);
    }

    @Override
    public boolean supports(String contentType) {
        if (contentType == null) {
            return false;
        }
        return contentType.contains(JSON_CONTENT_TYPE);
    }

    private JsonNode projectNode(JsonNode node, ProjectionTree projection, FilterContext context) {
        if (node.isArray()) {
            return projectArray((ArrayNode) node, projection, context);
        }
        
        if (node.isObject()) {
            return projectObject((ObjectNode) node, projection, context);
        }

        return node;
    }

    private JsonNode projectArray(ArrayNode array, ProjectionTree projection, FilterContext context) {
        ArrayNode result = JsonNodeFactory.instance.arrayNode(array.size());

        for (JsonNode element : array) {
            JsonNode projected = projectNode(element, projection, context);
            result.add(projected);
        }

        return result;
    }

    private JsonNode projectObject(ObjectNode object, ProjectionTree projection, FilterContext context) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();

        for (String requestedField : projection.getChildNames()) {
            if (!object.has(requestedField)) {
                String fullPath = context.buildPath(requestedField);
                throw new MissingFieldException(
                    fullPath, 
                    properties.getError().getMissingField().getStatus()
                );
            }

            JsonNode fieldValue = object.get(requestedField);
            ProjectionTree childProjection = projection.getChild(requestedField);

            if (childProjection.isEmpty()) {
                result.set(requestedField, fieldValue);
            } else {
                context.descend(requestedField);
                try {
                    JsonNode projectedChild = projectNode(fieldValue, childProjection, context);
                    result.set(requestedField, projectedChild);
                } finally {
                    context.ascend();
                }
            }
        }

        return result;
    }
}
