package com.projection.core;

import com.projection.exception.FieldNotAllowedException;

import java.util.Set;

/**
 * Validates projection requests against an allowlist.
 * Used when allowedFields are specified in @Projectable annotation.
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
        if (requested == null) {
            throw new IllegalArgumentException("requested projection tree must not be null");
        }
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
            ProjectionTree sourceChild = source.getChild(fieldName);
            
            if (target.hasChild(fieldName)) {
                // Field already exists - need to merge subtrees
                ProjectionTree existingChild = target.getChild(fieldName);
                
                if (!sourceChild.isEmpty() || !existingChild.isEmpty()) {
                    // At least one has nested fields - merge them
                    ProjectionTree.Builder mergedBuilder = ProjectionTree.builder();
                    mergeInto(mergedBuilder, existingChild);
                    mergeInto(mergedBuilder, sourceChild);
                    target.addChild(fieldName, mergedBuilder.build());
                }
                // If both are leaves, no action needed (already in target)
            } else {
                // Field doesn't exist - add it
                if (sourceChild.isEmpty()) {
                    target.addLeaf(fieldName);
                } else {
                    ProjectionTree.Builder childBuilder = ProjectionTree.builder();
                    mergeInto(childBuilder, sourceChild);
                    target.addChild(fieldName, childBuilder.build());
                }
            }
        }
    }

    public ProjectionTree getAllowedFields() {
        return allowedFields;
    }
}
