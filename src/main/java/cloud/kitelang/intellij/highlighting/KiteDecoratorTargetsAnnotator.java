package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteLanguage;
import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

import static cloud.kitelang.intellij.util.KitePsiUtil.skipWhitespace;
import static cloud.kitelang.intellij.util.KitePsiUtil.skipWhitespaceBackward;

/**
 * Annotator that validates decorators are applied to the correct declaration types.
 * <p>
 * Example:
 * <pre>
 * @count(3)
 * resource EC2.Instance server { }  // OK - @count valid on resource
 *
 * @count(3)
 * schema Config { }                 // Error - @count not valid on schema
 *
 * @cloud
 * schema Instance {
 *     string publicIp               // Error - @cloud on wrong element
 * }
 *
 * schema Instance {
 *     @cloud
 *     string publicIp               // OK - @cloud valid on schema property
 * }
 * </pre>
 */
public class KiteDecoratorTargetsAnnotator implements Annotator {

    /**
     * Target declaration types for decorators.
     */
    private enum Target {
        RESOURCE,
        COMPONENT,
        INPUT,
        OUTPUT,
        SCHEMA,
        SCHEMA_PROPERTY,
        FUNCTION,
        VARIABLE
    }

    /**
     * Map of decorator names to their valid target types.
     */
    private static final Map<String, Set<Target>> DECORATOR_TARGETS = Map.ofEntries(
            // Resource/component instance decorators
            Map.entry("count", Set.of(Target.RESOURCE, Target.COMPONENT)),
            Map.entry("existing", Set.of(Target.RESOURCE)),
            Map.entry("tags", Set.of(Target.RESOURCE, Target.COMPONENT)),
            Map.entry("provider", Set.of(Target.RESOURCE, Target.COMPONENT)),
            Map.entry("provisionOn", Set.of(Target.RESOURCE, Target.COMPONENT)),
            Map.entry("dependsOn", Set.of(Target.RESOURCE, Target.COMPONENT)),

            // Schema property decorator
            Map.entry("cloud", Set.of(Target.SCHEMA_PROPERTY)),

            // Input/output decorators
            Map.entry("minValue", Set.of(Target.INPUT, Target.OUTPUT)),
            Map.entry("maxValue", Set.of(Target.INPUT, Target.OUTPUT)),
            Map.entry("minLength", Set.of(Target.INPUT, Target.OUTPUT)),
            Map.entry("maxLength", Set.of(Target.INPUT, Target.OUTPUT)),
            Map.entry("nonEmpty", Set.of(Target.INPUT)),
            Map.entry("unique", Set.of(Target.INPUT)),
            Map.entry("sensitive", Set.of(Target.INPUT, Target.OUTPUT)),
            Map.entry("allowed", Set.of(Target.INPUT)),
            Map.entry("validate", Set.of(Target.INPUT)),

            // Multi-target decorators
            Map.entry("description", Set.of(
                    Target.RESOURCE, Target.COMPONENT, Target.INPUT, Target.OUTPUT,
                    Target.VARIABLE, Target.SCHEMA, Target.SCHEMA_PROPERTY, Target.FUNCTION
            ))
    );

