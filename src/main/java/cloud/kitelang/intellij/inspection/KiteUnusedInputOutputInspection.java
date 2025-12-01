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
 * Inspection that detects unused input/output declarations in Kite components.
 * Reports inputs that are declared but never referenced elsewhere in the component.
 * <p>
 * Note: Outputs are generally considered "used" by external consumers, so they are not flagged.
 * Only inputs that are never referenced within the component are flagged.
 */
public class KiteUnusedInputOutputInspection extends KiteInspectionBase {

    // Patterns for string interpolation
    private static final Pattern BRACE_INTERPOLATION_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
    private static final Pattern SIMPLE_INTERPOLATION_PATTERN = Pattern.compile("\\$([a-zA-Z_][a-zA-Z0-9_]*)");

    @Override
    public @NotNull String getShortName() {
        return "KiteUnusedInputOutput";
    }

    @Override
    protected void checkKiteFile(@NotNull KiteFile file,
                                  @NotNull InspectionManager manager,
                                  boolean isOnTheFly,
                                  @NotNull List<ProblemDescriptor> problems) {
        // Find all component declarations and check their inputs
        checkComponentsRecursive(file, manager, isOnTheFly, problems);
    }

    private void checkComponentsRecursive(PsiElement element,
                                           InspectionManager manager,
                                           boolean isOnTheFly,
                                           List<ProblemDescriptor> problems) {
        if (element == null || element.getNode() == null) return;

        var type = element.getNode().getElementType();

        if (type == KiteElementTypes.COMPONENT_DECLARATION) {
            checkComponent(element, manager, isOnTheFly, problems);
        }

        // Recurse into children (but not into nested component declarations - those are checked separately)
        var child = element.getFirstChild();
        while (child != null) {
            checkComponentsRecursive(child, manager, isOnTheFly, problems);
            child = child.getNextSibling();
        }
    }

    private void checkComponent(PsiElement componentDecl,
                                 InspectionManager manager,
                                 boolean isOnTheFly,
                                 List<ProblemDescriptor> problems) {

        // Collect all input declarations in this component
        Map<String, PsiElement> inputDeclarations = new HashMap<>();
        collectInputDeclarations(componentDecl, inputDeclarations);

        if (inputDeclarations.isEmpty()) {
            return;
        }

        // Collect all usages within this component
        Set<String> usedNames = new HashSet<>();
        collectUsagesInComponent(componentDecl, inputDeclarations.keySet(), usedNames);

        // Report unused inputs
        for (var entry : inputDeclarations.entrySet()) {
            var name = entry.getKey();
            var nameElement = entry.getValue();

            if (!usedNames.contains(name)) {
                var problem = createWarning(
                        manager,
                        nameElement,
                        "Input '" + name + "' is never used",
                        isOnTheFly
                );
                problems.add(problem);
            }
        }
    }

    /**
     * Collect all input declarations within a component.
     * Maps input name to its name element (for highlighting).
     */
    private void collectInputDeclarations(PsiElement componentDecl, Map<String, PsiElement> declarations) {
        collectInputDeclarationsRecursive(componentDecl, declarations, componentDecl);
    }

    private void collectInputDeclarationsRecursive(PsiElement element, Map<String, PsiElement> declarations, PsiElement rootComponent) {
        if (element == null || element.getNode() == null) return;

        var type = element.getNode().getElementType();

        // Only collect INPUT_DECLARATION (not OUTPUT_DECLARATION - outputs are considered used externally)
        if (type == KiteElementTypes.INPUT_DECLARATION) {
            var nameElement = KiteDeclarationHelper.findNameElementInDeclaration(element, type);
            if (nameElement != null) {
                declarations.put(nameElement.getText(), nameElement);
            }
        }

        // Recurse into children
        var child = element.getFirstChild();
        while (child != null) {
            // Skip nested component declarations (they have their own scope)
            if (child.getNode() != null &&
                child.getNode().getElementType() == KiteElementTypes.COMPONENT_DECLARATION &&
                child != rootComponent) {
                child = child.getNextSibling();
                continue;
            }
            collectInputDeclarationsRecursive(child, declarations, rootComponent);
            child = child.getNextSibling();
        }
    }

    /**
     * Collect all usages of the given names within a component.
     * Adds used names to the usedNames set.
     */
    private void collectUsagesInComponent(PsiElement componentDecl, Set<String> inputNames, Set<String> usedNames) {
        collectUsagesInComponentRecursive(componentDecl, inputNames, usedNames, componentDecl);
    }

