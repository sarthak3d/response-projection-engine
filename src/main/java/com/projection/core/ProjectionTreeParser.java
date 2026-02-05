package com.projection.core;

import com.projection.exception.InvalidProjectionSyntaxException;

/**
 * Recursive descent parser for the projection DSL.
 * 
 * Grammar:
 *   projection := field (',' field)*
 *   field      := name | name '(' projection ')'
 *   name       := [a-zA-Z_][a-zA-Z0-9_]*
 * 
 * The parser is intentionally strict - any syntax error results in immediate failure.
 */
public final class ProjectionTreeParser {

    private final String original;
    private final String input;
    private final int baseOffset;
    private int position;

    private ProjectionTreeParser(String original, String input, int baseOffset) {
        this.original = original;
        this.input = input;
        this.baseOffset = baseOffset;
        this.position = 0;
    }

    public static ProjectionTree parse(String projection) {
        if (projection == null || projection.isBlank()) {
            return ProjectionTree.empty();
        }

        String trimmed = projection.trim();
        int baseOffset = projection.indexOf(trimmed.charAt(0));
        
        ProjectionTreeParser parser = new ProjectionTreeParser(projection, trimmed, baseOffset);
        ProjectionTree tree = parser.parseProjection();
        parser.expectEnd();
        return tree;
    }

    private int originalPosition() {
        return baseOffset + position;
    }

    private ProjectionTree parseProjection() {
        ProjectionTree.Builder builder = ProjectionTree.builder();
        parseField(builder);

        while (peek() == ',') {
            consume(',');
            skipWhitespace();
            parseField(builder);
        }

        return builder.build();
    }

    private void parseField(ProjectionTree.Builder builder) {
        String name = parseName();
        skipWhitespace();

        if (peek() == '(') {
            consume('(');
            skipWhitespace();
            ProjectionTree subtree = parseProjection();
            skipWhitespace();
            consume(')');
            builder.addChild(name, subtree);
        } else {
            builder.addLeaf(name);
        }
        
        skipWhitespace();
    }

    private String parseName() {
        int start = position;

        if (position >= input.length()) {
            throw new InvalidProjectionSyntaxException(original, originalPosition(), "Expected field name");
        }

        char firstChar = input.charAt(position);
        if (!isNameStart(firstChar)) {
            throw new InvalidProjectionSyntaxException(
                original, originalPosition(), 
                String.format("Invalid field name start character: '%c'", firstChar)
            );
        }

        position++;
        while (position < input.length() && isNameContinue(input.charAt(position))) {
            position++;
        }

        return input.substring(start, position);
    }

    private boolean isNameStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private boolean isNameContinue(char c) {
        return isNameStart(c) || (c >= '0' && c <= '9');
    }

    private char peek() {
        skipWhitespace();
        if (position >= input.length()) {
            return '\0';
        }
        return input.charAt(position);
    }

    private void consume(char expected) {
        skipWhitespace();
        if (position >= input.length()) {
            throw new InvalidProjectionSyntaxException(
                original, originalPosition(), 
                String.format("Expected '%c' but reached end of input", expected)
            );
        }
        
        char actual = input.charAt(position);
        if (actual != expected) {
            throw new InvalidProjectionSyntaxException(
                original, originalPosition(), 
                String.format("Expected '%c' but found '%c'", expected, actual)
            );
        }
        position++;
    }

    private void skipWhitespace() {
        while (position < input.length() && Character.isWhitespace(input.charAt(position))) {
            position++;
        }
    }

    private void expectEnd() {
        skipWhitespace();
        if (position < input.length()) {
            throw new InvalidProjectionSyntaxException(
                original, originalPosition(), 
                String.format("Unexpected character '%c' after valid projection", input.charAt(position))
            );
        }
    }
}

