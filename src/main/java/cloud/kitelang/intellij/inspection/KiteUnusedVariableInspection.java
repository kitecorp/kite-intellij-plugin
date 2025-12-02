package cloud.kitelang.intellij.inspection;

import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteFile;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import cloud.kitelang.intellij.util.KiteDeclarationHelper;
import cloud.kitelang.intellij.util.KitePsiUtil;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inspection that detects unused variable declarations in Kite files.
 * Reports variables that are declared but never referenced elsewhere.
 * <p>
 * Checks usages in:
 * - Direct identifier references
 * - Simple string interpolation ($varName)
 * - Brace string interpolation (${varName})
 */
public class KiteUnusedVariableInspection extends KiteInspectionBase {

    // Patterns for string interpolation
    private static final Pattern BRACE_INTERPOLATION_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
    private static final Pattern SIMPLE_INTERPOLATION_PATTERN = Pattern.compile("\\$([a-zA-Z_][a-zA-Z0-9_]*)");

    @Override
    public @NotNull String getShortName() {
        return "KiteUnusedVariable";
    }

    @Override
    protected void checkKiteFile(@NotNull KiteFile file,
                                  @NotNull InspectionManager manager,
                                  boolean isOnTheFly,
                                  @NotNull List<ProblemDescriptor> problems) {

        // Collect all variable declarations with their name elements
        Map<String, PsiElement> variableDeclarations = new HashMap<>();
        collectVariableDeclarations(file, variableDeclarations);

        if (variableDeclarations.isEmpty()) {
            return;
        }

        // Collect all usages of each variable name
        Set<String> usedVariables = new HashSet<>();
        collectUsages(file, variableDeclarations.keySet(), usedVariables);

        // Report unused variables
        for (var entry : variableDeclarations.entrySet()) {
            var name = entry.getKey();
            var nameElement = entry.getValue();

            if (!usedVariables.contains(name)) {
                var problem = createWarning(
                        manager,
                        nameElement,
                        "Variable '" + name + "' is never used",
                        isOnTheFly
                );
                problems.add(problem);
            }
        }
    }

    /**
     * Collect all variable declarations in the file.
     * Maps variable name to its name element (for highlighting).
     */
    private void collectVariableDeclarations(PsiElement element, Map<String, PsiElement> declarations) {
        if (element == null || element.getNode() == null) return;

        var type = element.getNode().getElementType();

        if (type == KiteElementTypes.VARIABLE_DECLARATION) {
            var nameElement = KiteDeclarationHelper.findNameElementInDeclaration(element, type);
            if (nameElement != null) {
                declarations.put(nameElement.getText(), nameElement);
            }
        }

        // Recurse into children
        var child = element.getFirstChild();
        while (child != null) {
            collectVariableDeclarations(child, declarations);
            child = child.getNextSibling();
        }
    }

    /**
     * Collect all usages of the given variable names.
     * Adds used variable names to the usedVariables set.
     */
    private void collectUsages(PsiElement element, Set<String> variableNames, Set<String> usedVariables) {
        if (element == null || element.getNode() == null) return;

        var type = element.getNode().getElementType();

        // Check IDENTIFIER tokens that are usages (not declarations)
        if (type == KiteTokenTypes.IDENTIFIER) {
            var text = element.getText();
            if (variableNames.contains(text) && isUsage(element)) {
                usedVariables.add(text);
            }
        }

        // Check INTERP_SIMPLE tokens ($varName)
        if (type == KiteTokenTypes.INTERP_SIMPLE) {
            var text = element.getText();
            if (text.startsWith("$") && text.length() > 1) {
                var varName = text.substring(1);
                if (variableNames.contains(varName)) {
                    usedVariables.add(varName);
                }
            }
        }

        // Check INTERP_IDENTIFIER tokens (inside ${...})
        if (type == KiteTokenTypes.INTERP_IDENTIFIER) {
            var text = element.getText();
            if (variableNames.contains(text)) {
                usedVariables.add(text);
            }
        }

        // Check STRING tokens for interpolation patterns (legacy support)
        if (type == KiteTokenTypes.STRING) {
            collectUsagesInString(element, variableNames, usedVariables);
        }

        // Recurse into children
        var child = element.getFirstChild();
        while (child != null) {
            collectUsages(child, variableNames, usedVariables);
            child = child.getNextSibling();
        }
    }

