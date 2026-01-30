package com.projection.core;

import com.projection.exception.InvalidProjectionSyntaxException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProjectionTreeParserTest {

    @Nested
    @DisplayName("Simple field parsing")
    class SimpleFields {

        @Test
        void parseSingleField() {
            ProjectionTree tree = ProjectionTreeParser.parse("id");

            assertTrue(tree.hasChild("id"));
            assertEquals(1, tree.size());
        }

        @Test
        void parseMultipleFields() {
            ProjectionTree tree = ProjectionTreeParser.parse("id,name,email");

            assertTrue(tree.hasChild("id"));
            assertTrue(tree.hasChild("name"));
            assertTrue(tree.hasChild("email"));
            assertEquals(3, tree.size());
        }

        @Test
        void parseFieldWithUnderscore() {
            ProjectionTree tree = ProjectionTreeParser.parse("user_id,first_name");

            assertTrue(tree.hasChild("user_id"));
            assertTrue(tree.hasChild("first_name"));
        }

        @Test
        void parseFieldWithNumbers() {
            ProjectionTree tree = ProjectionTreeParser.parse("address1,phone2");

            assertTrue(tree.hasChild("address1"));
            assertTrue(tree.hasChild("phone2"));
        }
    }

    @Nested
    @DisplayName("Nested field parsing")
    class NestedFields {

        @Test
        void parseSingleNestedField() {
            ProjectionTree tree = ProjectionTreeParser.parse("profile(avatar)");

            assertTrue(tree.hasChild("profile"));
            ProjectionTree profile = tree.getChild("profile");
            assertTrue(profile.hasChild("avatar"));
        }

        @Test
        void parseMultipleNestedFields() {
            ProjectionTree tree = ProjectionTreeParser.parse("profile(avatar,bio,settings)");

            ProjectionTree profile = tree.getChild("profile");
            assertTrue(profile.hasChild("avatar"));
            assertTrue(profile.hasChild("bio"));
            assertTrue(profile.hasChild("settings"));
            assertEquals(3, profile.size());
        }

        @Test
        void parseDeeplyNestedFields() {
            ProjectionTree tree = ProjectionTreeParser.parse("orders(items(product(name,price)))");

            ProjectionTree orders = tree.getChild("orders");
            ProjectionTree items = orders.getChild("items");
            ProjectionTree product = items.getChild("product");
            assertTrue(product.hasChild("name"));
            assertTrue(product.hasChild("price"));
        }

        @Test
        void parseMixedFieldsAndNested() {
            ProjectionTree tree = ProjectionTreeParser.parse("id,name,profile(avatar,bio)");

            assertTrue(tree.hasChild("id"));
            assertTrue(tree.hasChild("name"));
            assertTrue(tree.hasChild("profile"));
            
            ProjectionTree profile = tree.getChild("profile");
            assertTrue(profile.hasChild("avatar"));
            assertTrue(profile.hasChild("bio"));
        }
    }

    @Nested
    @DisplayName("Whitespace handling")
    class WhitespaceHandling {

        @Test
        void parseWithSpacesAroundCommas() {
            ProjectionTree tree = ProjectionTreeParser.parse("id , name , email");

            assertTrue(tree.hasChild("id"));
            assertTrue(tree.hasChild("name"));
            assertTrue(tree.hasChild("email"));
        }

        @Test
        void parseWithSpacesAroundParentheses() {
            ProjectionTree tree = ProjectionTreeParser.parse("profile( avatar , bio )");

            ProjectionTree profile = tree.getChild("profile");
            assertTrue(profile.hasChild("avatar"));
            assertTrue(profile.hasChild("bio"));
        }

        @Test
        void parseWithLeadingAndTrailingSpaces() {
            ProjectionTree tree = ProjectionTreeParser.parse("  id,name  ");

            assertTrue(tree.hasChild("id"));
            assertTrue(tree.hasChild("name"));
        }
    }

    @Nested
    @DisplayName("Empty and null input")
    class EmptyInput {

        @Test
        void parseNullReturnsEmpty() {
            ProjectionTree tree = ProjectionTreeParser.parse(null);
            assertTrue(tree.isEmpty());
        }

        @Test
        void parseEmptyStringReturnsEmpty() {
            ProjectionTree tree = ProjectionTreeParser.parse("");
            assertTrue(tree.isEmpty());
        }

        @Test
        void parseBlankStringReturnsEmpty() {
            ProjectionTree tree = ProjectionTreeParser.parse("   ");
            assertTrue(tree.isEmpty());
        }
    }

    @Nested
    @DisplayName("Invalid syntax")
    class InvalidSyntax {

        @Test
        void rejectStartingWithNumber() {
            assertThrows(InvalidProjectionSyntaxException.class, 
                () -> ProjectionTreeParser.parse("123field"));
        }

        @Test
        void rejectUnclosedParenthesis() {
            assertThrows(InvalidProjectionSyntaxException.class,
                () -> ProjectionTreeParser.parse("profile(avatar"));
        }

        @Test
        void rejectExtraClosingParenthesis() {
            assertThrows(InvalidProjectionSyntaxException.class,
                () -> ProjectionTreeParser.parse("profile(avatar))"));
        }

        @Test
        void rejectSpecialCharacters() {
            assertThrows(InvalidProjectionSyntaxException.class,
                () -> ProjectionTreeParser.parse("field@name"));
        }

        @Test
        void rejectEmptyParentheses() {
            assertThrows(InvalidProjectionSyntaxException.class,
                () -> ProjectionTreeParser.parse("profile()"));
        }

        @Test
        void rejectTrailingComma() {
            assertThrows(InvalidProjectionSyntaxException.class,
                () -> ProjectionTreeParser.parse("id,name,"));
        }

        @Test
        void rejectDoubleComma() {
            assertThrows(InvalidProjectionSyntaxException.class,
                () -> ProjectionTreeParser.parse("id,,name"));
        }
    }

    @Nested
    @DisplayName("Complex projections from spec")
    class ComplexProjections {

        @Test
        void parseOrdersWithNestedItems() {
            ProjectionTree tree = ProjectionTreeParser.parse("orders(id,total,items(productId))");

            ProjectionTree orders = tree.getChild("orders");
            assertTrue(orders.hasChild("id"));
            assertTrue(orders.hasChild("total"));
            assertTrue(orders.hasChild("items"));

            ProjectionTree items = orders.getChild("items");
            assertTrue(items.hasChild("productId"));
        }

        @Test
        void parseUserProfileWithMultipleNested() {
            ProjectionTree tree = ProjectionTreeParser.parse(
                "id,name,profile(avatar,bio),orders(id,total)"
            );

            assertTrue(tree.hasChild("id"));
            assertTrue(tree.hasChild("name"));
            
            ProjectionTree profile = tree.getChild("profile");
            assertTrue(profile.hasChild("avatar"));
            assertTrue(profile.hasChild("bio"));

            ProjectionTree orders = tree.getChild("orders");
            assertTrue(orders.hasChild("id"));
            assertTrue(orders.hasChild("total"));
        }
    }
}
