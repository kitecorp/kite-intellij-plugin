package cloud.kitelang.intellij.completion;

import cloud.kitelang.intellij.documentation.KiteDecoratorDocumentation;
import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import cloud.kitelang.intellij.structure.KiteStructureViewIcons;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides code completion for decorators (after @).
 * Handles both decorator name completion and decorator argument completion.
 */
public class KiteDecoratorCompletionProvider extends CompletionProvider<CompletionParameters> {

    // Decorator categories for grouping
    private static final String CATEGORY_VALIDATION = "validation";
    private static final String CATEGORY_RESOURCE = "resource";
    private static final String CATEGORY_METADATA = "metadata";

    // Target types for decorators
    static final int TARGET_INPUT = 1;
    static final int TARGET_OUTPUT = 2;
    static final int TARGET_RESOURCE = 4;
    static final int TARGET_COMPONENT = 8;
    static final int TARGET_VAR = 16;
    static final int TARGET_SCHEMA = 32;
    static final int TARGET_SCHEMA_PROPERTY = 64;
    static final int TARGET_FUN = 128;

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

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
        PsiElement position = parameters.getPosition();

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
        }
    }

    /**
     * Check if this provider should handle completion at the given position.
     * Returns true if we're in a decorator context (after @ or inside decorator args).
     */
    public static boolean isInDecoratorContext(PsiElement position) {
        return isDecoratorContext(position) || getEnclosingDecoratorName(position) != null;
    }

    /**
     * Check if cursor is after @ (decorator context)
     */
    private static boolean isDecoratorContext(PsiElement position) {
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
            KiteDecoratorDocumentation.DecoratorLookupItem lookupItem =
                    new KiteDecoratorDocumentation.DecoratorLookupItem(decorator.name);

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
    private static String getEnclosingDecoratorName(PsiElement position) {
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
                        while (prev != null && KiteCompletionHelper.isWhitespace(prev)) {
                            prev = prev.getPrevSibling();
                        }
                        if (prev != null && prev.getNode() != null &&
                            prev.getNode().getElementType() == KiteTokenTypes.IDENTIFIER) {
                            // Check if this is preceded by @ (it's a decorator)
                            PsiElement beforeIdent = prev.getPrevSibling();
                            while (beforeIdent != null && KiteCompletionHelper.isWhitespace(beforeIdent)) {
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
}
