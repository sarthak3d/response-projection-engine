package com.projection.projector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.projection.config.ProjectionProperties;
import com.projection.core.FilterContext;
import com.projection.core.ProjectionTree;
import com.projection.exception.MissingFieldException;

import java.util.List;

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

        int memoizationThreshold = properties.getMemoizationThreshold();
        if (array.size() < memoizationThreshold) {
            for (JsonNode element : array) {
                JsonNode projected = projectNode(element, projection, context);
                result.add(projected);
            }
        } else {
            List<ProjectionTree.FieldInstruction> instructions = projection.compile();
            for (JsonNode element : array) {
                if (element.isObject()) {
                    JsonNode projected = projectObjectMemoized((ObjectNode) element, instructions, context);
                    result.add(projected);
                } else {
                    result.add(projectNode(element, projection, context));
                }
            }
        }

        return result;
    }

    private JsonNode projectObjectMemoized(ObjectNode object, List<ProjectionTree.FieldInstruction> instructions, FilterContext context) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();

        for (ProjectionTree.FieldInstruction instr : instructions) {
            String fieldName = instr.fieldName();
            
            JsonNode fieldValue = object.get(fieldName);
            
            if (fieldValue == null) {
                String fullPath = context.buildPath(fieldName);
                throw new MissingFieldException(
                    fullPath, 
                    properties.getError().getMissingField().getStatus()
                );
            }

            if (instr.isLeaf()) {
                result.set(fieldName, fieldValue);
            } else {
                context.descend(fieldName);
                try {
                    JsonNode projectedChild = projectNode(fieldValue, instr.childTree(), context);
                    result.set(fieldName, projectedChild);
                } finally {
                    context.ascend();
                }
            }
        }

        return result;
    }

    private JsonNode projectObject(ObjectNode object, ProjectionTree projection, FilterContext context) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();

        for (String requestedField : projection.getChildNames()) {
            JsonNode fieldValue = object.get(requestedField);

            if (fieldValue == null) {
                String fullPath = context.buildPath(requestedField);
                throw new MissingFieldException(
                    fullPath, 
                    properties.getError().getMissingField().getStatus()
                );
            }

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
