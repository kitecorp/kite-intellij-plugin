package cloud.kitelang.intellij.completion;

import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import cloud.kitelang.intellij.structure.KiteStructureViewIcons;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cloud.kitelang.intellij.completion.KiteCompletionHelper.isWhitespace;
import static cloud.kitelang.intellij.completion.KiteCompletionHelper.skipWhitespaceBackward;

/**
 * Provides completions inside component definition bodies.
 * Handles:
 * - Default value suggestions for input/output declarations based on type and property name
 * - Keyword completions (input, output, type keywords) for component definitions
 */
public class KiteComponentDefinitionCompletionProvider extends CompletionProvider<CompletionParameters> {

    // Built-in primitive types
    private static final String[] BUILTIN_TYPES = {
            "string", "number", "boolean", "any", "object"
    };

    // Built-in array types
    private static final String[] BUILTIN_ARRAY_TYPES = {
            "string[]", "number[]", "boolean[]", "any[]", "object[]"
    };

    // ========== Default Value Suggestions ==========

    // String suggestions by property name (case-insensitive matching)
    private static final Map<String, List<DefaultValueSuggestion>> STRING_SUGGESTIONS = new HashMap<>();

    // Number suggestions by property name (case-insensitive matching)
    private static final Map<String, List<DefaultValueSuggestion>> NUMBER_SUGGESTIONS = new HashMap<>();

    static {
        // String property suggestions
        STRING_SUGGESTIONS.put("environment", List.of(
                new DefaultValueSuggestion("\"dev\"", "development"),
                new DefaultValueSuggestion("\"staging\"", "staging"),
                new DefaultValueSuggestion("\"prod\"", "production")
        ));
        STRING_SUGGESTIONS.put("env", STRING_SUGGESTIONS.get("environment"));

        STRING_SUGGESTIONS.put("region", List.of(
                new DefaultValueSuggestion("\"us-east-1\"", "US East"),
                new DefaultValueSuggestion("\"us-west-2\"", "US West"),
                new DefaultValueSuggestion("\"eu-west-1\"", "EU West"),
                new DefaultValueSuggestion("\"ap-southeast-1\"", "Asia Pacific")
        ));

        STRING_SUGGESTIONS.put("protocol", List.of(
                new DefaultValueSuggestion("\"http\"", "HTTP"),
                new DefaultValueSuggestion("\"https\"", "HTTPS"),
                new DefaultValueSuggestion("\"tcp\"", "TCP")
        ));

        STRING_SUGGESTIONS.put("host", List.of(
                new DefaultValueSuggestion("\"localhost\"", "local"),
                new DefaultValueSuggestion("\"0.0.0.0\"", "all interfaces"),
                new DefaultValueSuggestion("\"127.0.0.1\"", "loopback")
        ));
        STRING_SUGGESTIONS.put("hostname", STRING_SUGGESTIONS.get("host"));

        STRING_SUGGESTIONS.put("provider", List.of(
                new DefaultValueSuggestion("\"aws\"", "Amazon Web Services"),
                new DefaultValueSuggestion("\"gcp\"", "Google Cloud Platform"),
                new DefaultValueSuggestion("\"azure\"", "Microsoft Azure")
        ));

        STRING_SUGGESTIONS.put("cidr", List.of(
                new DefaultValueSuggestion("\"10.0.0.0/16\"", "VPC default"),
                new DefaultValueSuggestion("\"10.0.1.0/24\"", "subnet"),
                new DefaultValueSuggestion("\"192.168.0.0/16\"", "private")
        ));

        STRING_SUGGESTIONS.put("instancetype", List.of(
                new DefaultValueSuggestion("\"t2.micro\"", "micro"),
                new DefaultValueSuggestion("\"t3.small\"", "small"),
                new DefaultValueSuggestion("\"t3.medium\"", "medium")
        ));

        STRING_SUGGESTIONS.put("runtime", List.of(
                new DefaultValueSuggestion("\"nodejs18.x\"", "Node.js 18"),
                new DefaultValueSuggestion("\"python3.11\"", "Python 3.11"),
                new DefaultValueSuggestion("\"java17\"", "Java 17")
        ));

        STRING_SUGGESTIONS.put("loglevel", List.of(
                new DefaultValueSuggestion("\"debug\"", "debug"),
                new DefaultValueSuggestion("\"info\"", "info"),
                new DefaultValueSuggestion("\"warn\"", "warning"),
                new DefaultValueSuggestion("\"error\"", "error")
        ));

        // Number property suggestions
        NUMBER_SUGGESTIONS.put("port", List.of(
                new DefaultValueSuggestion("80", "HTTP"),
                new DefaultValueSuggestion("443", "HTTPS"),
                new DefaultValueSuggestion("22", "SSH"),
                new DefaultValueSuggestion("3000", "Dev server"),
                new DefaultValueSuggestion("3306", "MySQL"),
                new DefaultValueSuggestion("5432", "PostgreSQL"),
                new DefaultValueSuggestion("6379", "Redis"),
                new DefaultValueSuggestion("8080", "Alt HTTP"),
                new DefaultValueSuggestion("27017", "MongoDB")
        ));

        NUMBER_SUGGESTIONS.put("timeout", List.of(
                new DefaultValueSuggestion("30", "30s"),
                new DefaultValueSuggestion("60", "1min"),
                new DefaultValueSuggestion("300", "5min"),
                new DefaultValueSuggestion("3600", "1hr")
        ));

        NUMBER_SUGGESTIONS.put("memory", List.of(
                new DefaultValueSuggestion("128", "128MB"),
                new DefaultValueSuggestion("256", "256MB"),
                new DefaultValueSuggestion("512", "512MB"),
                new DefaultValueSuggestion("1024", "1GB")
        ));

        NUMBER_SUGGESTIONS.put("replicas", List.of(
                new DefaultValueSuggestion("1", "single"),
                new DefaultValueSuggestion("2", "HA"),
                new DefaultValueSuggestion("3", "HA+1")
        ));

        NUMBER_SUGGESTIONS.put("ttl", List.of(
                new DefaultValueSuggestion("60", "1min"),
                new DefaultValueSuggestion("300", "5min"),
                new DefaultValueSuggestion("3600", "1hr")
        ));
    }