    /**
     * Check if an identifier is a usage (reference) rather than a declaration.
     * An identifier is a usage if it's NOT:
     * - The name being declared (before = or { in declaration)
     * - A type annotation
     * - A schema/component/function name
     */
    private boolean isUsage(PsiElement identifier) {
        // Check if this is part of a variable declaration name
        var parent = identifier.getParent();
        if (parent != null && parent.getNode() != null) {
            var parentType = parent.getNode().getElementType();
            if (parentType == KiteElementTypes.VARIABLE_DECLARATION) {
                var declName = KiteDeclarationHelper.findNameElementInDeclaration(parent, parentType);
                if (declName == identifier) {
                    return false; // This is the declaration, not a usage
                }
            }
        }

        // Check if preceded by a type keyword or other declaration keyword
        var prev = KitePsiUtil.skipWhitespaceBackward(identifier.getPrevSibling());
        if (prev != null && prev.getNode() != null) {
            var prevType = prev.getNode().getElementType();
            // If preceded by VAR, SCHEMA, COMPONENT, etc., likely not a usage
            if (prevType == KiteTokenTypes.VAR ||
                prevType == KiteTokenTypes.SCHEMA ||
                prevType == KiteTokenTypes.COMPONENT ||
                prevType == KiteTokenTypes.RESOURCE ||
                prevType == KiteTokenTypes.FUN) {
                return false;
            }
        }

        // Check if followed by = or { (declaration pattern)
        var next = KitePsiUtil.skipWhitespace(identifier.getNextSibling());
        if (next != null && next.getNode() != null) {
            var nextType = next.getNode().getElementType();
            if (nextType == KiteTokenTypes.ASSIGN || nextType == KiteTokenTypes.LBRACE) {
                // Could be a declaration name, but check more context
                // If the previous element is also an identifier (type), this is a property name
                // This could be "type name = value" pattern in schema/resource
                // which is a declaration, not a usage
                return prev == null || prev.getNode() == null ||
                       prev.getNode().getElementType() != KiteTokenTypes.IDENTIFIER;
            }
        }

        return true;
    }

    /**
     * Extract variable usages from string interpolation in legacy STRING tokens.
     */
    private void collectUsagesInString(PsiElement stringElement, Set<String> variableNames, Set<String> usedVariables) {
        var text = stringElement.getText();

        // Check ${...} interpolations
        Matcher braceMatcher = BRACE_INTERPOLATION_PATTERN.matcher(text);
        while (braceMatcher.find()) {
            var content = braceMatcher.group(1);
            var varName = extractFirstIdentifier(content);
            if (varName != null && variableNames.contains(varName)) {
                usedVariables.add(varName);
            }
        }

        // Check $var interpolations
        Matcher simpleMatcher = SIMPLE_INTERPOLATION_PATTERN.matcher(text);
        while (simpleMatcher.find()) {
            int matchStart = simpleMatcher.start();
            // Skip if this is part of ${...}
            if (matchStart + 1 < text.length() && text.charAt(matchStart + 1) == '{') {
                continue;
            }
            var varName = simpleMatcher.group(1);
            if (variableNames.contains(varName)) {
                usedVariables.add(varName);
            }
        }
    }

    /**
     * Extract the first identifier from an expression.
     */
    private String extractFirstIdentifier(String expression) {
        if (expression == null || expression.isEmpty()) {
            return null;
        }

        var sb = new StringBuilder();
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (i == 0) {
                if (Character.isJavaIdentifierStart(c)) {
                    sb.append(c);
                } else {
                    return null;
                }
            } else {
                if (Character.isJavaIdentifierPart(c)) {
                    sb.append(c);
                } else {
                    break;
                }
            }
        }

        return sb.length() > 0 ? sb.toString() : null;
    }
}
