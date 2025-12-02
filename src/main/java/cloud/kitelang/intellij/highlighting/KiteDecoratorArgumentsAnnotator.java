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

import static cloud.kitelang.intellij.util.KitePsiUtil.skipWhitespace;
import static cloud.kitelang.intellij.util.KitePsiUtil.skipWhitespaceBackward;

/**
 * Annotator that validates decorator arguments match expected types.
 * <p>
 * Example:
 * <pre>
 * @minValue("10")      // Error: @minValue requires a number argument
 * @nonEmpty(true)      // Error: @nonEmpty takes no arguments
 * @description(42)     // Error: @description requires a string argument
 *
 * @minValue(10)        // OK
 * @description("...")  // OK
 * @nonEmpty            // OK
 * @count(replicas)     // OK - variable references allowed
 * </pre>
 * <p>
 * This annotator uses two trigger points to work around the "range must be inside element"
 * constraint:
 * 1. Triggers on decorator name IDENTIFIER - for "missing argument" and "no arguments" errors
 * 2. Triggers on argument tokens - for "wrong argument type" errors
 */
public class KiteDecoratorArgumentsAnnotator implements Annotator {

    /**
     * Decorator argument type requirements.
     */
    private enum ArgType {
        NONE,           // No arguments allowed
        NUMBER,         // Number literal or variable
        STRING,         // String literal
        ARRAY,          // Array literal
        STRING_OR_ARRAY, // String or array
        OBJECT_ARRAY_STRING, // Object, array, or string
        IDENTIFIER_OR_ARRAY, // Identifier or array of identifiers
        NAMED           // Named arguments (e.g., regex: or preset:)
    }