    /**
     * A default value suggestion with display and description.
     */
    private static class DefaultValueSuggestion {
        final String value;
        final String description;

        DefaultValueSuggestion(String value, String description) {
            this.value = value;
            this.description = description;
        }
    }

    // ========== Context Classes ==========

    /**
     * Context information about an enclosing component definition block.
     * Only for component DEFINITIONS (component TypeName { }), not instances.
     */
    private static class ComponentDefinitionContext {
        final String componentTypeName;
        final PsiElement componentDeclaration;

        ComponentDefinitionContext(String componentTypeName, PsiElement componentDeclaration) {
            this.componentTypeName = componentTypeName;
            this.componentDeclaration = componentDeclaration;
        }
    }

    /**
     * Information about an input/output declaration's type and property name.
     */
    private static class InputOutputInfo {
        final String type;       // "string", "number", "boolean", etc.
        final String name;       // property name like "port", "host", "enabled"
        final boolean isInput;   // true for input, false for output

        InputOutputInfo(String type, String name, boolean isInput) {
            this.type = type;
            this.name = name;
            this.isInput = isInput;
        }
    }

    // ========== Main Completion Method ==========

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
        PsiElement position = parameters.getPosition();

        // Check if we're inside a component DEFINITION body (not instance)
        ComponentDefinitionContext componentDefContext = getEnclosingComponentDefinitionContext(position);
        if (componentDefContext == null) {
            return; // Not in a component definition context
        }