    /**
     * Human-readable names for target types.
     */
    private static final Map<Set<Target>, String> TARGET_DESCRIPTIONS = Map.of(
            Set.of(Target.RESOURCE, Target.COMPONENT), "resource or component instances",
            Set.of(Target.RESOURCE), "resource declarations",
            Set.of(Target.SCHEMA_PROPERTY), "schema properties",
            Set.of(Target.INPUT, Target.OUTPUT), "input or output declarations",
            Set.of(Target.INPUT), "input declarations"
    );

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element.getNode() == null) return;

        if (element.getContainingFile() == null ||
            element.getContainingFile().getLanguage() != KiteLanguage.INSTANCE) {
            return;
        }

        IElementType type = element.getNode().getElementType();

        // Trigger on decorator name IDENTIFIER (after @)
        if (type == KiteTokenTypes.IDENTIFIER && isDecoratorName(element)) {
            checkDecoratorTarget(element, holder);
        }
    }

    /**
     * Check if this identifier is a decorator name (preceded by @).
     */
    private boolean isDecoratorName(PsiElement identifier) {
        PsiElement prev = skipWhitespaceBackward(identifier.getPrevSibling());
        return prev != null && prev.getNode() != null &&
               prev.getNode().getElementType() == KiteTokenTypes.AT;
    }

    /**
     * Check if the decorator is applied to a valid target.
     */
    private void checkDecoratorTarget(PsiElement nameElement, AnnotationHolder holder) {
        String decoratorName = nameElement.getText();

        // Get valid targets for this decorator
        Set<Target> validTargets = DECORATOR_TARGETS.get(decoratorName);
        if (validTargets == null) {
            return; // Unknown decorator, handled by different annotator
        }

        // Find the target declaration
        Target actualTarget = findDecoratorTarget(nameElement);
        if (actualTarget == null) {
            return; // Could not determine target, skip validation
        }

        // Check if target is valid
        if (!validTargets.contains(actualTarget)) {
            String targetDescription = TARGET_DESCRIPTIONS.getOrDefault(validTargets,
                    formatTargets(validTargets));
            holder.newAnnotation(HighlightSeverity.ERROR,
                            "@" + decoratorName + " cannot be applied here; only valid on " + targetDescription)
                    .range(nameElement)
                    .create();
        }
    }

    /**
     * Format a set of targets as a human-readable string.
     */
    private String formatTargets(Set<Target> targets) {
        return targets.stream()
                .map(t -> t.name().toLowerCase().replace("_", " ") + " declarations")
                .reduce((a, b) -> a + " or " + b)
                .orElse("unknown");
    }

    /**
     * Find what type of declaration this decorator is applied to.
     * Walks forward from the decorator to find the next declaration.
     */
    @Nullable
    private Target findDecoratorTarget(PsiElement decoratorName) {
        // First check if we're inside a schema/component body (for property decorators)
        PsiElement parent = decoratorName.getParent();
        while (parent != null) {
            if (parent.getNode() != null) {
                IElementType parentType = parent.getNode().getElementType();
                if (parentType == KiteElementTypes.SCHEMA_DECLARATION) {
                    // Inside schema - check if decorator is on a property
                    if (isPropertyDecorator(decoratorName)) {
                        return Target.SCHEMA_PROPERTY;
                    }
                }
                if (parentType == KiteElementTypes.COMPONENT_DECLARATION) {
                    // Inside component - decorators could be on input/output
                    break;
                }
            }
            parent = parent.getParent();
        }

        // Walk forward to find the next declaration element
        PsiElement current = decoratorName;

        // Skip past decorator arguments if present: @name(args)
        PsiElement afterName = skipWhitespace(decoratorName.getNextSibling());
        if (afterName != null && afterName.getNode() != null &&
            afterName.getNode().getElementType() == KiteTokenTypes.LPAREN) {
            // Skip to closing paren
            current = skipToClosingParen(afterName);
            if (current != null) {
                current = current.getNextSibling();
            }
        } else {
            current = decoratorName.getNextSibling();
        }

        // Walk siblings to find next declaration keyword or element
        while (current != null) {
            if (current.getNode() != null) {
                IElementType currentType = current.getNode().getElementType();

                // Skip whitespace and newlines
                if (isWhitespaceOrNewline(currentType)) {
                    current = current.getNextSibling();
                    continue;
                }

                // Skip other decorators (can have multiple decorators on one declaration)
                if (currentType == KiteTokenTypes.AT) {
                    current = skipDecorator(current);
                    continue;
                }

                // Check for declaration keywords
                if (currentType == KiteTokenTypes.RESOURCE) {
                    return Target.RESOURCE;
                }
                if (currentType == KiteTokenTypes.COMPONENT) {
                    return Target.COMPONENT;
                }
                if (currentType == KiteTokenTypes.SCHEMA) {
                    return Target.SCHEMA;
                }
                if (currentType == KiteTokenTypes.INPUT) {
                    return Target.INPUT;
                }
                if (currentType == KiteTokenTypes.OUTPUT) {
                    return Target.OUTPUT;
                }
                if (currentType == KiteTokenTypes.FUN) {
                    return Target.FUNCTION;
                }
                if (currentType == KiteTokenTypes.VAR) {
                    return Target.VARIABLE;
                }

                // Check for element types (when PSI is fully parsed)
                if (currentType == KiteElementTypes.RESOURCE_DECLARATION) {
                    return Target.RESOURCE;
                }
                if (currentType == KiteElementTypes.COMPONENT_DECLARATION) {
                    return Target.COMPONENT;
                }
                if (currentType == KiteElementTypes.SCHEMA_DECLARATION) {
                    return Target.SCHEMA;
                }
                if (currentType == KiteElementTypes.INPUT_DECLARATION) {
                    return Target.INPUT;
                }
                if (currentType == KiteElementTypes.OUTPUT_DECLARATION) {
                    return Target.OUTPUT;
                }
                if (currentType == KiteElementTypes.FUNCTION_DECLARATION) {
                    return Target.FUNCTION;
                }
                if (currentType == KiteElementTypes.VARIABLE_DECLARATION) {
                    return Target.VARIABLE;
                }

                // If we hit an identifier (type name for schema property), check context
                if (currentType == KiteTokenTypes.IDENTIFIER || currentType == KiteTokenTypes.ANY) {
                    // Could be a schema property type
                    if (isInsideSchemaBody(decoratorName)) {
                        return Target.SCHEMA_PROPERTY;
                    }
                }

                // Stop if we hit something unexpected
                break;
            }
            current = current.getNextSibling();
        }

        return null;
    }

    /**
     * Check if decorator is directly on a schema property (inside schema body).
     */
    private boolean isPropertyDecorator(PsiElement decoratorName) {
        // Walk forward to see what follows the decorator
        PsiElement current = decoratorName;

        // Skip past arguments
        PsiElement afterName = skipWhitespace(decoratorName.getNextSibling());
        if (afterName != null && afterName.getNode() != null &&
            afterName.getNode().getElementType() == KiteTokenTypes.LPAREN) {
            current = skipToClosingParen(afterName);
            if (current != null) {
                current = current.getNextSibling();
            }
        } else {
            current = decoratorName.getNextSibling();
        }

        // Look for type identifier (schema property pattern: type propertyName)
        while (current != null) {
            if (current.getNode() != null) {
                IElementType type = current.getNode().getElementType();
                if (isWhitespaceOrNewline(type)) {
                    current = current.getNextSibling();
                    continue;
                }
                // Skip other decorators
                if (type == KiteTokenTypes.AT) {
                    current = skipDecorator(current);
                    continue;
                }
                // Found identifier = property type
                if (type == KiteTokenTypes.IDENTIFIER || type == KiteTokenTypes.ANY) {
                    return true;
                }
                break;
            }
            current = current.getNextSibling();
        }
        return false;
    }

    /**
     * Check if element is inside a schema body (between { and }).
     */
    private boolean isInsideSchemaBody(PsiElement element) {
        PsiElement parent = element.getParent();
        while (parent != null) {
            if (parent.getNode() != null) {
                IElementType type = parent.getNode().getElementType();
                if (type == KiteElementTypes.SCHEMA_DECLARATION) {
                    return true;
                }
                // Stop at file level or other declarations
                if (type == KiteElementTypes.COMPONENT_DECLARATION ||
                    type == KiteElementTypes.RESOURCE_DECLARATION ||
                    type == KiteElementTypes.FUNCTION_DECLARATION) {
                    return false;
                }
            }
            parent = parent.getParent();
        }
        return false;
    }

    /**
     * Skip to the closing parenthesis.
     */
    @Nullable
    private PsiElement skipToClosingParen(PsiElement lparen) {
        int depth = 1;
        PsiElement current = lparen.getNextSibling();
        while (current != null && depth > 0) {
            if (current.getNode() != null) {
                IElementType type = current.getNode().getElementType();
                if (type == KiteTokenTypes.LPAREN) {
                    depth++;
                } else if (type == KiteTokenTypes.RPAREN) {
                    depth--;
                    if (depth == 0) {
                        return current;
                    }
                }
            }
            current = current.getNextSibling();
        }
        return null;
    }

    /**
     * Skip a decorator (@ name (args)?).
     */
    @Nullable
    private PsiElement skipDecorator(PsiElement at) {
        PsiElement current = at.getNextSibling();

        // Skip to decorator name
        while (current != null && current.getNode() != null &&
               isWhitespaceOrNewline(current.getNode().getElementType())) {
            current = current.getNextSibling();
        }

        if (current == null) return null;

        // Skip name
        current = current.getNextSibling();

        // Skip whitespace
        while (current != null && current.getNode() != null &&
               isWhitespaceOrNewline(current.getNode().getElementType())) {
            current = current.getNextSibling();
        }

        // Check for arguments
        if (current != null && current.getNode() != null &&
            current.getNode().getElementType() == KiteTokenTypes.LPAREN) {
            current = skipToClosingParen(current);
            if (current != null) {
                current = current.getNextSibling();
            }
        }

        return current;
    }

    /**
     * Check if element type is whitespace or newline.
     */
    private boolean isWhitespaceOrNewline(IElementType type) {
        return type == KiteTokenTypes.WHITESPACE ||
               type == KiteTokenTypes.NEWLINE ||
               type == KiteTokenTypes.NL ||
               type == com.intellij.psi.TokenType.WHITE_SPACE;
    }
}