    /**
     * Map of decorator names to their expected argument types.
     */
    private static final Map<String, ArgType> DECORATOR_ARG_TYPES = Map.ofEntries(
            // Number argument decorators
            Map.entry("minValue", ArgType.NUMBER),
            Map.entry("maxValue", ArgType.NUMBER),
            Map.entry("minLength", ArgType.NUMBER),
            Map.entry("maxLength", ArgType.NUMBER),
            Map.entry("count", ArgType.NUMBER),

            // String argument decorators
            Map.entry("description", ArgType.STRING),
            Map.entry("existing", ArgType.STRING),

            // No argument decorators
            Map.entry("nonEmpty", ArgType.NONE),
            Map.entry("sensitive", ArgType.NONE),
            Map.entry("unique", ArgType.NONE),
            Map.entry("cloud", ArgType.NONE),

            // Array argument decorators
            Map.entry("allowed", ArgType.ARRAY),

            // Multiple type decorators
            Map.entry("tags", ArgType.OBJECT_ARRAY_STRING),
            Map.entry("provider", ArgType.STRING_OR_ARRAY),
            Map.entry("provisionOn", ArgType.STRING_OR_ARRAY),
            Map.entry("dependsOn", ArgType.IDENTIFIER_OR_ARRAY),

            // Named argument decorators
            Map.entry("validate", ArgType.NAMED)
    );

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element.getNode() == null) return;

        if (element.getContainingFile() == null ||
            element.getContainingFile().getLanguage() != KiteLanguage.INSTANCE) {
            return;
        }

        IElementType type = element.getNode().getElementType();

        // Path 1: Trigger on decorator name IDENTIFIER (after @)
        // Handles: missing argument errors, no argument errors
        if (type == KiteTokenTypes.IDENTIFIER && isDecoratorName(element)) {
            checkDecoratorFromName(element, holder);
            return;
        }

        // Path 2: Trigger on argument tokens inside decorator parentheses
        // Handles: wrong argument type errors
        if (isArgumentToken(type)) {
            checkArgumentInDecorator(element, holder);
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
     * Check decorator requirements from the name identifier.
     * Handles missing argument and no-argument errors.
     */
    private void checkDecoratorFromName(PsiElement nameElement, AnnotationHolder holder) {
        String decoratorName = nameElement.getText();

        // Check if we have argument type requirements for this decorator
        ArgType expectedType = DECORATOR_ARG_TYPES.get(decoratorName);
        if (expectedType == null) {
            return; // Unknown decorator, handled by different annotator
        }

        // Find the opening parenthesis
        PsiElement afterName = skipWhitespace(nameElement.getNextSibling());
        boolean hasParens = afterName != null && afterName.getNode() != null &&
                           afterName.getNode().getElementType() == KiteTokenTypes.LPAREN;

        // Check for "no arguments" error
        if (expectedType == ArgType.NONE && hasParens) {
            holder.newAnnotation(HighlightSeverity.ERROR,
                            "@" + decoratorName + " takes no arguments")
                    .range(nameElement)
                    .create();
            return;
        }

        // Check for "missing argument" error
        if (expectedType != ArgType.NONE && !hasParens) {
            String requiredType = getRequiredTypeDescription(expectedType);
            holder.newAnnotation(HighlightSeverity.ERROR,
                            "@" + decoratorName + " requires " + requiredType)
                    .range(nameElement)
                    .create();
        }
    }

    /**
     * Get human-readable description of required type.
     */
    private String getRequiredTypeDescription(ArgType type) {
        return switch (type) {
            case NUMBER -> "a number argument";
            case STRING -> "a string argument";
            case ARRAY -> "an array argument";
            case STRING_OR_ARRAY -> "a string or array argument";
            case OBJECT_ARRAY_STRING -> "an object, array, or string argument";
            case IDENTIFIER_OR_ARRAY -> "a resource reference or array argument";
            case NAMED -> "named arguments (regex: or preset:)";
            default -> "an argument";
        };
    }

    /**
     * Check if token type is a potential decorator argument.
     */
    private boolean isArgumentToken(IElementType type) {
        return type == KiteTokenTypes.NUMBER ||
               type == KiteTokenTypes.DQUOTE ||
               type == KiteTokenTypes.SINGLE_STRING ||
               type == KiteTokenTypes.STRING ||
               type == KiteTokenTypes.TRUE ||
               type == KiteTokenTypes.FALSE ||
               type == KiteTokenTypes.LBRACK ||
               type == KiteTokenTypes.LBRACE;
    }

    /**
     * Check if this argument token is inside a decorator and validate its type.
     */
    private void checkArgumentInDecorator(PsiElement argElement, AnnotationHolder holder) {
        // Check if we're inside decorator parentheses
        DecoratorContext ctx = findDecoratorContext(argElement);
        if (ctx == null) {
            return;
        }

        // Check if we have argument type requirements for this decorator
        ArgType expectedType = DECORATOR_ARG_TYPES.get(ctx.decoratorName);
        if (expectedType == null || expectedType == ArgType.NONE) {
            return;
        }

        // Validate argument type
        IElementType argType = argElement.getNode().getElementType();

        if (!isValidArgumentType(argType, expectedType)) {
            String requiredType = getRequiredTypeDescription(expectedType);
            holder.newAnnotation(HighlightSeverity.ERROR,
                            "@" + ctx.decoratorName + " requires " + requiredType)
                    .range(argElement)
                    .create();
        }
    }

    /**
     * Find the decorator context for an argument element.
     * Returns null if not inside a decorator.
     */
    @Nullable
    private DecoratorContext findDecoratorContext(PsiElement argElement) {
        // Walk backward to find LPAREN, then IDENTIFIER, then AT
        PsiElement current = argElement.getPrevSibling();

        // Find the opening paren
        while (current != null) {
            if (current.getNode() != null) {
                IElementType type = current.getNode().getElementType();
                if (type == KiteTokenTypes.LPAREN) {
                    break;
                }
                // If we hit another structural element, stop
                if (type == KiteTokenTypes.RPAREN || type == KiteTokenTypes.SEMICOLON ||
                    type == KiteTokenTypes.LBRACE || type == KiteTokenTypes.RBRACE) {
                    return null;
                }
            }
            current = current.getPrevSibling();
        }

        if (current == null) {
            return null;
        }

        // Found LPAREN, now find decorator name
        PsiElement beforeParen = skipWhitespaceBackward(current.getPrevSibling());
        if (beforeParen == null || beforeParen.getNode() == null) {
            return null;
        }

        if (beforeParen.getNode().getElementType() != KiteTokenTypes.IDENTIFIER) {
            return null;
        }

        // Check for @ before the identifier
        PsiElement beforeName = skipWhitespaceBackward(beforeParen.getPrevSibling());
        if (beforeName == null || beforeName.getNode() == null ||
            beforeName.getNode().getElementType() != KiteTokenTypes.AT) {
            return null;
        }

        return new DecoratorContext(beforeParen.getText(), argElement);
    }

    /**
     * Check if the argument type is valid for the expected type.
     */
    private boolean isValidArgumentType(IElementType argType, ArgType expectedType) {
        return switch (expectedType) {
            case NUMBER -> argType == KiteTokenTypes.NUMBER;
                // Note: IDENTIFIER is checked separately via context to allow variable refs
            case STRING -> argType == KiteTokenTypes.DQUOTE ||
                          argType == KiteTokenTypes.SINGLE_STRING ||
                          argType == KiteTokenTypes.STRING;
            case ARRAY -> argType == KiteTokenTypes.LBRACK;
            case STRING_OR_ARRAY -> argType == KiteTokenTypes.DQUOTE ||
                                   argType == KiteTokenTypes.SINGLE_STRING ||
                                   argType == KiteTokenTypes.STRING ||
                                   argType == KiteTokenTypes.LBRACK;
            case OBJECT_ARRAY_STRING -> argType == KiteTokenTypes.DQUOTE ||
                                       argType == KiteTokenTypes.SINGLE_STRING ||
                                       argType == KiteTokenTypes.STRING ||
                                       argType == KiteTokenTypes.LBRACK ||
                                       argType == KiteTokenTypes.LBRACE;
            case IDENTIFIER_OR_ARRAY -> argType == KiteTokenTypes.LBRACK;
                // IDENTIFIER is valid for this type
            case NAMED -> true; // Named args validation not fully implemented
            case NONE -> false;
        };
    }

    /**
     * Context information about a decorator.
     */
    private record DecoratorContext(String decoratorName, PsiElement argument) {}
}
