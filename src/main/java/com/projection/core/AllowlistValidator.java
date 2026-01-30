package com.projection.core;

import com.projection.exception.FieldNotAllowedException;

import java.util.Set;

/**
 * Validates projection requests against an allowlist.
 * Used when @ProjectableFields annotation is present on an endpoint.
 */
public final class AllowlistValidator {

    private final ProjectionTree allowedFields;

    private AllowlistValidator(ProjectionTree allowedFields) {
        this.allowedFields = allowedFields;
    }

    public static AllowlistValidator fromFieldSpecs(String[] fieldSpecs) {
        if (fieldSpecs == null || fieldSpecs.length == 0) {
            return null;
        }

        ProjectionTree.Builder builder = ProjectionTree.builder();
        for (String spec : fieldSpecs) {
            ProjectionTree parsed = ProjectionTreeParser.parse(spec);
            mergeInto(builder, parsed);
        }

        return new AllowlistValidator(builder.build());
    }

    public void validate(ProjectionTree requested) {
        validateTree(requested, allowedFields, "");
    }

    private void validateTree(ProjectionTree requested, ProjectionTree allowed, String currentPath) {
        for (String fieldName : requested.getChildNames()) {
            String fieldPath = currentPath.isEmpty() ? fieldName : currentPath + "." + fieldName;

            if (!allowed.hasChild(fieldName)) {
                throw new FieldNotAllowedException(fieldPath);
            }

            ProjectionTree requestedChild = requested.getChild(fieldName);
            ProjectionTree allowedChild = allowed.getChild(fieldName);

            if (!requestedChild.isEmpty()) {
                if (allowedChild.isEmpty()) {
                    throw new FieldNotAllowedException(fieldPath);
                }
                validateTree(requestedChild, allowedChild, fieldPath);
            }
        }
    }

    private static void mergeInto(ProjectionTree.Builder target, ProjectionTree source) {
        for (String fieldName : source.getChildNames()) {
            ProjectionTree child = source.getChild(fieldName);
            if (child.isEmpty()) {
                target.addLeaf(fieldName);
            } else {
                ProjectionTree.Builder childBuilder = ProjectionTree.builder();
                mergeInto(childBuilder, child);
                target.addChild(fieldName, childBuilder.build());
            }
        }
    }

    public ProjectionTree getAllowedFields() {
        return allowedFields;
    }
}