        // Check if we're after = in an input/output declaration
        InputOutputInfo inputOutputInfo = getInputOutputInfo(position);
        if (inputOutputInfo != null) {
            // Provide type-based default value suggestions
            addComponentDefinitionDefaultValueCompletions(result, inputOutputInfo);
        } else if (!isBeforeAssignment(position)) {
            // Not in value context - provide keyword completions
            addComponentDefinitionKeywordCompletions(result);
        } else {
            // Before assignment - provide keyword completions
            addComponentDefinitionKeywordCompletions(result);
        }
    }

    /**
     * Check if we're inside a component definition context.
     * This is a public static method so other completion providers can check
     * and skip when we're in component definition context.
     */
    public static boolean isInComponentDefinitionContext(@NotNull PsiElement position) {
        return getEnclosingComponentDefinitionContextStatic(position) != null;
    }

    // ========== Context Detection ==========

    /**
     * Get the component definition context if we're inside a component definition body.
     * Returns null if not inside a component definition, or if it's a component instance.
     * <p>
     * Component definition: component TypeName { } (only one identifier before {)
     * Component instance: component TypeName instanceName { } (two identifiers before {)
     */
    @Nullable
    private ComponentDefinitionContext getEnclosingComponentDefinitionContext(PsiElement position) {
        return getEnclosingComponentDefinitionContextStatic(position);
    }

    /**
     * Static version for use in isInComponentDefinitionContext check.
     */
    @Nullable
    private static ComponentDefinitionContext getEnclosingComponentDefinitionContextStatic(PsiElement position) {
        // Walk up the PSI tree to find an enclosing COMPONENT_DECLARATION
        PsiElement current = position;
        while (current != null) {
            if (current.getNode() != null &&
                current.getNode().getElementType() == KiteElementTypes.COMPONENT_DECLARATION) {
                // Found component declaration - check if we're inside the braces
                if (isInsideBraces(position, current)) {
                    // Check if this is a definition (one identifier) vs instance (two identifiers)
                    if (isComponentDefinition(current)) {
                        String typeName = extractComponentTypeName(current);
                        if (typeName != null) {
                            return new ComponentDefinitionContext(typeName, current);
                        }
                    }
                }
            }
            current = current.getParent();
        }
        return null;
    }

    /**
     * Check if a COMPONENT_DECLARATION is a definition (not an instance).
     * A definition has only one identifier before the opening brace.
     * An instance has two identifiers (TypeName instanceName).
     */
    private static boolean isComponentDefinition(PsiElement componentDeclaration) {
        int identifierCount = 0;
        boolean foundLBrace = false;

        for (PsiElement child = componentDeclaration.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() != null) {
                IElementType type = child.getNode().getElementType();
                if (type == KiteTokenTypes.LBRACE) {
                    foundLBrace = true;
                    break;
                }
                if (type == KiteTokenTypes.IDENTIFIER) {
                    identifierCount++;
                }
            }
        }

        // Definition has exactly one identifier before {, instance has two
        return foundLBrace && identifierCount == 1;
    }

    /**
     * Extract the type name from a component declaration.
     * For "component WebServer { }" returns "WebServer"
     */
    @Nullable
    private static String extractComponentTypeName(PsiElement componentDeclaration) {
        for (PsiElement child = componentDeclaration.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() != null) {
                IElementType type = child.getNode().getElementType();
                if (type == KiteTokenTypes.IDENTIFIER) {
                    return child.getText();
                }
                if (type == KiteTokenTypes.LBRACE) {
                    break; // Past the header
                }
            }
        }
        return null;
    }

    /**
     * Check if position is inside the braces of a declaration.
     */
    private static boolean isInsideBraces(PsiElement position, PsiElement declaration) {
        int posOffset = position.getTextOffset();
        boolean foundLBrace = false;
        int lbraceOffset = -1;
        int rbraceOffset = -1;

        for (PsiElement child = declaration.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() != null) {
                IElementType type = child.getNode().getElementType();
                if (type == KiteTokenTypes.LBRACE) {
                    foundLBrace = true;
                    lbraceOffset = child.getTextOffset();
                } else if (type == KiteTokenTypes.RBRACE && foundLBrace) {
                    rbraceOffset = child.getTextOffset();
                    break;
                }
            }
        }

        return foundLBrace && lbraceOffset >= 0 && posOffset > lbraceOffset &&
               (rbraceOffset < 0 || posOffset < rbraceOffset);
    }

    /**
     * Check if position is before an assignment operator (= or +=).
     */
    private boolean isBeforeAssignment(PsiElement position) {
        // Check if the next non-whitespace element is an assignment
        PsiElement next = position.getNextSibling();
        while (next != null && isWhitespace(next)) {
            next = next.getNextSibling();
        }
        if (next != null && next.getNode() != null) {
            IElementType type = next.getNode().getElementType();
            return type == KiteTokenTypes.ASSIGN || type == KiteTokenTypes.PLUS_ASSIGN;
        }
        return false;
    }

    // ========== Input/Output Info Extraction ==========

    /**
     * Extract the type and property name from the current input/output line.
     * Parses backwards from position to find: input/output type name =
     * Returns null if not on an input/output line with an assignment.
     */
    @Nullable
    private InputOutputInfo getInputOutputInfo(PsiElement position) {
        // First check if we're after an = sign (not before)
        if (isBeforeAssignment(position)) {
            return null; // Not after =, so not in value position
        }

        // Walk backwards to find input/output keyword, type, and name
        String type = null;
        String name = null;
        Boolean isInput = null;

        PsiElement current = position.getPrevSibling();

        // Collect tokens going backwards until we hit a newline or find what we need
        List<String> tokens = new ArrayList<>();
        List<IElementType> tokenTypes = new ArrayList<>();

        while (current != null) {
            if (current.getNode() != null) {
                IElementType elementType = current.getNode().getElementType();

                // Stop at newline
                if (elementType == KiteTokenTypes.NL || elementType == KiteTokenTypes.NEWLINE) {
                    break;
                }

                // Skip whitespace
                if (isWhitespace(current)) {
                    current = current.getPrevSibling();
                    continue;
                }

                tokens.add(0, current.getText());
                tokenTypes.add(0, elementType);
            }
            current = current.getPrevSibling();
        }

        // Also check parent's siblings
        PsiElement parent = position.getParent();
        if (parent != null) {
            current = parent.getPrevSibling();
            while (current != null) {
                if (current.getNode() != null) {
                    IElementType elementType = current.getNode().getElementType();

                    if (elementType == KiteTokenTypes.NL || elementType == KiteTokenTypes.NEWLINE) {
                        break;
                    }

                    if (!isWhitespace(current)) {
                        tokens.add(0, current.getText());
                        tokenTypes.add(0, elementType);
                    }
                }
                current = current.getPrevSibling();
            }
        }

        // Parse tokens: looking for pattern "input/output type name ="
        // Example: ["input", "string", "port", "="] or ["output", "number", "count", "="]
        for (int i = 0; i < tokens.size(); i++) {
            String text = tokens.get(i);
            IElementType tokenType = tokenTypes.get(i);

            if (tokenType == KiteTokenTypes.INPUT) {
                isInput = true;
            } else if (tokenType == KiteTokenTypes.OUTPUT) {
                isInput = false;
            } else if (tokenType == KiteTokenTypes.IDENTIFIER || tokenType == KiteTokenTypes.ANY) {
                // Could be type or name - type comes first
                if (type == null) {
                    type = text;
                } else if (name == null) {
                    name = text;
                }
            }
        }

        if (isInput != null && type != null && name != null) {
            return new InputOutputInfo(type, name, isInput);
        }

        return null;
    }

    // ========== Completion Methods ==========

    /**
     * Add default value completions for component definitions.
     * Called when cursor is after = in an input/output declaration.
     */
    private void addComponentDefinitionDefaultValueCompletions(@NotNull CompletionResultSet result,
                                                               @NotNull InputOutputInfo info) {
        String typeLower = info.type.toLowerCase();
        String nameLower = info.name.toLowerCase();

        // Boolean type suggestions
        if (typeLower.equals("boolean") || typeLower.equals("bool")) {
            addBooleanCompletions(result);
            return;
        }

        // Number type suggestions
        if (typeLower.equals("number") || typeLower.equals("int") || typeLower.equals("integer") ||
            typeLower.equals("float") || typeLower.equals("double")) {
            // Try property-name-based suggestions first
            List<DefaultValueSuggestion> suggestions = NUMBER_SUGGESTIONS.get(nameLower);
            if (suggestions != null && !suggestions.isEmpty()) {
                double priority = 600.0;
                for (DefaultValueSuggestion suggestion : suggestions) {
                    LookupElementBuilder element = LookupElementBuilder.create(suggestion.value)
                            .withTypeText(suggestion.description, true)
                            .withIcon(KiteStructureViewIcons.PROPERTY)
                            .withBoldness(true);
                    result.addElement(PrioritizedLookupElement.withPriority(element, priority));
                    priority -= 10.0;
                }
            } else {
                // Generic number suggestions
                addGenericNumberCompletions(result);
            }
            return;
        }

        // String type suggestions
        if (typeLower.equals("string") || typeLower.equals("str")) {
            // Try property-name-based suggestions first
            List<DefaultValueSuggestion> suggestions = STRING_SUGGESTIONS.get(nameLower);
            if (suggestions != null && !suggestions.isEmpty()) {
                double priority = 600.0;
                for (DefaultValueSuggestion suggestion : suggestions) {
                    LookupElementBuilder element = LookupElementBuilder.create(suggestion.value)
                            .withTypeText(suggestion.description, true)
                            .withIcon(KiteStructureViewIcons.PROPERTY)
                            .withBoldness(true);
                    result.addElement(PrioritizedLookupElement.withPriority(element, priority));
                    priority -= 10.0;
                }
            } else {
                // Generic string suggestion - empty string
                LookupElementBuilder element = LookupElementBuilder.create("\"\"")
                        .withTypeText("empty string", true)
                        .withIcon(KiteStructureViewIcons.PROPERTY)
                        .withInsertHandler((ctx, item) -> {
                            // Position cursor between the quotes
                            ctx.getEditor().getCaretModel().moveToOffset(ctx.getTailOffset() - 1);
                        });
                result.addElement(PrioritizedLookupElement.withPriority(element, 500.0));
            }
            return;
        }

        // For any other type, add boolean and common literals
        addBooleanCompletions(result);
    }

    /**
     * Add boolean literal completions.
     */
    private void addBooleanCompletions(@NotNull CompletionResultSet result) {
        LookupElementBuilder trueElement = LookupElementBuilder.create("true")
                .withTypeText("boolean", true)
                .withIcon(KiteStructureViewIcons.PROPERTY)
                .withBoldness(true);
        result.addElement(PrioritizedLookupElement.withPriority(trueElement, 600.0));

        LookupElementBuilder falseElement = LookupElementBuilder.create("false")
                .withTypeText("boolean", true)
                .withIcon(KiteStructureViewIcons.PROPERTY)
                .withBoldness(true);
        result.addElement(PrioritizedLookupElement.withPriority(falseElement, 590.0));
    }

    /**
     * Add generic number completions when no property-specific suggestions exist.
     */
    private void addGenericNumberCompletions(@NotNull CompletionResultSet result) {
        String[] numbers = {"0", "1", "10", "100"};
        String[] descriptions = {"zero", "one", "ten", "hundred"};
        double priority = 500.0;

        for (int i = 0; i < numbers.length; i++) {
            LookupElementBuilder element = LookupElementBuilder.create(numbers[i])
                    .withTypeText(descriptions[i], true)
                    .withIcon(KiteStructureViewIcons.PROPERTY);
            result.addElement(PrioritizedLookupElement.withPriority(element, priority));
            priority -= 10.0;
        }
    }

    /**
     * Add keyword completions for component definition bodies.
     * Only shows input, output, and type keywords - NOT variables, functions, etc.
     */
    private void addComponentDefinitionKeywordCompletions(@NotNull CompletionResultSet result) {
        // Input/output keywords (highest priority)
        LookupElementBuilder inputElement = LookupElementBuilder.create("input")
                .withTypeText("keyword")
                .withIcon(KiteStructureViewIcons.INPUT)
                .withBoldness(true);
        result.addElement(PrioritizedLookupElement.withPriority(inputElement, 600.0));

        LookupElementBuilder outputElement = LookupElementBuilder.create("output")
                .withTypeText("keyword")
                .withIcon(KiteStructureViewIcons.OUTPUT)
                .withBoldness(true);
        result.addElement(PrioritizedLookupElement.withPriority(outputElement, 590.0));

        // Built-in types
        double typePriority = 400.0;
        for (String type : BUILTIN_TYPES) {
            LookupElementBuilder element = LookupElementBuilder.create(type)
                    .withTypeText("type")
                    .withIcon(KiteStructureViewIcons.TYPE);
            result.addElement(PrioritizedLookupElement.withPriority(element, typePriority));
            typePriority -= 5.0;
        }

        // Array types
        typePriority = 350.0;
        for (String type : BUILTIN_ARRAY_TYPES) {
            LookupElementBuilder element = LookupElementBuilder.create(type)
                    .withTypeText("array type")
                    .withIcon(KiteStructureViewIcons.TYPE);
            result.addElement(PrioritizedLookupElement.withPriority(element, typePriority));
            typePriority -= 5.0;
        }
    }
}
