package cloud.kitelang.intellij.completion;

import cloud.kitelang.intellij.KiteLanguage;
import cloud.kitelang.intellij.documentation.KiteDocumentationProvider;
import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import cloud.kitelang.intellij.reference.KiteImportHelper;
import cloud.kitelang.intellij.structure.KiteStructureViewIcons;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * Provides code completion for the Kite language.
 *
 * Completion types:
 * - Keywords (resource, component, var, input, output, etc.)
 * - Declared identifiers (variables, resources, components, etc.)
 * - Property access (object.property)
 * - Built-in types (string, number, boolean, etc.)
 */
public class KiteCompletionContributor extends CompletionContributor {

    // Keywords that can start a declaration or statement
    private static final String[] TOP_LEVEL_KEYWORDS = {
        "resource", "component", "schema", "fun", "type",
        "var", "input", "output", "import"
    };

    // Control flow keywords
    private static final String[] CONTROL_KEYWORDS = {
        "if", "else", "for", "while", "in", "return"
    };

    // Built-in types
    private static final String[] BUILTIN_TYPES = {
        "string", "number", "boolean", "object", "any"
    };

    // Built-in array types
    private static final String[] BUILTIN_ARRAY_TYPES = {
            "string[]", "number[]", "boolean[]", "object[]", "any[]"
    };

    // Literals
    private static final String[] LITERAL_KEYWORDS = {
        "true", "false", "null", "this"
    };

    // ========== Component Definition Default Value Suggestions ==========

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
                new DefaultValueSuggestion("128", "128 MB"),
                new DefaultValueSuggestion("256", "256 MB"),
                new DefaultValueSuggestion("512", "512 MB"),
                new DefaultValueSuggestion("1024", "1 GB")
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

    // Decorator categories for grouping
    private static final String CATEGORY_VALIDATION = "validation";
    private static final String CATEGORY_RESOURCE = "resource";
    private static final String CATEGORY_METADATA = "metadata";

    // Target types for decorators
    private static final int TARGET_INPUT = 1;
    private static final int TARGET_OUTPUT = 2;
    private static final int TARGET_RESOURCE = 4;
    private static final int TARGET_COMPONENT = 8;
    private static final int TARGET_VAR = 16;
    private static final int TARGET_SCHEMA = 32;
    private static final int TARGET_SCHEMA_PROPERTY = 64;
    private static final int TARGET_FUN = 128;

    // Common target combinations
    private static final int TARGET_INPUT_OUTPUT = TARGET_INPUT | TARGET_OUTPUT;
    private static final int TARGET_RESOURCE_COMPONENT = TARGET_RESOURCE | TARGET_COMPONENT;
    private static final int TARGET_ALL = TARGET_INPUT | TARGET_OUTPUT | TARGET_RESOURCE | TARGET_COMPONENT | TARGET_VAR | TARGET_SCHEMA | TARGET_SCHEMA_PROPERTY | TARGET_FUN;

    // Decorator definitions with name, description, template, category, and valid targets
    private static final DecoratorInfo[] DECORATORS = {
            // Validation decorators - input/output only
            new DecoratorInfo("minValue", "Minimum numeric value", "(n)", "minValue($END$)", CATEGORY_VALIDATION, TARGET_INPUT_OUTPUT),
            new DecoratorInfo("maxValue", "Maximum numeric value", "(n)", "maxValue($END$)", CATEGORY_VALIDATION, TARGET_INPUT_OUTPUT),
            new DecoratorInfo("minLength", "Minimum string/array length", "(n)", "minLength($END$)", CATEGORY_VALIDATION, TARGET_INPUT_OUTPUT),
            new DecoratorInfo("maxLength", "Maximum string/array length", "(n)", "maxLength($END$)", CATEGORY_VALIDATION, TARGET_INPUT_OUTPUT),
            new DecoratorInfo("nonEmpty", "Must not be empty", "", "nonEmpty", CATEGORY_VALIDATION, TARGET_INPUT),
            new DecoratorInfo("validate", "Custom regex validation", "(regex: \"...\")", "validate(regex: \"$END$\")", CATEGORY_VALIDATION, TARGET_INPUT_OUTPUT),
            new DecoratorInfo("allowed", "Allowed values whitelist", "([...])", "allowed([$END$])", CATEGORY_VALIDATION, TARGET_INPUT),
            new DecoratorInfo("unique", "Array elements must be unique", "", "unique", CATEGORY_VALIDATION, TARGET_INPUT),

            // Resource decorators - resource/component only
            new DecoratorInfo("existing", "Reference existing resource", "(\"ref\")", "existing(\"$END$\")", CATEGORY_RESOURCE, TARGET_RESOURCE),
            new DecoratorInfo("sensitive", "Mark as sensitive data", "", "sensitive", CATEGORY_RESOURCE, TARGET_INPUT_OUTPUT),
            new DecoratorInfo("dependsOn", "Explicit dependencies", "(res) or ([...])", "dependsOn($END$)", CATEGORY_RESOURCE, TARGET_RESOURCE_COMPONENT),
            new DecoratorInfo("tags", "Cloud provider tags", "({...})", "tags({$END$})", CATEGORY_RESOURCE, TARGET_RESOURCE_COMPONENT),
            new DecoratorInfo("provider", "Target cloud providers", "(\"...\") or ([...])", "provider(\"$END$\")", CATEGORY_RESOURCE, TARGET_RESOURCE_COMPONENT),

            // Metadata decorators
            new DecoratorInfo("description", "Documentation text", "(\"...\")", "description(\"$END$\")", CATEGORY_METADATA, TARGET_ALL),
            new DecoratorInfo("count", "Create N instances", "(n)", "count($END$)", CATEGORY_METADATA, TARGET_RESOURCE_COMPONENT),
            new DecoratorInfo("cloud", "Set by cloud provider", "", "cloud", CATEGORY_METADATA, TARGET_SCHEMA_PROPERTY),
    };

    /**
     * Information about a decorator for code completion.
     */
    private static class DecoratorInfo {
        final String name;
        final String description;
        final String tailText;   // Shows argument format like "(n)" or "([...])"
        final String template;   // Template with $END$ marker for cursor position
        final String category;   // Category for grouping
        final int targets;       // Bitmask of valid target declaration types

        DecoratorInfo(String name, String description, String tailText, String template, String category, int targets) {
            this.name = name;
            this.description = description;
            this.tailText = tailText;
            this.template = template;
            this.category = category;
            this.targets = targets;
        }

        boolean hasArgs() {
            return !tailText.isEmpty();
        }

        boolean canApplyTo(int targetType) {
            return (targets & targetType) != 0;
        }
    }

    public KiteCompletionContributor() {
        // General completion provider for Kite files
        extend(CompletionType.BASIC,
            PlatformPatterns.psiElement().withLanguage(KiteLanguage.INSTANCE),
            new CompletionProvider<>() {
                @Override
                protected void addCompletions(@NotNull CompletionParameters parameters,
                                              @NotNull ProcessingContext context,
                                              @NotNull CompletionResultSet result) {
                    PsiElement position = parameters.getPosition();
                    PsiElement originalPosition = parameters.getOriginalPosition();

                    // Check if we're after @ (decorator context)
                    if (isDecoratorContext(position)) {
                        int targetType = detectDecoratorTargetType(parameters);
                        addDecoratorCompletions(result, targetType);
                        return;
                    }

                    // Check if we're inside decorator argument parentheses
                    String decoratorName = getEnclosingDecoratorName(position);
                    if (decoratorName != null) {
                        addDecoratorArgumentCompletions(result, decoratorName);
                        return;
                    }

                    // Check if we're after a dot (property access)
                    if (isPropertyAccessContext(position)) {
                        addPropertyCompletions(parameters, result);
                        return;
                    }

                    // Check if we're in a type position
                    if (isTypeContext(position)) {
                        addTypeCompletions(result);
                        addDeclaredTypeCompletions(parameters.getOriginalFile(), result);
                        return;
                    }

                    // Check if we're inside a resource block
                    ResourceContext resourceContext = getEnclosingResourceContext(position);
                    if (resourceContext != null) {
                        // Check if we're on the LEFT side of = (property name) or RIGHT side (value)
                        if (isBeforeAssignment(position)) {
                            // LEFT side: only schema properties (non-@cloud)
                            addSchemaPropertyCompletions(parameters.getOriginalFile(), result, resourceContext);
                            return;
                        } else {
                            // RIGHT side: variables, resources, components, functions
                            addValueCompletions(parameters.getOriginalFile(), result);
                            return;
                        }
                    }

                    // Check if we're inside a component DEFINITION body (not instance)
                    ComponentDefinitionContext componentDefContext = getEnclosingComponentDefinitionContext(position);
                    if (componentDefContext != null) {
                        // Check if we're after = in an input/output declaration
                        InputOutputInfo inputOutputInfo = getInputOutputInfo(position);
                        if (inputOutputInfo != null) {
                            // After = in input/output: show type-aware default value suggestions
                            addComponentDefinitionDefaultValueCompletions(result, inputOutputInfo);
                            return;
                        } else {
                            // NOT after =: show only input/output keywords and types
                            // Don't show variables, functions, resources, etc.
                            addComponentDefinitionKeywordCompletions(result);
                            return;
                        }
                    }

                    // Add keyword completions
                    addKeywordCompletions(result, position);

                    // Add declared identifier completions
                    addIdentifierCompletions(parameters.getOriginalFile(), result, position);
                }
            }
        );
    }