    private void collectUsagesInComponentRecursive(PsiElement element, Set<String> inputNames, Set<String> usedNames, PsiElement rootComponent) {
        if (element == null || element.getNode() == null) return;

        var type = element.getNode().getElementType();

        // Check IDENTIFIER tokens that are usages (not declarations)
        if (type == KiteTokenTypes.IDENTIFIER) {
            var text = element.getText();
            if (inputNames.contains(text) && isUsage(element)) {
                usedNames.add(text);
            }
        }

        // Check INTERP_SIMPLE tokens ($varName)
        if (type == KiteTokenTypes.INTERP_SIMPLE) {
            var text = element.getText();
            if (text.startsWith("$") && text.length() > 1) {
                var varName = text.substring(1);
                if (inputNames.contains(varName)) {
                    usedNames.add(varName);
                }
            }
        }

        // Check INTERP_IDENTIFIER tokens (inside ${...})
        if (type == KiteTokenTypes.INTERP_IDENTIFIER) {
            var text = element.getText();
            if (inputNames.contains(text)) {
                usedNames.add(text);
            }
        }

        // Check STRING tokens for interpolation patterns
        if (type == KiteTokenTypes.STRING) {
            collectUsagesInString(element, inputNames, usedNames);
        }

        // Recurse into children
        var child = element.getFirstChild();
        while (child != null) {
            // Skip nested component declarations (they have their own scope)
            if (child.getNode() != null &&
                child.getNode().getElementType() == KiteElementTypes.COMPONENT_DECLARATION &&
                child != rootComponent) {
                child = child.getNextSibling();
                continue;
            }
            collectUsagesInComponentRecursive(child, inputNames, usedNames, rootComponent);
            child = child.getNextSibling();
        }
    }

    /**
     * Check if an identifier is a usage (reference) rather than a declaration.
     */
    private boolean isUsage(PsiElement identifier) {
        // Check if this is part of an input/output declaration name
        var parent = identifier.getParent();
        if (parent != null && parent.getNode() != null) {
            var parentType = parent.getNode().getElementType();
            if (parentType == KiteElementTypes.INPUT_DECLARATION ||
                parentType == KiteElementTypes.OUTPUT_DECLARATION) {
                var declName = KiteDeclarationHelper.findNameElementInDeclaration(parent, parentType);
                if (declName == identifier) {
                    return false; // This is the declaration, not a usage
                }
            }
        }

        // Check if preceded by INPUT or OUTPUT keyword
        var prev = KitePsiUtil.skipWhitespaceBackward(identifier.getPrevSibling());
        if (prev != null && prev.getNode() != null) {
            var prevType = prev.getNode().getElementType();
            if (prevType == KiteTokenTypes.INPUT || prevType == KiteTokenTypes.OUTPUT) {
                return false;
            }
            // Check for type identifier before this (like "string port" where this is "port")
            if (prevType == KiteTokenTypes.IDENTIFIER) {
                var beforeType = KitePsiUtil.skipWhitespaceBackward(prev.getPrevSibling());
                if (beforeType != null && beforeType.getNode() != null) {
                    var beforeTypeType = beforeType.getNode().getElementType();
                    if (beforeTypeType == KiteTokenTypes.INPUT || beforeTypeType == KiteTokenTypes.OUTPUT) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    /**
     * Extract usages from string interpolation.
     */
    private void collectUsagesInString(PsiElement stringElement, Set<String> inputNames, Set<String> usedNames) {
        var text = stringElement.getText();

        // Check ${...} interpolations
        Matcher braceMatcher = BRACE_INTERPOLATION_PATTERN.matcher(text);
        while (braceMatcher.find()) {
            var content = braceMatcher.group(1);
            var varName = extractFirstIdentifier(content);
            if (varName != null && inputNames.contains(varName)) {
                usedNames.add(varName);
            }
        }

        // Check $var interpolations
        Matcher simpleMatcher = SIMPLE_INTERPOLATION_PATTERN.matcher(text);
        while (simpleMatcher.find()) {
            int matchStart = simpleMatcher.start();
            if (matchStart + 1 < text.length() && text.charAt(matchStart + 1) == '{') {
                continue;
            }
            var varName = simpleMatcher.group(1);
            if (inputNames.contains(varName)) {
                usedNames.add(varName);
            }
        }
    }

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
