package io.kite.intellij.refactoring;

import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Names validator for Kite language.
 * Validates identifier names and determines if a string is a keyword.
 */
public class KiteNamesValidator implements NamesValidator {

    // Kite language keywords that cannot be used as identifiers
    private static final Set<String> KEYWORDS = Set.of(
            // IaC keywords
            "resource", "component", "schema", "input", "output",
            // Control flow
            "if", "else", "while", "for", "in", "return",
            // Declarations
            "import", "from", "fun", "var", "type", "init", "this",
            // Type keywords
            "object", "any", "string", "number", "boolean",
            // Literals
            "true", "false", "null"
    );

    @Override
    public boolean isKeyword(@NotNull String name, Project project) {
        return KEYWORDS.contains(name);
    }

    @Override
    public boolean isIdentifier(@NotNull String name, Project project) {
        if (name.isEmpty()) {
            return false;
        }

        // Don't allow keywords as identifiers
        if (isKeyword(name, project)) {
            return false;
        }

        // First character must be a letter or underscore
        char firstChar = name.charAt(0);
        if (!Character.isLetter(firstChar) && firstChar != '_') {
            return false;
        }

        // Remaining characters must be letters, digits, or underscores
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return false;
            }
        }

        return true;
    }
}