    /**
     * Check if cursor is after @ (decorator context)
     */
    private boolean isDecoratorContext(PsiElement position) {
        // Direct previous sibling is @
        PsiElement prev = position.getPrevSibling();
        if (prev != null && prev.getNode() != null) {
            if (prev.getNode().getElementType() == KiteTokenTypes.AT) {
                return true;
            }
        }

        // Also check parent's previous sibling (for cases where PSI structure differs)
        PsiElement parent = position.getParent();
        if (parent != null) {
            prev = parent.getPrevSibling();
            if (prev != null && prev.getNode() != null) {
                if (prev.getNode().getElementType() == KiteTokenTypes.AT) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Detect the target type that a decorator at the current position would apply to.
     * Uses text-based lookahead on the ORIGINAL file (before dummy identifier insertion)
     * to find the first declaration keyword after the current position.
     */
    private int detectDecoratorTargetType(CompletionParameters parameters) {
        PsiElement position = parameters.getPosition();

        // First, check if we're inside a schema body - if so, decorators apply to properties
        if (isInsideSchemaBody(position)) {
            return TARGET_SCHEMA_PROPERTY;
        }

        // Use the ORIGINAL file text (without the dummy identifier "IntelliJRulezz")
        // This is the actual file content before completion was triggered
        PsiFile originalFile = parameters.getOriginalFile();
        if (originalFile == null) {
            return TARGET_ALL;
        }

        String text = originalFile.getText();
        // Use the offset in the original file, not the position's offset (which includes dummy)
        int startOffset = parameters.getOffset();

        // Scan forward from cursor position to find the declaration keyword
        int i = startOffset;
        while (i < text.length()) {
            char c = text.charAt(i);

            // Skip whitespace
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            // Skip line comments
            if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
                while (i < text.length() && text.charAt(i) != '\n') i++;
                continue;
            }

            // Check for declaration keyword or identifier
            if (Character.isLetter(c) || c == '_') {
                int wordStart = i;
                while (i < text.length() && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_')) {
                    i++;
                }
                String word = text.substring(wordStart, i);

                // Check if this word is a declaration keyword
                switch (word) {
                    case "input":
                        return TARGET_INPUT;
                    case "output":
                        return TARGET_OUTPUT;
                    case "resource":
                        return TARGET_RESOURCE;
                    case "component":
                        return TARGET_COMPONENT;
                    case "var":
                        return TARGET_VAR;
                    case "schema":
                        return TARGET_SCHEMA;
                    case "fun":
                        return TARGET_FUN;
                }

                // It's some other identifier - continue looking
                continue;
            }

            // Skip decorator @ and its name/arguments (another decorator before the declaration)
            if (c == '@') {
                i++;
                // Skip whitespace after @
                while (i < text.length() && Character.isWhitespace(text.charAt(i))) i++;
                // Skip decorator name
                while (i < text.length() && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_')) i++;
                // Skip whitespace
                while (i < text.length() && Character.isWhitespace(text.charAt(i))) i++;
                // Skip decorator arguments if present
                if (i < text.length() && text.charAt(i) == '(') {
                    int parenDepth = 1;
                    i++;
                    while (i < text.length() && parenDepth > 0) {
                        if (text.charAt(i) == '(') parenDepth++;
                        else if (text.charAt(i) == ')') parenDepth--;
                        i++;
                    }
                }
                continue;
            }

            // Skip parentheses, brackets, braces (could be decorator arguments)
            if (c == '(' || c == ')' || c == '[' || c == ']' || c == '{' || c == '}') {
                i++;
                continue;
            }

            // Skip other common characters that might appear
            if (c == ',' || c == ':' || c == '=' || c == '"' || c == '\'') {
                i++;
                continue;
            }

            // Any other unexpected character - stop searching
            break;
        }

        // Could not determine - show all decorators
        return TARGET_ALL;
    }

    /**
     * Skip past a decorator (@ identifier and optional arguments).
     */
    private PsiElement skipPastDecorator(PsiElement atSymbol) {
        PsiElement current = atSymbol.getNextSibling();

        // Skip whitespace
        while (current != null && isWhitespace(current)) {
            current = current.getNextSibling();
        }

        // Skip identifier (decorator name)
        if (current != null && current.getNode() != null &&
            current.getNode().getElementType() == KiteTokenTypes.IDENTIFIER) {
            current = current.getNextSibling();
        }

        // Skip whitespace
        while (current != null && isWhitespace(current)) {
            current = current.getNextSibling();
        }

        // Skip arguments if present (parentheses)
        if (current != null && current.getNode() != null &&
            current.getNode().getElementType() == KiteTokenTypes.LPAREN) {
            int parenDepth = 1;
            current = current.getNextSibling();
            while (current != null && parenDepth > 0) {
                IElementType type = current.getNode() != null ? current.getNode().getElementType() : null;
                if (type == KiteTokenTypes.LPAREN) parenDepth++;
                if (type == KiteTokenTypes.RPAREN) parenDepth--;
                current = current.getNextSibling();
            }
        }

        return current;
    }

    /**
     * Check if position is inside a schema body (between { and }).
     */
    private boolean isInsideSchemaBody(PsiElement position) {
        PsiElement current = position;
        while (current != null) {
            if (current.getNode() != null &&
                current.getNode().getElementType() == KiteElementTypes.SCHEMA_DECLARATION) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    /**
     * Add decorator completions with nice formatting.
     * Only shows decorators that are valid for the detected target type.
     */
    private void addDecoratorCompletions(@NotNull CompletionResultSet result, int targetType) {
        for (DecoratorInfo decorator : DECORATORS) {
            // Skip decorators that don't apply to this target
            if (!decorator.canApplyTo(targetType)) {
                continue;
            }

            // Use DecoratorLookupItem as the lookup object to enable documentation popup
            KiteDocumentationProvider.DecoratorLookupItem lookupItem =
                    new KiteDocumentationProvider.DecoratorLookupItem(decorator.name);

            LookupElementBuilder element = LookupElementBuilder.create(lookupItem, decorator.name)
                    .withIcon(KiteStructureViewIcons.DECORATOR)
                    .withTypeText(decorator.category, true)
                    .withTailText(decorator.tailText.isEmpty() ? "" : " " + decorator.tailText, true)
                    .withBoldness(true);

            // Add insert handler to place cursor correctly for decorators with arguments
            if (decorator.hasArgs()) {
                element = element.withInsertHandler((ctx, item) -> {
                    // Get the template and find where to place cursor
                    String template = decorator.template;
                    int endMarkerPos = template.indexOf("$END$");

                    if (endMarkerPos >= 0) {
                        // Remove the decorator name from template to get suffix
                        String suffix = template.substring(decorator.name.length()).replace("$END$", "");
                        ctx.getDocument().insertString(ctx.getTailOffset(), suffix);

                        // Position cursor at the $END$ location
                        int newCursorPos = ctx.getStartOffset() + endMarkerPos;
                        ctx.getEditor().getCaretModel().moveToOffset(newCursorPos);
                    }
                });
            }

            // Priority by category: validation highest, then resource, then metadata
            double priority = switch (decorator.category) {
                case CATEGORY_VALIDATION -> 300.0;
                case CATEGORY_RESOURCE -> 200.0;
                case CATEGORY_METADATA -> 100.0;
                default -> 50.0;
            };

            result.addElement(PrioritizedLookupElement.withPriority(element, priority));
        }
    }

    /**
     * Get the decorator name if cursor is inside decorator argument parentheses.
     * Returns null if not inside decorator arguments.
     * <p>
     * Example: @minValue(|) -> returns "minValue"
     *
     * @validate(regex: "|") -> returns "validate"
     */
    @Nullable
    private String getEnclosingDecoratorName(PsiElement position) {
        // Walk backwards through siblings and parents to find LPAREN
        PsiElement current = position;
        int parenDepth = 0;

        while (current != null) {
            if (current.getNode() != null) {
                IElementType type = current.getNode().getElementType();

                if (type == KiteTokenTypes.RPAREN) {
                    parenDepth++;
                } else if (type == KiteTokenTypes.LPAREN) {
                    if (parenDepth == 0) {
                        // Found the opening paren, look for identifier before it (decorator name)
                        PsiElement prev = current.getPrevSibling();
                        while (prev != null && isWhitespace(prev)) {
                            prev = prev.getPrevSibling();
                        }
                        if (prev != null && prev.getNode() != null &&
                            prev.getNode().getElementType() == KiteTokenTypes.IDENTIFIER) {
                            // Check if this is preceded by @ (it's a decorator)
                            PsiElement beforeIdent = prev.getPrevSibling();
                            while (beforeIdent != null && isWhitespace(beforeIdent)) {
                                beforeIdent = beforeIdent.getPrevSibling();
                            }
                            if (beforeIdent != null && beforeIdent.getNode() != null &&
                                beforeIdent.getNode().getElementType() == KiteTokenTypes.AT) {
                                return prev.getText();
                            }
                        }
                        return null;
                    }
                    parenDepth--;
                }
            }

            // Move to previous sibling or parent
            PsiElement prev = current.getPrevSibling();
            if (prev == null) {
                current = current.getParent();
            } else {
                current = prev;
            }
        }

        return null;
    }

    /**
     * Add completions specific to decorator arguments.
     * Different decorators get different completions:
     * - @validate -> regex:, preset:
     * - @provider, @provisionOn -> cloud provider strings
     * - @tags -> object syntax helpers
     * - @allowed -> array syntax helpers
     * - @existing -> reference type helpers
     * - @dependsOn -> resource/component references
     */
    private void addDecoratorArgumentCompletions(@NotNull CompletionResultSet result, String decoratorName) {
        switch (decoratorName) {
            case "validate" -> addValidateCompletions(result);
            case "provider", "provisionOn" -> addProviderCompletions(result);
            case "tags" -> addTagsCompletions(result);
            case "allowed" -> addAllowedCompletions(result);
            case "existing" -> addExistingCompletions(result);
            case "dependsOn" -> addDependsOnCompletions(result);
            case "description" -> addDescriptionCompletions(result);
            // For simple numeric decorators like @minValue, @maxValue, @count, @minLength, @maxLength
            // don't add any special completions - user should type numbers
        }
    }

    /**
     * Add completions for @validate decorator arguments.
     * Supports named arguments: regex: "pattern" or preset: "presetName"
     */
    private void addValidateCompletions(@NotNull CompletionResultSet result) {
        // Named argument: regex
        result.addElement(PrioritizedLookupElement.withPriority(
                LookupElementBuilder.create("regex: \"\"")
                        .withPresentableText("regex:")
                        .withTailText(" \"pattern\"", true)
                        .withTypeText("named arg", true)
                        .withInsertHandler((ctx, item) -> {
                            // Position cursor inside the quotes
                            ctx.getEditor().getCaretModel().moveToOffset(ctx.getTailOffset() - 1);
                        }),
                200.0));

        // Named argument: preset with common values
        String[] presets = {"email", "url", "uuid", "ipv4", "hostname", "phone", "date"};
        for (String preset : presets) {
            result.addElement(PrioritizedLookupElement.withPriority(
                    LookupElementBuilder.create("preset: \"" + preset + "\"")
                            .withPresentableText("preset: \"" + preset + "\"")
                            .withTypeText("validation preset", true),
                    150.0));
        }

        // Generic preset option for custom presets
        result.addElement(PrioritizedLookupElement.withPriority(
                LookupElementBuilder.create("preset: \"\"")
                        .withPresentableText("preset:")
                        .withTailText(" \"presetName\"", true)
                        .withTypeText("named arg", true)
                        .withInsertHandler((ctx, item) -> {
                            // Position cursor inside the quotes
                            ctx.getEditor().getCaretModel().moveToOffset(ctx.getTailOffset() - 1);
                        }),
                100.0));
    }

    /**
     * Add completions for @provider and @provisionOn decorator arguments.
     */
    private void addProviderCompletions(@NotNull CompletionResultSet result) {
        // Cloud provider strings
        String[] providers = {"aws", "azure", "gcp", "cloudflare", "kubernetes", "docker"};
        for (String provider : providers) {
            result.addElement(PrioritizedLookupElement.withPriority(
                    LookupElementBuilder.create("\"" + provider + "\"")
                            .withTypeText("cloud provider", true),
                    200.0));
        }

        // Array syntax for multiple providers
        result.addElement(PrioritizedLookupElement.withPriority(
                LookupElementBuilder.create("[\"aws\", \"azure\"]")
                        .withTailText(" (multiple)", true)
                        .withTypeText("array", true)
                        .withInsertHandler((ctx, item) -> {
                            // Position cursor inside the first string
                            int startOffset = ctx.getStartOffset();
                            ctx.getEditor().getCaretModel().moveToOffset(startOffset + 2);
                        }),
                150.0));
    }

    /**
     * Add completions for @tags decorator arguments.
     */
    private void addTagsCompletions(@NotNull CompletionResultSet result) {
        // Object syntax for key-value tags
        result.addElement(PrioritizedLookupElement.withPriority(
                LookupElementBuilder.create("{}")
                        .withTailText(" object", true)
                        .withTypeText("tags object", true)
                        .withInsertHandler((ctx, item) -> {
                            ctx.getDocument().replaceString(ctx.getStartOffset(), ctx.getTailOffset(), "{}");
                            ctx.getEditor().getCaretModel().moveToOffset(ctx.getStartOffset() + 1);
                        }),
                200.0));

        // Common tag keys
        result.addElement(PrioritizedLookupElement.withPriority(
                LookupElementBuilder.create("{Environment: \"production\"}")
                        .withTypeText("example", true),
                150.0));
        result.addElement(PrioritizedLookupElement.withPriority(
                LookupElementBuilder.create("{Name: \"\"}")
                        .withTypeText("common tag", true)
                        .withInsertHandler((ctx, item) -> {
                            ctx.getEditor().getCaretModel().moveToOffset(ctx.getTailOffset() - 2);
                        }),
                150.0));
        result.addElement(PrioritizedLookupElement.withPriority(
                LookupElementBuilder.create("{Project: \"\"}")
                        .withTypeText("common tag", true)
                        .withInsertHandler((ctx, item) -> {
                            ctx.getEditor().getCaretModel().moveToOffset(ctx.getTailOffset() - 2);
                        }),
                150.0));
    }

    /**
     * Add completions for @allowed decorator arguments.
     */
    private void addAllowedCompletions(@NotNull CompletionResultSet result) {
        // Array syntax
        result.addElement(PrioritizedLookupElement.withPriority(
                LookupElementBuilder.create("[]")
                        .withTailText(" array of allowed values", true)
                        .withTypeText("array", true)
                        .withInsertHandler((ctx, item) -> {
                            ctx.getEditor().getCaretModel().moveToOffset(ctx.getTailOffset() - 1);
                        }),
                200.0));

        // String array example
        result.addElement(PrioritizedLookupElement.withPriority(
                LookupElementBuilder.create("[\"value1\", \"value2\"]")
                        .withTypeText("string array", true)
                        .withInsertHandler((ctx, item) -> {
                            // Position cursor inside first string
                            int startOffset = ctx.getStartOffset();
                            ctx.getEditor().getCaretModel().moveToOffset(startOffset + 2);
                        }),
                150.0));

        // Number array example
        result.addElement(PrioritizedLookupElement.withPriority(
                LookupElementBuilder.create("[1, 2, 3]")
                        .withTypeText("number array", true),
                150.0));
    }

    /**
     * Add completions for @existing decorator arguments.
     */
    private void addExistingCompletions(@NotNull CompletionResultSet result) {
        // Reference format hints
        result.addElement(PrioritizedLookupElement.withPriority(
                LookupElementBuilder.create("\"arn:aws:...\"")
                        .withTypeText("ARN reference", true)
                        .withInsertHandler((ctx, item) -> {
                            ctx.getDocument().replaceString(ctx.getStartOffset(), ctx.getTailOffset(), "\"arn:aws:\"");
                            ctx.getEditor().getCaretModel().moveToOffset(ctx.getTailOffset() - 1);
                        }),
                200.0));
        result.addElement(PrioritizedLookupElement.withPriority(
                LookupElementBuilder.create("\"https://...\"")
                        .withTypeText("URL reference", true)
                        .withInsertHandler((ctx, item) -> {
                            ctx.getDocument().replaceString(ctx.getStartOffset(), ctx.getTailOffset(), "\"https://\"");
                            ctx.getEditor().getCaretModel().moveToOffset(ctx.getTailOffset() - 1);
                        }),
                200.0));
        result.addElement(PrioritizedLookupElement.withPriority(
                LookupElementBuilder.create("\"id:...\"")
                        .withTypeText("ID reference", true)
                        .withInsertHandler((ctx, item) -> {
                            ctx.getDocument().replaceString(ctx.getStartOffset(), ctx.getTailOffset(), "\"\"");
                            ctx.getEditor().getCaretModel().moveToOffset(ctx.getTailOffset() - 1);
                        }),
                200.0));
        result.addElement(PrioritizedLookupElement.withPriority(
                LookupElementBuilder.create("{tags: {}}")
                        .withTypeText("tag reference", true)
                        .withInsertHandler((ctx, item) -> {
                            ctx.getEditor().getCaretModel().moveToOffset(ctx.getTailOffset() - 2);
                        }),
                150.0));
    }

    /**
     * Add completions for @dependsOn decorator arguments.
     */
    private void addDependsOnCompletions(@NotNull CompletionResultSet result) {
        // Array syntax for multiple dependencies
        result.addElement(PrioritizedLookupElement.withPriority(
                LookupElementBuilder.create("[]")
                        .withTailText(" array of resources", true)
                        .withTypeText("array", true)
                        .withInsertHandler((ctx, item) -> {
                            ctx.getEditor().getCaretModel().moveToOffset(ctx.getTailOffset() - 1);
                        }),
                200.0));
    }

    /**
     * Add completions for @description decorator arguments.
     */
    private void addDescriptionCompletions(@NotNull CompletionResultSet result) {
        // Just provide a quote helper
        result.addElement(PrioritizedLookupElement.withPriority(
                LookupElementBuilder.create("\"\"")
                        .withTailText(" description text", true)
                        .withTypeText("string", true)
                        .withInsertHandler((ctx, item) -> {
                            ctx.getEditor().getCaretModel().moveToOffset(ctx.getTailOffset() - 1);
                        }),
                200.0));
    }

    /**
     * Check if cursor is after a dot (property access context)
     */
    private boolean isPropertyAccessContext(PsiElement position) {
        PsiElement prev = skipWhitespaceBackward(position.getPrevSibling());
        if (prev == null) {
            // Check parent's previous sibling
            PsiElement parent = position.getParent();
            if (parent != null) {
                prev = skipWhitespaceBackward(parent.getPrevSibling());
            }
        }

        if (prev != null && prev.getNode() != null) {
            return prev.getNode().getElementType() == KiteTokenTypes.DOT;
        }
        return false;
    }

    /**
     * Check if cursor is in a type position (after var, input, output, etc.)
     */
    private boolean isTypeContext(PsiElement position) {
        PsiElement prev = skipWhitespaceBackward(position.getPrevSibling());
        if (prev == null) {
            PsiElement parent = position.getParent();
            if (parent != null) {
                prev = skipWhitespaceBackward(parent.getPrevSibling());
            }
        }

        if (prev != null && prev.getNode() != null) {
            IElementType prevType = prev.getNode().getElementType();
            return prevType == KiteTokenTypes.VAR ||
                   prevType == KiteTokenTypes.INPUT ||
                   prevType == KiteTokenTypes.OUTPUT ||
                   prevType == KiteTokenTypes.RESOURCE ||
                   prevType == KiteTokenTypes.COLON;  // For function return types
        }
        return false;
    }

    /**
     * Add keyword completions based on context
     */
    private void addKeywordCompletions(@NotNull CompletionResultSet result, PsiElement position) {
        // Top-level keywords
        for (String keyword : TOP_LEVEL_KEYWORDS) {
            result.addElement(createKeywordLookup(keyword));
        }

        // Control flow keywords
        for (String keyword : CONTROL_KEYWORDS) {
            result.addElement(createKeywordLookup(keyword));
        }

        // Literals
        for (String keyword : LITERAL_KEYWORDS) {
            result.addElement(createKeywordLookup(keyword));
        }
    }

    /**
     * Add built-in type completions
     */
    private void addTypeCompletions(@NotNull CompletionResultSet result) {
        // Basic types (higher priority)
        for (String type : BUILTIN_TYPES) {
            LookupElementBuilder element = LookupElementBuilder.create(type)
                    .withTypeText("type")
                    .withBoldness(true)
                    .withIcon(KiteStructureViewIcons.TYPE);
            result.addElement(PrioritizedLookupElement.withPriority(element, 100.0));
        }

        // Array types (slightly lower priority so they appear after basic types)
        for (String type : BUILTIN_ARRAY_TYPES) {
            LookupElementBuilder element = LookupElementBuilder.create(type)
                    .withTypeText("array type")
                    .withBoldness(true)
                    .withIcon(KiteStructureViewIcons.TYPE);
            result.addElement(PrioritizedLookupElement.withPriority(element, 90.0));
        }
    }

    /**
     * Add completions for declared types (schemas, type aliases)
     */
    private void addDeclaredTypeCompletions(PsiFile file, @NotNull CompletionResultSet result) {
        collectDeclarations(file, (name, declarationType, element) -> {
            if (declarationType == KiteElementTypes.SCHEMA_DECLARATION ||
                declarationType == KiteElementTypes.TYPE_DECLARATION ||
                declarationType == KiteElementTypes.COMPONENT_DECLARATION) {
                result.addElement(
                    LookupElementBuilder.create(name)
                        .withTypeText(getTypeTextForDeclaration(declarationType))
                        .withIcon(getIconForDeclaration(declarationType))
                );
            }
        });
    }

    /**
     * Add completions for declared identifiers (variables, resources, etc.)
     * Includes declarations from the current file and imported files.
     * Excludes schemas and type declarations when in value position (after =).
     */
    private void addIdentifierCompletions(PsiFile file, @NotNull CompletionResultSet result, PsiElement position) {
        Set<String> addedNames = new HashSet<>();

        // Check if we're after '=' (value position) - if so, exclude types/schemas
        boolean isValuePosition = isAfterAssignment(position);

        // Collect from current file (higher priority)
        collectDeclarations(file, (name, declarationType, element) -> {
            // Skip schemas and type declarations in value positions
            if (isValuePosition && isTypeDeclaration(declarationType)) {
                return;
            }
            if (!addedNames.contains(name)) {
                addedNames.add(name);
                LookupElementBuilder lookup = LookupElementBuilder.create(name)
                        .withTypeText(getTypeTextForDeclaration(declarationType))
                        .withIcon(getIconForDeclaration(declarationType));
                result.addElement(PrioritizedLookupElement.withPriority(lookup, 100.0));
            }
        });

        // Also collect for-loop variables
        collectForLoopVariables(file, (name, element) -> {
            if (!addedNames.contains(name)) {
                addedNames.add(name);
                LookupElementBuilder lookup = LookupElementBuilder.create(name)
                        .withTypeText("loop variable")
                        .withIcon(KiteStructureViewIcons.VARIABLE);
                result.addElement(PrioritizedLookupElement.withPriority(lookup, 100.0));
            }
        });

        // Collect only specifically imported symbols from imported files (higher priority)
        // For named imports like "import foo, bar from ...", only add foo and bar
        // For wildcard imports "import * from ...", add all symbols
        List<PsiFile> importedFiles = KiteImportHelper.getImportedFiles(file);
        for (PsiFile importedFile : importedFiles) {
            if (importedFile == null) continue;

            collectDeclarations(importedFile, (name, declarationType, element) -> {
                // Skip schemas and type declarations in value positions
                if (isValuePosition && isTypeDeclaration(declarationType)) {
                    return;
                }
                // Only add if this specific symbol is imported
                if (KiteImportHelper.isSymbolImported(name, file) && !addedNames.contains(name)) {
                    addedNames.add(name);
                    LookupElementBuilder lookup = LookupElementBuilder.create(name)
                            .withTypeText(getTypeTextForDeclaration(declarationType))
                            .withTailText(" (" + importedFile.getName() + ")", true)
                            .withIcon(getIconForDeclaration(declarationType));
                    result.addElement(PrioritizedLookupElement.withPriority(lookup, 50.0));
                }
            });
        }

        // Auto-import: Collect from all project files for symbols NOT yet imported
        // These will have an InsertHandler that adds the import statement
        VirtualFile currentVFile = file.getVirtualFile();
        String currentPath = currentVFile != null ? currentVFile.getPath() : null;
        Project project = file.getProject();
        List<PsiFile> allKiteFiles = KiteImportHelper.getAllKiteFilesInProject(project);

        for (PsiFile projectFile : allKiteFiles) {
            if (projectFile == null) continue;
            VirtualFile vf = projectFile.getVirtualFile();
            if (vf == null) continue;

            String filePath = vf.getPath();
            // Skip current file only
            if (filePath.equals(currentPath)) {
                continue;
            }

            final PsiFile targetFile = projectFile;
            collectDeclarations(projectFile, (name, declarationType, element) -> {
                // Skip schemas and type declarations in value positions
                if (isValuePosition && isTypeDeclaration(declarationType)) {
                    return;
                }
                // Only offer as auto-import if NOT already imported and not already added
                if (!KiteImportHelper.isSymbolImported(name, file) && !addedNames.contains(name)) {
                    addedNames.add(name);
                    LookupElementBuilder lookup = LookupElementBuilder.create(name)
                            .withTypeText(getTypeTextForDeclaration(declarationType))
                            .withTailText(" (import from " + targetFile.getName() + ")", true)
                            .withIcon(getIconForDeclaration(declarationType))
                            .withInsertHandler(createAutoImportHandler(file, targetFile, name));
                    result.addElement(PrioritizedLookupElement.withPriority(lookup, 10.0));
                }
            });
        }
    }

    /**
     * Creates an insert handler that adds an import statement for the symbol.
     */
    private InsertHandler<LookupElement> createAutoImportHandler(PsiFile currentFile, PsiFile importFromFile, String symbolName) {
        return (context, item) -> {
            // Get the relative import path
            String importPath = KiteImportHelper.getRelativeImportPath(currentFile, importFromFile);
            if (importPath == null) return;

            Project project = context.getProject();
            Document document = context.getDocument();

            WriteCommandAction.runWriteCommandAction(project, () -> {
                // Find where to insert the import statement (after existing imports or at the top)
                String fileText = document.getText();
                int insertOffset = findImportInsertOffset(fileText);

                // Build the import statement
                String importStatement = "import " + symbolName + " from \"" + importPath + "\"\n";

                // Check if this import already exists
                if (!fileText.contains("import " + symbolName + " from \"" + importPath + "\"") &&
                    !fileText.contains("import " + symbolName + " from '" + importPath + "'")) {
                    document.insertString(insertOffset, importStatement);
                    PsiDocumentManager.getInstance(project).commitDocument(document);
                }
            });
        };
    }

    /**
     * Find the offset where new import statements should be inserted.
     * After existing imports, or at the start of the file.
     */
    private int findImportInsertOffset(String text) {
        // Find the last import statement
        int lastImportEnd = 0;
        int idx = 0;
        while (idx < text.length()) {
            // Skip whitespace and comments at the beginning of lines
            int lineStart = idx;
            while (idx < text.length() && Character.isWhitespace(text.charAt(idx)) && text.charAt(idx) != '\n') {
                idx++;
            }

            // Check for comment lines
            if (idx + 1 < text.length() && text.charAt(idx) == '/' && text.charAt(idx + 1) == '/') {
                // Skip to end of line
                while (idx < text.length() && text.charAt(idx) != '\n') {
                    idx++;
                }
                if (idx < text.length()) idx++; // Skip newline
                continue;
            }

            // Check for import statement
            if (text.startsWith("import", idx)) {
                // Find end of this line
                while (idx < text.length() && text.charAt(idx) != '\n') {
                    idx++;
                }
                if (idx < text.length()) idx++; // Skip newline
                lastImportEnd = idx;
                continue;
            }

            // Not an import, break
            break;
        }

        return lastImportEnd;
    }

    /**
     * Check if the cursor is after an assignment operator (in value position).
     */
    private boolean isAfterAssignment(PsiElement position) {
        // Walk backward to see if there's an '=' on the same line before us
        PsiElement current = position.getPrevSibling();
        while (current != null) {
            if (current.getNode() != null) {
                IElementType type = current.getNode().getElementType();

                // If we hit a newline, we're at the start of a new line - not in value position
                if (type == KiteTokenTypes.NL || type == KiteTokenTypes.NEWLINE) {
                    return false;
                }

                // If we hit an '=', we're in value position
                if (type == KiteTokenTypes.ASSIGN) {
                    return true;
                }
            }
            current = current.getPrevSibling();
        }

        // Also check parent's siblings
        PsiElement parent = position.getParent();
        if (parent != null) {
            current = parent.getPrevSibling();
            while (current != null) {
                if (current.getNode() != null) {
                    IElementType type = current.getNode().getElementType();

                    if (type == KiteTokenTypes.NL || type == KiteTokenTypes.NEWLINE) {
                        return false;
                    }

                    if (type == KiteTokenTypes.ASSIGN) {
                        return true;
                    }
                }
                current = current.getPrevSibling();
            }
        }

        return false;
    }

    /**
     * Check if a declaration type is a type/schema/component definition (not a value).
     * These should be excluded from value position completions.
     */
    private boolean isTypeDeclaration(IElementType type) {
        return type == KiteElementTypes.SCHEMA_DECLARATION ||
               type == KiteElementTypes.TYPE_DECLARATION ||
               type == KiteElementTypes.COMPONENT_DECLARATION;
    }

    /**
     * Add property completions for object.property access (supports chained access like server.tag.)
     * For resources, shows all schema properties with initialized ones bold and first.
     */
    private void addPropertyCompletions(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
        PsiElement position = parameters.getPosition();
        PsiFile file = parameters.getOriginalFile();

        // Build the property chain by walking backwards (e.g., "server.tag" -> ["server", "tag"])
        List<String> chain = buildPropertyChain(position);
        if (chain.isEmpty()) return;

        // Start with the first element (should be a declaration)
        String rootName = chain.get(0);
        PsiElement declaration = findDeclaration(file, rootName);

        if (declaration == null) return;

        // Check if this is a resource declaration - if so, show all schema properties
        if (declaration.getNode().getElementType() == KiteElementTypes.RESOURCE_DECLARATION && chain.size() == 1) {
            addResourcePropertyCompletions(file, result, declaration);
            return;
        }

        // Check if this is a component instantiation (e.g., "component WebServer serviceA { ... }")
        // If so, we need to get outputs from the component TYPE declaration, not the instance
        PsiElement currentContext = declaration;
        if (declaration.getNode().getElementType() == KiteElementTypes.COMPONENT_DECLARATION) {
            if (isComponentInstantiation(declaration)) {
                // Get the component type name (e.g., "WebServer" from "component WebServer serviceA")
                String componentTypeName = getComponentTypeName(declaration);
                if (componentTypeName != null) {
                    // Find the component TYPE declaration (e.g., "component WebServer { ... }")
                    PsiElement componentDeclaration = findComponentDeclaration(file, componentTypeName);
                    if (componentDeclaration != null) {
                        currentContext = componentDeclaration;
                    }
                }
            }
        }

        // Navigate through the chain for nested properties
        for (int i = 1; i < chain.size(); i++) {
            String propertyName = chain.get(i);
            currentContext = findPropertyValue(currentContext, propertyName);
            if (currentContext == null) return;
        }

        // Collect properties from the final context
        collectPropertiesFromContext(currentContext, (propertyName, propertyElement) -> {
            result.addElement(
                LookupElementBuilder.create(propertyName)
                    .withTypeText("property")
                    .withIcon(KiteStructureViewIcons.PROPERTY)
            );
        });
    }

    /**
     * Add property completions for resource property access (photos.host).
     * Shows all schema properties with:
     * - Initialized properties shown bold and first in the list
     * - Uninitialized properties shown normally
     * - Cloud properties are included (they can be read, just not set)
     */
    private void addResourcePropertyCompletions(PsiFile file, @NotNull CompletionResultSet result, PsiElement resourceDecl) {
        // Get the schema/type name for this resource
        String schemaName = extractResourceTypeName(resourceDecl);
        if (schemaName == null) return;

        // Get all schema properties
        Map<String, SchemaPropertyInfo> schemaProperties = findSchemaProperties(file, schemaName);

        // Get properties that are initialized in the resource
        Set<String> initializedProperties = collectExistingPropertyNames(resourceDecl);

        // Add initialized properties first (bold, higher priority)
        for (String propertyName : initializedProperties) {
            SchemaPropertyInfo propInfo = schemaProperties.get(propertyName);
            String typeText = propInfo != null ? propInfo.type : "property";

            LookupElementBuilder element = LookupElementBuilder.create(propertyName)
                    .withTypeText(typeText)
                    .withIcon(KiteStructureViewIcons.PROPERTY)
                    .withBoldness(true);

            // Higher priority for initialized properties
            result.addElement(PrioritizedLookupElement.withPriority(element, 200.0));
        }

        // Add remaining schema properties (not bold, lower priority)
        for (Map.Entry<String, SchemaPropertyInfo> entry : schemaProperties.entrySet()) {
            String propertyName = entry.getKey();

            // Skip if already added as initialized
            if (initializedProperties.contains(propertyName)) {
                continue;
            }

            SchemaPropertyInfo propInfo = entry.getValue();
            String typeText = propInfo.type;

            // Add cloud indicator to type text
            if (propInfo.isCloud) {
                typeText = typeText + " (cloud)";
            }

            LookupElementBuilder element = LookupElementBuilder.create(propertyName)
                    .withTypeText(typeText)
                    .withIcon(KiteStructureViewIcons.PROPERTY)
                    .withBoldness(false);

            // Lower priority for non-initialized properties
            result.addElement(PrioritizedLookupElement.withPriority(element, 100.0));
        }

        // Also add any custom properties defined in the resource but not in schema
        // (in case the schema doesn't capture all properties)
        for (String propertyName : initializedProperties) {
            if (!schemaProperties.containsKey(propertyName)) {
                LookupElementBuilder element = LookupElementBuilder.create(propertyName)
                        .withTypeText("property")
                        .withIcon(KiteStructureViewIcons.PROPERTY)
                        .withBoldness(true);

                result.addElement(PrioritizedLookupElement.withPriority(element, 200.0));
            }
        }
    }

    /**
     * Check if a component declaration is an instantiation (has both type and instance name)
     * e.g., "component WebServer serviceA { ... }" is an instantiation
     * e.g., "component WebServer { ... }" is a declaration (defines the component)
     */
    private boolean isComponentInstantiation(PsiElement componentDecl) {
        // Count identifiers before the opening brace
        int identifierCount = 0;
        PsiElement child = componentDecl.getFirstChild();
        while (child != null) {
            IElementType type = child.getNode().getElementType();
            if (type == KiteTokenTypes.IDENTIFIER) {
                identifierCount++;
            } else if (type == KiteTokenTypes.LBRACE) {
                break;
            }
            child = child.getNextSibling();
        }
        // Instantiation has 2 identifiers: TypeName and instanceName
        // Declaration has 1 identifier: TypeName
        return identifierCount >= 2;
    }

    /**
     * Get the component type name from a component instantiation
     * e.g., "component WebServer serviceA { ... }" returns "WebServer"
     */
    private String getComponentTypeName(PsiElement componentDecl) {
        // The first identifier after "component" keyword is the type name
        boolean foundComponent = false;
        PsiElement child = componentDecl.getFirstChild();
        while (child != null) {
            IElementType type = child.getNode().getElementType();
            if (type == KiteTokenTypes.COMPONENT) {
                foundComponent = true;
            } else if (foundComponent && type == KiteTokenTypes.IDENTIFIER) {
                return child.getText();
            }
            child = child.getNextSibling();
        }
        return null;
    }

    /**
     * Find a component declaration (not instantiation) by type name
     * e.g., find "component WebServer { ... }" (the declaration that defines WebServer)
     */
    private PsiElement findComponentDeclaration(PsiFile file, String typeName) {
        final PsiElement[] result = {null};

        collectDeclarations(file, (declName, declarationType, element) -> {
            if (declarationType == KiteElementTypes.COMPONENT_DECLARATION) {
                // Check if this is a declaration (not instantiation) with matching type name
                if (!isComponentInstantiation(element)) {
                    String componentTypeName = getComponentTypeName(element);
                    if (typeName.equals(componentTypeName)) {
                        result[0] = element;
                    }
                }
            }
        });

        return result[0];
    }

    /**
     * Build a chain of property names from the cursor position backwards
     * e.g., "server.tag." returns ["server", "tag"]
     */
    private List<String> buildPropertyChain(PsiElement position) {
        List<String> chain = new ArrayList<>();

        PsiElement current = position;

        // Walk backwards collecting identifiers and dots
        while (true) {
            // Skip to find the dot before current position
            PsiElement dot = findPreviousDot(current);
            if (dot == null) break;

            // Find the identifier before the dot
            PsiElement identifier = skipWhitespaceBackward(dot.getPrevSibling());
            if (identifier == null || identifier.getNode() == null) break;

            IElementType type = identifier.getNode().getElementType();
            if (type == KiteTokenTypes.IDENTIFIER) {
                chain.add(0, identifier.getText()); // Add to front
                current = identifier;
            } else {
                break;
            }
        }

        return chain;
    }

    /**
     * Find the previous dot token relative to the given element
     */
    private PsiElement findPreviousDot(PsiElement element) {
        PsiElement prev = skipWhitespaceBackward(element.getPrevSibling());
        if (prev == null) {
            PsiElement parent = element.getParent();
            if (parent != null) {
                prev = skipWhitespaceBackward(parent.getPrevSibling());
            }
        }
        if (prev != null && prev.getNode() != null &&
            prev.getNode().getElementType() == KiteTokenTypes.DOT) {
            return prev;
        }
        return null;
    }

    /**
     * Find the value element of a property within a context
     * e.g., for "tag = { ... }", returns the object literal element
     */
    private PsiElement findPropertyValue(PsiElement context, String propertyName) {
        final PsiElement[] result = {null};

        // Search for the property in the context
        visitPropertiesInContext(context, (name, valueElement) -> {
            if (name.equals(propertyName) && result[0] == null) {
                result[0] = valueElement;
            }
        });

        return result[0];
    }

    /**
     * Visit properties in a context and get their value elements
     */
    private void visitPropertiesInContext(PsiElement context, PropertyValueVisitor visitor) {
        int braceDepth = 0;
        PsiElement child = context.getFirstChild();

        while (child != null) {
            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.LBRACE) {
                braceDepth++;
            } else if (childType == KiteTokenTypes.RBRACE) {
                braceDepth--;
            } else if (braceDepth == 1 && childType == KiteTokenTypes.IDENTIFIER) {
                // Found a property at depth 1
                PsiElement next = skipWhitespaceForward(child.getNextSibling());
                if (next != null) {
                    IElementType nextType = next.getNode().getElementType();
                    if (nextType == KiteTokenTypes.ASSIGN || nextType == KiteTokenTypes.COLON) {
                        // Find the value after = or :
                        PsiElement value = skipWhitespaceForward(next.getNextSibling());
                        if (value != null) {
                            visitor.visit(child.getText(), value);
                        }
                    }
                }
            }

            // Recurse into nested PSI elements but track brace depth
            if (child.getFirstChild() != null && !isDeclarationType(childType)) {
                visitPropertiesInContextRecursive(child, visitor, braceDepth);
            }

            child = child.getNextSibling();
        }
    }

    private void visitPropertiesInContextRecursive(PsiElement element, PropertyValueVisitor visitor, int currentDepth) {
        PsiElement child = element.getFirstChild();

        while (child != null) {
            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.LBRACE) {
                currentDepth++;
            } else if (childType == KiteTokenTypes.RBRACE) {
                currentDepth--;
            } else if (currentDepth == 1 && childType == KiteTokenTypes.IDENTIFIER) {
                PsiElement next = skipWhitespaceForward(child.getNextSibling());
                if (next != null) {
                    IElementType nextType = next.getNode().getElementType();
                    if (nextType == KiteTokenTypes.ASSIGN || nextType == KiteTokenTypes.COLON) {
                        PsiElement value = skipWhitespaceForward(next.getNextSibling());
                        if (value != null) {
                            visitor.visit(child.getText(), value);
                        }
                    }
                }
            }

            if (child.getFirstChild() != null && !isDeclarationType(childType)) {
                visitPropertiesInContextRecursive(child, visitor, currentDepth);
            }

            child = child.getNextSibling();
        }
    }

    /**
     * Collect properties from a context element (could be a declaration or object literal)
     */
    private void collectPropertiesFromContext(PsiElement context, PropertyVisitor visitor) {
        // If context is an object literal (starts with {), collect its properties
        if (context.getNode().getElementType() == KiteElementTypes.OBJECT_LITERAL ||
            context.getNode().getElementType() == KiteTokenTypes.LBRACE ||
            (context.getText() != null && context.getText().trim().startsWith("{"))) {
            collectPropertiesFromObjectLiteral(context, visitor);
        } else {
            // It's a declaration, use existing method
            collectPropertiesFromDeclaration(context, visitor);
        }
    }

    /**
     * Collect properties from an object literal
     */
    private void collectPropertiesFromObjectLiteral(PsiElement objectLiteral, PropertyVisitor visitor) {
        int braceDepth = 0;
        PsiElement child = objectLiteral.getFirstChild();

        while (child != null) {
            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.LBRACE) {
                braceDepth++;
            } else if (childType == KiteTokenTypes.RBRACE) {
                braceDepth--;
            } else if (braceDepth == 1 && childType == KiteTokenTypes.IDENTIFIER) {
                PsiElement next = skipWhitespaceForward(child.getNextSibling());
                if (next != null) {
                    IElementType nextType = next.getNode().getElementType();
                    if (nextType == KiteTokenTypes.ASSIGN || nextType == KiteTokenTypes.COLON) {
                        visitor.visit(child.getText(), child);
                    }
                }
            }

            if (child.getFirstChild() != null) {
                collectPropertiesFromObjectLiteralRecursive(child, visitor, braceDepth);
            }

            child = child.getNextSibling();
        }
    }

    private void collectPropertiesFromObjectLiteralRecursive(PsiElement element, PropertyVisitor visitor, int currentDepth) {
        PsiElement child = element.getFirstChild();

        while (child != null) {
            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.LBRACE) {
                currentDepth++;
            } else if (childType == KiteTokenTypes.RBRACE) {
                currentDepth--;
            } else if (currentDepth == 1 && childType == KiteTokenTypes.IDENTIFIER) {
                PsiElement next = skipWhitespaceForward(child.getNextSibling());
                if (next != null) {
                    IElementType nextType = next.getNode().getElementType();
                    if (nextType == KiteTokenTypes.ASSIGN || nextType == KiteTokenTypes.COLON) {
                        visitor.visit(child.getText(), child);
                    }
                }
            }

            if (child.getFirstChild() != null) {
                collectPropertiesFromObjectLiteralRecursive(child, visitor, currentDepth);
            }

            child = child.getNextSibling();
        }
    }

    @FunctionalInterface
    private interface PropertyValueVisitor {
        void visit(String name, PsiElement valueElement);
    }

    /**
     * Create a lookup element for a keyword
     */
    private LookupElement createKeywordLookup(String keyword) {
        return LookupElementBuilder.create(keyword)
            .withBoldness(true)
            .withTypeText("keyword");
    }

    /**
     * Collect all declarations from the file
     */
    private void collectDeclarations(PsiFile file, DeclarationVisitor visitor) {
        collectDeclarationsRecursive(file, visitor);
    }

    private void collectDeclarationsRecursive(PsiElement element, DeclarationVisitor visitor) {
        if (element == null) return;

        IElementType elementType = element.getNode().getElementType();

        if (isDeclarationType(elementType)) {
            String name = findNameInDeclaration(element, elementType);
            if (name != null && !name.isEmpty()) {
                visitor.visit(name, elementType, element);
            }
        }

        // Recurse into children
        PsiElement child = element.getFirstChild();
        while (child != null) {
            collectDeclarationsRecursive(child, visitor);
            child = child.getNextSibling();
        }
    }

    /**
     * Collect for-loop variables
     */
    private void collectForLoopVariables(PsiFile file, ForLoopVariableVisitor visitor) {
        collectForLoopVariablesRecursive(file, visitor);
    }

    private void collectForLoopVariablesRecursive(PsiElement element, ForLoopVariableVisitor visitor) {
        if (element == null) return;

        IElementType elementType = element.getNode().getElementType();

        if (elementType == KiteElementTypes.FOR_STATEMENT) {
            // Find identifier after "for" keyword
            boolean foundFor = false;
            PsiElement child = element.getFirstChild();
            while (child != null) {
                IElementType childType = child.getNode().getElementType();
                if (childType == KiteTokenTypes.FOR) {
                    foundFor = true;
                } else if (foundFor && childType == KiteTokenTypes.IDENTIFIER) {
                    visitor.visit(child.getText(), child);
                    break;
                } else if (childType == KiteTokenTypes.IN) {
                    break;  // Stop if we hit "in" keyword
                }
                child = child.getNextSibling();
            }
        }

        // Recurse into children
        PsiElement child = element.getFirstChild();
        while (child != null) {
            collectForLoopVariablesRecursive(child, visitor);
            child = child.getNextSibling();
        }
    }

    /**
     * Find a declaration by name
     */
    private PsiElement findDeclaration(PsiFile file, String name) {
        final PsiElement[] result = {null};

        collectDeclarations(file, (declName, declarationType, element) -> {
            if (name.equals(declName) && result[0] == null) {
                result[0] = element;
            }
        });

        return result[0];
    }

    /**
     * Collect only direct properties from inside a declaration's braces (not nested)
     * For server., should show "size" and "tag" but NOT "Environment", "Name", etc.
     * For components, only collects OUTPUT declarations (inputs are not accessible from outside).
     */
    private void collectPropertiesFromDeclaration(PsiElement declaration, PropertyVisitor visitor) {
        // Check if this is a component declaration
        boolean isComponent = declaration.getNode().getElementType() == KiteElementTypes.COMPONENT_DECLARATION;

        int braceDepth = 0;
        PsiElement child = declaration.getFirstChild();

        while (child != null) {
            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.LBRACE) {
                braceDepth++;
            } else if (childType == KiteTokenTypes.RBRACE) {
                braceDepth--;
            } else if (braceDepth == 1) {
                // For components: only OUTPUT_DECLARATION is accessible from outside
                // (inputs are parameters passed IN, outputs are values exposed OUT)
                if (isComponent && childType == KiteElementTypes.OUTPUT_DECLARATION) {
                    String name = findNameInDeclaration(child, childType);
                    if (name != null && !name.isEmpty()) {
                        visitor.visit(name, child);
                    }
                }
                // For non-components: collect INPUT_DECLARATION and OUTPUT_DECLARATION
                else if (!isComponent && (childType == KiteElementTypes.INPUT_DECLARATION ||
                    childType == KiteElementTypes.OUTPUT_DECLARATION)) {
                    String name = findNameInDeclaration(child, childType);
                    if (name != null && !name.isEmpty()) {
                        visitor.visit(name, child);
                    }
                }
                // Check for identifier followed by = or : (for resources)
                else if (childType == KiteTokenTypes.IDENTIFIER) {
                    PsiElement next = skipWhitespaceForward(child.getNextSibling());
                    if (next != null) {
                        IElementType nextType = next.getNode().getElementType();
                        if (nextType == KiteTokenTypes.ASSIGN || nextType == KiteTokenTypes.COLON) {
                            visitor.visit(child.getText(), child);
                        }
                    }
                }
            }

            // Recurse into nested PSI elements but track brace depth
            if (child.getFirstChild() != null && !isDeclarationType(childType)) {
                collectPropertiesAtDepth(child, visitor, braceDepth);
            }

            child = child.getNextSibling();
        }
    }

