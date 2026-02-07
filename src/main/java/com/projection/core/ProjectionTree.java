package com.projection.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents a whitelist tree of fields to project from a response.
 * 
 * Structure example for projection "id,name,profile(avatar,bio)":
 * ROOT
 *  |- id (leaf)
 *  |- name (leaf)
 *  |- profile
 *      |- avatar (leaf)
 *      |- bio (leaf)
 * 
 * Thread-safe after construction. Instances are immutable after building.
 */
public final class ProjectionTree {

    private final Map<String, ProjectionTree> children;
    private final boolean isLeaf;
    private final List<FieldInstruction> compiledInstructions;

    private ProjectionTree(Map<String, ProjectionTree> children) {
        this.children = Collections.unmodifiableMap(new LinkedHashMap<>(children));
        this.isLeaf = children.isEmpty();
        this.compiledInstructions = compileInstructions(this.children);
    }

    private static List<FieldInstruction> compileInstructions(Map<String, ProjectionTree> children) {
        if (children.isEmpty()) {
            return Collections.emptyList();
        }
        List<FieldInstruction> instructions = new ArrayList<>(children.size());
        for (Map.Entry<String, ProjectionTree> entry : children.entrySet()) {
            String fieldName = entry.getKey();
            ProjectionTree childTree = entry.getValue();
            boolean isLeaf = childTree.isEmpty();
            instructions.add(new FieldInstruction(fieldName, childTree, isLeaf));
        }
        return Collections.unmodifiableList(instructions);
    }

    public boolean hasChild(String fieldName) {
        return children.containsKey(fieldName);
    }

    public ProjectionTree getChild(String fieldName) {
        return children.get(fieldName);
    }

    public Set<String> getChildNames() {
        return children.keySet();
    }

    public boolean isLeaf() {
        return isLeaf;
    }

    public boolean isEmpty() {
        return children.isEmpty();
    }

    public int size() {
        return children.size();
    }

    /**
     * Represents a pre-computed projection instruction for a single field.
     * Used to avoid repeated Map lookups when projecting arrays.
     */
    public record FieldInstruction(String fieldName, ProjectionTree childTree, boolean isLeaf) {}

    /**
     * Returns the pre-compiled list of field instructions.
     * This list is computed once during construction and cached,
     * allowing efficient iteration without Map lookups.
     */
    public List<FieldInstruction> compile() {
        return compiledInstructions;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ProjectionTree empty() {
        return new ProjectionTree(Collections.emptyMap());
    }

    public static class Builder {
        private final Map<String, ProjectionTree> children = new LinkedHashMap<>();

        public Builder addLeaf(String fieldName) {
            if (fieldName == null || fieldName.isBlank()) {
                throw new IllegalArgumentException("fieldName must not be null or blank");
            }
            children.put(fieldName, ProjectionTree.empty());
            return this;
        }

        public Builder addChild(String fieldName, ProjectionTree subtree) {
            if (fieldName == null || fieldName.isBlank()) {
                throw new IllegalArgumentException("fieldName must not be null or blank");
            }
            if (subtree == null) {
                throw new IllegalArgumentException("subtree must not be null");
            }
            children.put(fieldName, subtree);
            return this;
        }

        public boolean hasChild(String fieldName) {
            return children.containsKey(fieldName);
        }

        public ProjectionTree getChild(String fieldName) {
            return children.get(fieldName);
        }

        public ProjectionTree build() {
            return new ProjectionTree(children);
        }
    }

    @Override
    public String toString() {
        return formatTree("", true);
    }

    private String formatTree(String prefix, boolean isRoot) {
        StringBuilder sb = new StringBuilder();
        if (isRoot) {
            sb.append("ROOT\n");
        }

        String[] names = children.keySet().toArray(new String[0]);
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            boolean isLast = (i == names.length - 1);
            String connector = isLast ? "+-- " : "|-- ";
            String childPrefix = isLast ? "    " : "|   ";

            sb.append(prefix).append(connector).append(name);
            
            ProjectionTree child = children.get(name);
            if (!child.isEmpty()) {
                sb.append("\n");
                sb.append(child.formatTree(prefix + childPrefix, false));
            } else {
                sb.append("\n");
            }
        }
        return sb.toString();
    }
}
