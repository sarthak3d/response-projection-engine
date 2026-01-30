package com.projection.core;

import com.projection.exception.FieldNotAllowedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AllowlistValidatorTest {

    @Nested
    @DisplayName("Allowlist creation")
    class AllowlistCreation {

        @Test
        void createsFromSimpleFields() {
            AllowlistValidator validator = AllowlistValidator.fromFieldSpecs(
                new String[]{"id", "name", "email"}
            );

            assertNotNull(validator);
            assertTrue(validator.getAllowedFields().hasChild("id"));
            assertTrue(validator.getAllowedFields().hasChild("name"));
            assertTrue(validator.getAllowedFields().hasChild("email"));
        }

        @Test
        void createsFromNestedFields() {
            AllowlistValidator validator = AllowlistValidator.fromFieldSpecs(
                new String[]{"profile(avatar,bio)"}
            );

            assertTrue(validator.getAllowedFields().hasChild("profile"));
            ProjectionTree profile = validator.getAllowedFields().getChild("profile");
            assertTrue(profile.hasChild("avatar"));
            assertTrue(profile.hasChild("bio"));
        }

        @Test
        void mergesMultipleSpecs() {
            AllowlistValidator validator = AllowlistValidator.fromFieldSpecs(
                new String[]{"id", "name", "profile(avatar)"}
            );

            assertTrue(validator.getAllowedFields().hasChild("id"));
            assertTrue(validator.getAllowedFields().hasChild("name"));
            assertTrue(validator.getAllowedFields().hasChild("profile"));
        }

        @Test
        void returnsNullForEmptySpecs() {
            AllowlistValidator validator = AllowlistValidator.fromFieldSpecs(new String[]{});
            assertNull(validator);
        }

        @Test
        void returnsNullForNull() {
            AllowlistValidator validator = AllowlistValidator.fromFieldSpecs(null);
            assertNull(validator);
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        void allowsExactMatch() {
            AllowlistValidator validator = AllowlistValidator.fromFieldSpecs(
                new String[]{"id", "name"}
            );

            ProjectionTree requested = ProjectionTreeParser.parse("id,name");
            assertDoesNotThrow(() -> validator.validate(requested));
        }

        @Test
        void allowsSubsetOfAllowed() {
            AllowlistValidator validator = AllowlistValidator.fromFieldSpecs(
                new String[]{"id", "name", "email"}
            );

            ProjectionTree requested = ProjectionTreeParser.parse("id");
            assertDoesNotThrow(() -> validator.validate(requested));
        }

        @Test
        void rejectsFieldNotInAllowlist() {
            AllowlistValidator validator = AllowlistValidator.fromFieldSpecs(
                new String[]{"id", "name"}
            );

            ProjectionTree requested = ProjectionTreeParser.parse("id,secret");
            
            FieldNotAllowedException exception = assertThrows(
                FieldNotAllowedException.class,
                () -> validator.validate(requested)
            );
            
            assertTrue(exception.getPath().contains("secret"));
        }

        @Test
        void allowsNestedFieldsWhenAllowed() {
            AllowlistValidator validator = AllowlistValidator.fromFieldSpecs(
                new String[]{"profile(avatar,bio)"}
            );

            ProjectionTree requested = ProjectionTreeParser.parse("profile(avatar)");
            assertDoesNotThrow(() -> validator.validate(requested));
        }

        @Test
        void rejectsNestedFieldNotInAllowlist() {
            AllowlistValidator validator = AllowlistValidator.fromFieldSpecs(
                new String[]{"profile(avatar)"}
            );

            ProjectionTree requested = ProjectionTreeParser.parse("profile(avatar,secret)");
            
            FieldNotAllowedException exception = assertThrows(
                FieldNotAllowedException.class,
                () -> validator.validate(requested)
            );
            
            assertTrue(exception.getPath().contains("secret"));
        }

        @Test
        void rejectsNestedAccessWhenOnlyLeafAllowed() {
            AllowlistValidator validator = AllowlistValidator.fromFieldSpecs(
                new String[]{"profile"}
            );

            ProjectionTree requested = ProjectionTreeParser.parse("profile(avatar)");
            
            assertThrows(FieldNotAllowedException.class, () -> validator.validate(requested));
        }
    }
}