    private void collectPropertiesAtDepth(PsiElement element, PropertyVisitor visitor, int currentDepth) {
        PsiElement child = element.getFirstChild();

        while (child != null) {
            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.LBRACE) {
                currentDepth++;
            } else if (childType == KiteTokenTypes.RBRACE) {
                currentDepth--;
            } else if (currentDepth == 1 && childType == KiteTokenTypes.IDENTIFIER) {
                // Only collect at depth 1 (direct children)
                PsiElement next = skipWhitespaceForward(child.getNextSibling());
                if (next != null) {
                    IElementType nextType = next.getNode().getElementType();
                    if (nextType == KiteTokenTypes.ASSIGN || nextType == KiteTokenTypes.COLON) {
                        visitor.visit(child.getText(), child);
                    }
                }
            }

            if (child.getFirstChild() != null && !isDeclarationType(childType)) {
                collectPropertiesAtDepth(child, visitor, currentDepth);
            }

            child = child.getNextSibling();
        }
    }

    /**
     * Find the dot token before the current position
     */
    private PsiElement findDotBeforePosition(PsiElement position) {
        PsiElement prev = skipWhitespaceBackward(position.getPrevSibling());
        if (prev != null && prev.getNode() != null &&
            prev.getNode().getElementType() == KiteTokenTypes.DOT) {
            return prev;
        }

        // Check parent's context
        PsiElement parent = position.getParent();
        if (parent != null) {
            prev = skipWhitespaceBackward(parent.getPrevSibling());
            if (prev != null && prev.getNode() != null &&
                prev.getNode().getElementType() == KiteTokenTypes.DOT) {
                return prev;
            }
        }

        return null;
    }

    /**
     * Check if an element type is a declaration
     */
    private boolean isDeclarationType(IElementType type) {
        return type == KiteElementTypes.VARIABLE_DECLARATION ||
               type == KiteElementTypes.INPUT_DECLARATION ||
               type == KiteElementTypes.OUTPUT_DECLARATION ||
               type == KiteElementTypes.RESOURCE_DECLARATION ||
               type == KiteElementTypes.COMPONENT_DECLARATION ||
               type == KiteElementTypes.SCHEMA_DECLARATION ||
               type == KiteElementTypes.FUNCTION_DECLARATION ||
               type == KiteElementTypes.TYPE_DECLARATION;
    }

    /**
     * Find the name in a declaration
     */
    private String findNameInDeclaration(PsiElement declaration, IElementType declarationType) {
        // For-loop special case: "for identifier in ..."
        if (declarationType == KiteElementTypes.FOR_STATEMENT) {
            boolean foundFor = false;
            PsiElement child = declaration.getFirstChild();
            while (child != null) {
                IElementType childType = child.getNode().getElementType();
                if (childType == KiteTokenTypes.FOR) {
                    foundFor = true;
                } else if (foundFor && childType == KiteTokenTypes.IDENTIFIER) {
                    return child.getText();
                }
                child = child.getNextSibling();
            }
            return null;
        }

        // Function special case: "fun functionName(...) returnType {"
        // The function name is the first identifier after 'fun'
        if (declarationType == KiteElementTypes.FUNCTION_DECLARATION) {
            boolean foundFun = false;
            PsiElement child = declaration.getFirstChild();
            while (child != null) {
                IElementType childType = child.getNode().getElementType();
                if (childType == KiteTokenTypes.FUN) {
                    foundFun = true;
                } else if (foundFun && childType == KiteTokenTypes.IDENTIFIER) {
                    return child.getText();
                } else if (childType == KiteTokenTypes.LPAREN) {
                    break; // Stop at opening paren if no identifier found
                }
                child = child.getNextSibling();
            }
            return null;
        }

        // For other declarations: find the last identifier before '=' or '{'
        String lastIdentifier = null;
        PsiElement child = declaration.getFirstChild();
        while (child != null) {
            IElementType childType = child.getNode().getElementType();
            if (childType == KiteTokenTypes.IDENTIFIER) {
                lastIdentifier = child.getText();
            } else if (childType == KiteTokenTypes.ASSIGN ||
                       childType == KiteTokenTypes.LBRACE ||
                       childType == KiteTokenTypes.PLUS_ASSIGN) {
                if (lastIdentifier != null) {
                    return lastIdentifier;
                }
            }
            child = child.getNextSibling();
        }
        return lastIdentifier;
    }

    /**
     * Get display text for a declaration type
     */
    private String getTypeTextForDeclaration(IElementType type) {
        if (type == KiteElementTypes.VARIABLE_DECLARATION) return "variable";
        if (type == KiteElementTypes.INPUT_DECLARATION) return "input";
        if (type == KiteElementTypes.OUTPUT_DECLARATION) return "output";
        if (type == KiteElementTypes.RESOURCE_DECLARATION) return "resource";
        if (type == KiteElementTypes.COMPONENT_DECLARATION) return "component";
        if (type == KiteElementTypes.SCHEMA_DECLARATION) return "schema";
        if (type == KiteElementTypes.FUNCTION_DECLARATION) return "function";
        if (type == KiteElementTypes.TYPE_DECLARATION) return "type";
        return "identifier";
    }

    /**
     * Get icon for a declaration type
     */
    private Icon getIconForDeclaration(IElementType type) {
        if (type == KiteElementTypes.VARIABLE_DECLARATION) return KiteStructureViewIcons.VARIABLE;
        if (type == KiteElementTypes.INPUT_DECLARATION) return KiteStructureViewIcons.INPUT;
        if (type == KiteElementTypes.OUTPUT_DECLARATION) return KiteStructureViewIcons.OUTPUT;
        if (type == KiteElementTypes.RESOURCE_DECLARATION) return KiteStructureViewIcons.RESOURCE;
        if (type == KiteElementTypes.COMPONENT_DECLARATION) return KiteStructureViewIcons.COMPONENT;
        if (type == KiteElementTypes.SCHEMA_DECLARATION) return KiteStructureViewIcons.SCHEMA;
        if (type == KiteElementTypes.FUNCTION_DECLARATION) return KiteStructureViewIcons.FUNCTION;
        if (type == KiteElementTypes.TYPE_DECLARATION) return KiteStructureViewIcons.TYPE;
        return null;
    }

    // Whitespace helpers
    private PsiElement skipWhitespaceBackward(PsiElement element) {
        while (element != null && isWhitespace(element)) {
            element = element.getPrevSibling();
        }
        return element;
    }

    private PsiElement skipWhitespaceForward(PsiElement element) {
        while (element != null && isWhitespace(element)) {
            element = element.getNextSibling();
        }
        return element;
    }

    private boolean isWhitespace(PsiElement element) {
        if (element == null || element.getNode() == null) return false;
        IElementType type = element.getNode().getElementType();
        return type == com.intellij.psi.TokenType.WHITE_SPACE ||
               type == KiteTokenTypes.NL ||
               type == KiteTokenTypes.WHITESPACE ||
               type == KiteTokenTypes.NEWLINE;
    }

    // Visitor interfaces
    @FunctionalInterface
    private interface DeclarationVisitor {
        void visit(String name, IElementType declarationType, PsiElement element);
    }

    @FunctionalInterface
    private interface ForLoopVariableVisitor {
        void visit(String name, PsiElement element);
    }

    @FunctionalInterface
    private interface PropertyVisitor {
        void visit(String name, PsiElement element);
    }

    // ========== Schema Property Completion ==========

    /**
     * Context information about an enclosing resource block.
     */
    private static class ResourceContext {
        final String schemaName;
        final PsiElement resourceDeclaration;

        ResourceContext(String schemaName, PsiElement resourceDeclaration) {
            this.schemaName = schemaName;
            this.resourceDeclaration = resourceDeclaration;
        }
    }

    /**
     * Get the resource context if we're inside a resource block.
     * Returns null if not inside a resource block.
     */
    @Nullable
    private ResourceContext getEnclosingResourceContext(PsiElement position) {
        // Walk up the PSI tree to find an enclosing RESOURCE_DECLARATION
        PsiElement current = position;
        while (current != null) {
            if (current.getNode() != null &&
                current.getNode().getElementType() == KiteElementTypes.RESOURCE_DECLARATION) {
                // Found resource declaration - check if we're inside the braces
                if (isInsideBraces(position, current)) {
                    String schemaName = extractResourceTypeName(current);
                    if (schemaName != null) {
                        return new ResourceContext(schemaName, current);
                    }
                }
            }
            current = current.getParent();
        }
        return null;
    }

    /**
     * Check if position is inside the braces of a declaration.
     */
    private boolean isInsideBraces(PsiElement position, PsiElement declaration) {
        int posOffset = position.getTextOffset();

        // Find LBRACE and RBRACE positions
        int lbraceOffset = -1;
        int rbraceOffset = -1;

        PsiElement child = declaration.getFirstChild();
        while (child != null) {
            if (child.getNode() != null) {
                IElementType type = child.getNode().getElementType();
                if (type == KiteTokenTypes.LBRACE && lbraceOffset == -1) {
                    lbraceOffset = child.getTextOffset();
                } else if (type == KiteTokenTypes.RBRACE) {
                    rbraceOffset = child.getTextOffset();
                }
            }
            child = child.getNextSibling();
        }

        return lbraceOffset != -1 && rbraceOffset != -1 &&
               posOffset > lbraceOffset && posOffset < rbraceOffset;
    }

    /**
     * Check if the cursor is before the assignment operator (on the left side of =).
     * Returns true if we're typing a property name, false if we're typing a value.
     * <p>
     * We check by walking backward on the same line - if we find '=' before newline, we're on right side.
     */
    private boolean isBeforeAssignment(PsiElement position) {
        // Walk backward to see if there's an '=' on the same line before us
        PsiElement current = position.getPrevSibling();
        while (current != null) {
            if (current.getNode() != null) {
                IElementType type = current.getNode().getElementType();

                // If we hit a newline, we're at the start of a new property - on left side
                if (type == KiteTokenTypes.NL || type == KiteTokenTypes.NEWLINE) {
                    return true;
                }

                // If we hit an '=', we're on the right side (value position)
                if (type == KiteTokenTypes.ASSIGN) {
                    return false;
                }
            }
            current = current.getPrevSibling();
        }

        // Also check parent's siblings
        PsiElement parent = position.getParent();
        if (parent != null) {
            current = parent.getPrevSibling();
            while (current != null) {
                if (current.getNode() != null) {
                    IElementType type = current.getNode().getElementType();

                    if (type == KiteTokenTypes.NL || type == KiteTokenTypes.NEWLINE) {
                        return true;
                    }

                    if (type == KiteTokenTypes.ASSIGN) {
                        return false;
                    }
                }
                current = current.getPrevSibling();
            }
        }

        // Default to left side (property name position)
        return true;
    }

    // ========== Component Definition Context Detection ==========

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
     * Get the component definition context if we're inside a component definition body.
     * Returns null if not inside a component definition, or if it's a component instance.
     * <p>
     * Component definition: component TypeName { } (only one identifier before {)
     * Component instance: component TypeName instanceName { } (two identifiers before {)
     */
    @Nullable
    private ComponentDefinitionContext getEnclosingComponentDefinitionContext(PsiElement position) {
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
    private boolean isComponentDefinition(PsiElement componentDeclaration) {
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
    private String extractComponentTypeName(PsiElement componentDeclaration) {
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

    /**
     * Extract the type and property name from the current input/output line.
     * Parses backwards from position to find: input/output type name =
     * Returns null if not on an input/output line with an assignment.
     */
    @Nullable
    private InputOutputInfo getInputOutputInfo(PsiElement position) {
        // First check if we're after an = sign
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

    /**
     * Add value completions for the right side of assignments in resource blocks.
     * Shows variables, inputs, outputs, resources, components, and functions in that priority order.
     * Includes items from imported files.
     */
    private void addValueCompletions(PsiFile file, @NotNull CompletionResultSet result) {
        Set<String> addedNames = new HashSet<>();

        // 1. Variables (highest priority)
        collectDeclarations(file, (name, declarationType, element) -> {
            if (declarationType == KiteElementTypes.VARIABLE_DECLARATION && !addedNames.contains(name)) {
                addedNames.add(name);
                LookupElementBuilder lookup = LookupElementBuilder.create(name)
                        .withTypeText("variable")
                        .withIcon(KiteStructureViewIcons.VARIABLE);
                result.addElement(PrioritizedLookupElement.withPriority(lookup, 500.0));
            }
        });

        // 2. Inputs
        collectDeclarations(file, (name, declarationType, element) -> {
            if (declarationType == KiteElementTypes.INPUT_DECLARATION && !addedNames.contains(name)) {
                addedNames.add(name);
                LookupElementBuilder lookup = LookupElementBuilder.create(name)
                        .withTypeText("input")
                        .withIcon(KiteStructureViewIcons.INPUT);
                result.addElement(PrioritizedLookupElement.withPriority(lookup, 450.0));
            }
        });

        // 3. Outputs
        collectDeclarations(file, (name, declarationType, element) -> {
            if (declarationType == KiteElementTypes.OUTPUT_DECLARATION && !addedNames.contains(name)) {
                addedNames.add(name);
                LookupElementBuilder lookup = LookupElementBuilder.create(name)
                        .withTypeText("output")
                        .withIcon(KiteStructureViewIcons.OUTPUT);
                result.addElement(PrioritizedLookupElement.withPriority(lookup, 400.0));
            }
        });

        // 4. Resources
        collectDeclarations(file, (name, declarationType, element) -> {
            if (declarationType == KiteElementTypes.RESOURCE_DECLARATION && !addedNames.contains(name)) {
                addedNames.add(name);
                LookupElementBuilder lookup = LookupElementBuilder.create(name)
                        .withTypeText("resource")
                        .withIcon(KiteStructureViewIcons.RESOURCE);
                result.addElement(PrioritizedLookupElement.withPriority(lookup, 300.0));
            }
        });

        // 5. Components
        collectDeclarations(file, (name, declarationType, element) -> {
            if (declarationType == KiteElementTypes.COMPONENT_DECLARATION && !addedNames.contains(name)) {
                addedNames.add(name);
                LookupElementBuilder lookup = LookupElementBuilder.create(name)
                        .withTypeText("component")
                        .withIcon(KiteStructureViewIcons.COMPONENT);
                result.addElement(PrioritizedLookupElement.withPriority(lookup, 200.0));
            }
        });

        // 6. Functions (lowest priority among these)
        collectDeclarations(file, (name, declarationType, element) -> {
            if (declarationType == KiteElementTypes.FUNCTION_DECLARATION && !addedNames.contains(name)) {
                addedNames.add(name);
                LookupElementBuilder lookup = LookupElementBuilder.create(name)
                        .withTypeText("function")
                        .withIcon(KiteStructureViewIcons.FUNCTION);
                result.addElement(PrioritizedLookupElement.withPriority(lookup, 100.0));
            }
        });

        // Also add from imported files with slightly lower priority
        addValueCompletionsFromImports(file, result, addedNames);
    }

    /**
     * Add value completions from imported files.
     */
    private void addValueCompletionsFromImports(PsiFile file, @NotNull CompletionResultSet result, Set<String> addedNames) {
        List<PsiFile> importedFiles = KiteImportHelper.getImportedFiles(file);

        for (PsiFile importedFile : importedFiles) {
            if (importedFile == null) continue;

            // Variables from imports
            collectDeclarations(importedFile, (name, declarationType, element) -> {
                if (declarationType == KiteElementTypes.VARIABLE_DECLARATION && !addedNames.contains(name)) {
                    addedNames.add(name);
                    LookupElementBuilder lookup = LookupElementBuilder.create(name)
                            .withTypeText("variable")
                            .withTailText(" (" + importedFile.getName() + ")", true)
                            .withIcon(KiteStructureViewIcons.VARIABLE);
                    result.addElement(PrioritizedLookupElement.withPriority(lookup, 450.0));
                }
            });

            // Inputs from imports
            collectDeclarations(importedFile, (name, declarationType, element) -> {
                if (declarationType == KiteElementTypes.INPUT_DECLARATION && !addedNames.contains(name)) {
                    addedNames.add(name);
                    LookupElementBuilder lookup = LookupElementBuilder.create(name)
                            .withTypeText("input")
                            .withTailText(" (" + importedFile.getName() + ")", true)
                            .withIcon(KiteStructureViewIcons.INPUT);
                    result.addElement(PrioritizedLookupElement.withPriority(lookup, 400.0));
                }
            });

            // Outputs from imports
            collectDeclarations(importedFile, (name, declarationType, element) -> {
                if (declarationType == KiteElementTypes.OUTPUT_DECLARATION && !addedNames.contains(name)) {
                    addedNames.add(name);
                    LookupElementBuilder lookup = LookupElementBuilder.create(name)
                            .withTypeText("output")
                            .withTailText(" (" + importedFile.getName() + ")", true)
                            .withIcon(KiteStructureViewIcons.OUTPUT);
                    result.addElement(PrioritizedLookupElement.withPriority(lookup, 350.0));
                }
            });

            // Resources from imports
            collectDeclarations(importedFile, (name, declarationType, element) -> {
                if (declarationType == KiteElementTypes.RESOURCE_DECLARATION && !addedNames.contains(name)) {
                    addedNames.add(name);
                    LookupElementBuilder lookup = LookupElementBuilder.create(name)
                            .withTypeText("resource")
                            .withTailText(" (" + importedFile.getName() + ")", true)
                            .withIcon(KiteStructureViewIcons.RESOURCE);
                    result.addElement(PrioritizedLookupElement.withPriority(lookup, 250.0));
                }
            });

            // Components from imports
            collectDeclarations(importedFile, (name, declarationType, element) -> {
                if (declarationType == KiteElementTypes.COMPONENT_DECLARATION && !addedNames.contains(name)) {
                    addedNames.add(name);
                    LookupElementBuilder lookup = LookupElementBuilder.create(name)
                            .withTypeText("component")
                            .withTailText(" (" + importedFile.getName() + ")", true)
                            .withIcon(KiteStructureViewIcons.COMPONENT);
                    result.addElement(PrioritizedLookupElement.withPriority(lookup, 150.0));
                }
            });

            // Functions from imports
            collectDeclarations(importedFile, (name, declarationType, element) -> {
                if (declarationType == KiteElementTypes.FUNCTION_DECLARATION && !addedNames.contains(name)) {
                    addedNames.add(name);
                    LookupElementBuilder lookup = LookupElementBuilder.create(name)
                            .withTypeText("function")
                            .withTailText(" (" + importedFile.getName() + ")", true)
                            .withIcon(KiteStructureViewIcons.FUNCTION);
                    result.addElement(PrioritizedLookupElement.withPriority(lookup, 50.0));
                }
            });
        }
    }

    /**
     * Extract the resource type name from a resource declaration.
     * Pattern: resource TypeName instanceName { ... }
     */
    @Nullable
    private String extractResourceTypeName(PsiElement resourceDecl) {
        boolean foundResource = false;
        PsiElement child = resourceDecl.getFirstChild();

        while (child != null) {
            if (child.getNode() != null) {
                IElementType type = child.getNode().getElementType();

                if (type == KiteTokenTypes.RESOURCE) {
                    foundResource = true;
                } else if (foundResource && type == KiteTokenTypes.IDENTIFIER) {
                    return child.getText(); // First identifier is the type name
                } else if (type == KiteTokenTypes.LBRACE) {
                    break;
                }
            }
            child = child.getNextSibling();
        }
        return null;
    }

    /**
     * Add schema property completions with high priority.
     * Excludes properties that are already defined in the resource block.
     * Excludes @cloud properties (they are set by the cloud provider).
     */
    private void addSchemaPropertyCompletions(PsiFile file, @NotNull CompletionResultSet result, ResourceContext resourceContext) {
        Map<String, SchemaPropertyInfo> schemaProperties = findSchemaProperties(file, resourceContext.schemaName);

        // Collect already-defined properties in the resource block
        Set<String> existingProperties = collectExistingPropertyNames(resourceContext.resourceDeclaration);

        for (Map.Entry<String, SchemaPropertyInfo> entry : schemaProperties.entrySet()) {
            String propertyName = entry.getKey();
            SchemaPropertyInfo propInfo = entry.getValue();

            // Skip properties that are already defined in the resource
            if (existingProperties.contains(propertyName)) {
                continue;
            }

            // Skip @cloud properties - they are set by the cloud provider, not by the user
            if (propInfo.isCloud) {
                continue;
            }

            // Create lookup element with high priority
            LookupElementBuilder element = LookupElementBuilder.create(propertyName)
                    .withTypeText(propInfo.type)
                    .withIcon(KiteStructureViewIcons.PROPERTY)
                    .withBoldness(true)
                    .withInsertHandler((ctx, item) -> {
                        // Add " = " after property name
                        ctx.getDocument().insertString(ctx.getTailOffset(), " = ");
                        ctx.getEditor().getCaretModel().moveToOffset(ctx.getTailOffset());
                    });

            // Use PrioritizedLookupElement to boost priority
            result.addElement(PrioritizedLookupElement.withPriority(element, 100.0));
        }
    }

    /**
     * Collect the names of properties already defined in a resource block.
     */
    private Set<String> collectExistingPropertyNames(PsiElement resourceDecl) {
        Set<String> propertyNames = new HashSet<>();
        int braceDepth = 0;

        PsiElement child = resourceDecl.getFirstChild();
        while (child != null) {
            if (child.getNode() != null) {
                IElementType type = child.getNode().getElementType();

                if (type == KiteTokenTypes.LBRACE) {
                    braceDepth++;
                } else if (type == KiteTokenTypes.RBRACE) {
                    braceDepth--;
                } else if (braceDepth == 1 && type == KiteTokenTypes.IDENTIFIER) {
                    // Check if this identifier is followed by = (it's a property definition)
                    PsiElement next = skipWhitespaceForward(child.getNextSibling());
                    if (next != null && next.getNode() != null) {
                        IElementType nextType = next.getNode().getElementType();
                        if (nextType == KiteTokenTypes.ASSIGN || nextType == KiteTokenTypes.PLUS_ASSIGN) {
                            propertyNames.add(child.getText());
                        }
                    }
                }
            }
            child = child.getNextSibling();
        }

        return propertyNames;
    }

    /**
     * Find schema properties by name. Returns a map of property name to SchemaPropertyInfo.
     * Searches in current file and imported files.
     */
    private Map<String, SchemaPropertyInfo> findSchemaProperties(PsiFile file, String schemaName) {
        Map<String, SchemaPropertyInfo> properties = new HashMap<>();

        // Search in current file
        findSchemaPropertiesRecursive(file, schemaName, properties);

        // If not found, search in imported files
        if (properties.isEmpty()) {
            findSchemaPropertiesInImports(file, schemaName, properties, new HashSet<>());
        }

        return properties;
    }

    /**
     * Recursively search for a schema and extract its properties.
     */
    private void findSchemaPropertiesRecursive(PsiElement element, String schemaName, Map<String, SchemaPropertyInfo> properties) {
        if (element == null || element.getNode() == null) return;

        if (element.getNode().getElementType() == KiteElementTypes.SCHEMA_DECLARATION) {
            // Check if this is the schema we're looking for
            String name = extractSchemaName(element);
            if (schemaName.equals(name)) {
                extractSchemaProperties(element, properties);
                return;
            }
        }

        // Recurse into children
        PsiElement child = element.getFirstChild();
        while (child != null) {
            findSchemaPropertiesRecursive(child, schemaName, properties);
            if (!properties.isEmpty()) return; // Found it
            child = child.getNextSibling();
        }
    }

    /**
     * Search for schema properties in imported files.
     */
    private void findSchemaPropertiesInImports(PsiFile file, String schemaName,
                                               Map<String, SchemaPropertyInfo> properties, Set<String> visited) {
        List<PsiFile> importedFiles = KiteImportHelper.getImportedFiles(file);

        for (PsiFile importedFile : importedFiles) {
            if (importedFile == null || importedFile.getVirtualFile() == null) continue;

            String path = importedFile.getVirtualFile().getPath();
            if (visited.contains(path)) continue;
            visited.add(path);

            findSchemaPropertiesRecursive(importedFile, schemaName, properties);
            if (!properties.isEmpty()) return;

            // Recursively check imports
            findSchemaPropertiesInImports(importedFile, schemaName, properties, visited);
            if (!properties.isEmpty()) return;
        }
    }

    /**
     * Get the schema name from a schema declaration.
     */
    @Nullable
    private String extractSchemaName(PsiElement schemaDecl) {
        boolean foundSchema = false;
        PsiElement child = schemaDecl.getFirstChild();

        while (child != null) {
            if (child.getNode() != null) {
                IElementType type = child.getNode().getElementType();

                if (type == KiteTokenTypes.SCHEMA) {
                    foundSchema = true;
                } else if (foundSchema && type == KiteTokenTypes.IDENTIFIER) {
                    return child.getText();
                } else if (type == KiteTokenTypes.LBRACE) {
                    break;
                }
            }
            child = child.getNextSibling();
        }
        return null;
    }

    /**
     * Information about a schema property.
     */
    private static class SchemaPropertyInfo {
        final String type;
        final boolean isCloud;

        SchemaPropertyInfo(String type, boolean isCloud) {
            this.type = type;
            this.isCloud = isCloud;
        }
    }

    /**
     * Extract property definitions from a schema.
     * Pattern inside schema: [@cloud] type propertyName [= defaultValue]
     * Properties with @cloud annotation are marked as cloud properties.
     */
    private void extractSchemaProperties(PsiElement schemaDecl, Map<String, SchemaPropertyInfo> properties) {
        boolean insideBraces = false;
        String currentType = null;
        boolean isCloudProperty = false;

        PsiElement child = schemaDecl.getFirstChild();
        while (child != null) {
            if (child.getNode() != null) {
                IElementType type = child.getNode().getElementType();

                if (type == KiteTokenTypes.LBRACE) {
                    insideBraces = true;
                } else if (type == KiteTokenTypes.RBRACE) {
                    break;
                } else if (insideBraces) {
                    // Skip whitespace and newlines
                    if (type == KiteTokenTypes.WHITESPACE ||
                        type == KiteTokenTypes.NL ||
                        type == KiteTokenTypes.NEWLINE ||
                        type == com.intellij.psi.TokenType.WHITE_SPACE) {
                        child = child.getNextSibling();
                        continue;
                    }

                    // Check for @cloud annotation
                    if (type == KiteTokenTypes.AT) {
                        // Look at next sibling to see if it's "cloud"
                        PsiElement next = skipWhitespaceForward(child.getNextSibling());
                        if (next != null && next.getNode() != null &&
                            next.getNode().getElementType() == KiteTokenTypes.IDENTIFIER &&
                            "cloud".equals(next.getText())) {
                            isCloudProperty = true;
                        }
                        child = child.getNextSibling();
                        continue;
                    }

                    // Handle 'any' keyword as a type
                    if (type == KiteTokenTypes.ANY) {
                        currentType = "any";
                        child = child.getNextSibling();
                        continue;
                    }

                    // Track type -> name pattern
                    if (type == KiteTokenTypes.IDENTIFIER) {
                        String text = child.getText();
                        // Skip "cloud" if we just saw @
                        if ("cloud".equals(text) && isCloudProperty) {
                            child = child.getNextSibling();
                            continue;
                        }
                        if (currentType == null) {
                            // First identifier is the type
                            currentType = text;
                        } else {
                            // Second identifier is the property name
                            properties.put(text, new SchemaPropertyInfo(currentType, isCloudProperty));
                            currentType = null;
                            isCloudProperty = false; // Reset for next property
                        }
                    }

                    // Reset on newline or assignment (end of property definition)
                    if (type == KiteTokenTypes.NL || type == KiteTokenTypes.ASSIGN) {
                        currentType = null;
                    }
                }
            }
            child = child.getNextSibling();
        }
    }
}
