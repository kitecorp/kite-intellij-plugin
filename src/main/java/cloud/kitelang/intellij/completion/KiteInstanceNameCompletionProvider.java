package cloud.kitelang.intellij.completion;

import cloud.kitelang.intellij.psi.KiteTokenTypes;
import cloud.kitelang.intellij.structure.KiteStructureViewIcons;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static cloud.kitelang.intellij.util.KitePsiUtil.skipWhitespaceBackward;

/**
 * Provides instance name completion for resource and component declarations.
 * <p>
 * When typing after "resource TypeName " or "component TypeName ", suggests smart instance names:
 * - camelCase version of the type name (DatabaseConfig → databaseConfig)
 * - Prefixed versions (myDatabase, primaryDatabase)
 * - Common abbreviations (Database → db, Server → server)
 */
public class KiteInstanceNameCompletionProvider extends CompletionProvider<CompletionParameters> {

    /**
     * Check if position is in an instance name context.
     * Public static method for other providers to check and skip.
     */
    public static boolean isInInstanceNameContext(@NotNull PsiElement position) {
        return getInstanceNameContextStatic(position) != null;
    }

    /**
     * Common abbreviations for type name parts.
     */
    private static final Map<String, String> ABBREVIATIONS = Map.of(
            "database", "db",
            "configuration", "config",
            "connection", "conn",
            "application", "app",
            "environment", "env",
            "repository", "repo",
            "service", "svc"
    );

    /**
     * Common prefixes to suggest for instance names.
     */
    private static final String[] PREFIXES = {"my", "primary", "main", "default"};

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
        PsiElement position = parameters.getPosition();

        // Check if we're in an instance name position
        InstanceNameContext nameContext = getInstanceNameContextStatic(position);
        if (nameContext == null) {
            return;
        }

        // Stop other providers from adding their completions (variables, schemas, etc.)
        result.stopHere();

        // Generate and add name suggestions
        addInstanceNameSuggestions(result, nameContext.typeName, nameContext.isComponent);
    }

    /**
     * Check if cursor is in an instance name position (after type name, before brace).
     * Returns the type name if in valid position, null otherwise.
     */
    @Nullable
    private static InstanceNameContext getInstanceNameContextStatic(PsiElement position) {
        // Walk backwards to find the pattern: (resource|component) TypeName <cursor>
        PsiElement current = skipWhitespaceBackward(position.getPrevSibling());

        // If position is the completion dummy identifier, check parent's previous sibling
        if (current == null) {
            PsiElement parent = position.getParent();
            if (parent != null) {
                current = skipWhitespaceBackward(parent.getPrevSibling());
            }
        }

        // Expect an identifier (the type name)
        if (current == null || current.getNode() == null) {
            return null;
        }

        if (current.getNode().getElementType() != KiteTokenTypes.IDENTIFIER) {
            return null;
        }

        String typeName = current.getText();

        // Now check what's before the type name - should be 'resource' or 'component' keyword
        PsiElement beforeType = skipWhitespaceBackward(current.getPrevSibling());
        if (beforeType == null || beforeType.getNode() == null) {
            return null;
        }

        IElementType keywordType = beforeType.getNode().getElementType();
        boolean isComponent = keywordType == KiteTokenTypes.COMPONENT;
        boolean isResource = keywordType == KiteTokenTypes.RESOURCE;

        if (!isComponent && !isResource) {
            return null;
        }

        // Make sure we're not already inside braces
        if (isInsideOrAfterBrace(position)) {
            return null;
        }

        return new InstanceNameContext(typeName, isComponent);
    }

    /**
     * Check if position is inside or after an opening brace.
     */
    private static boolean isInsideOrAfterBrace(PsiElement position) {
        // Walk through siblings to see if there's a { before us
        PsiElement current = position.getPrevSibling();
        while (current != null) {
            if (current.getNode() != null) {
                IElementType type = current.getNode().getElementType();
                if (type == KiteTokenTypes.LBRACE) {
                    return true;
                }
                // If we hit a newline before finding a brace, we're still in header
                if (type == KiteTokenTypes.NL || type == KiteTokenTypes.NEWLINE) {
                    // Check if there's a resource/component keyword on this line
                    PsiElement prev = skipWhitespaceBackward(current.getPrevSibling());
                    if (prev != null && prev.getNode() != null) {
                        IElementType prevType = prev.getNode().getElementType();
                        if (prevType == KiteTokenTypes.IDENTIFIER) {
                            // Continue checking for resource/component before identifier
                            PsiElement beforeId = skipWhitespaceBackward(prev.getPrevSibling());
                            if (beforeId != null && beforeId.getNode() != null) {
                                IElementType keywordType = beforeId.getNode().getElementType();
                                if (keywordType == KiteTokenTypes.RESOURCE ||
                                    keywordType == KiteTokenTypes.COMPONENT) {
                                    return false; // We're on resource/component line, before brace
                                }
                            }
                        }
                    }
                }
            }
            current = current.getPrevSibling();
        }

        // Also check parent context
        PsiElement parent = position.getParent();
        if (parent != null) {
            current = parent.getPrevSibling();
            while (current != null) {
                if (current.getNode() != null &&
                    current.getNode().getElementType() == KiteTokenTypes.LBRACE) {
                    return true;
                }
                current = current.getPrevSibling();
            }
        }

        return false;
    }

    /**
     * Generate and add instance name suggestions based on the type name.
     */
    private void addInstanceNameSuggestions(@NotNull CompletionResultSet result,
                                            String typeName, boolean isComponent) {
        Set<String> addedNames = new LinkedHashSet<>();
        String icon = isComponent ? "component" : "resource";

        // 1. CamelCase version (highest priority)
        String camelCase = toCamelCase(typeName);
        addSuggestion(result, camelCase, icon, 1000.0, addedNames);

        // 2. Last word in camelCase as standalone (e.g., DatabaseConfig → config)
        String lastWord = extractLastWord(typeName);
        if (lastWord != null && !lastWord.equals(camelCase)) {
            addSuggestion(result, lastWord, icon, 900.0, addedNames);
        }

        // 3. Abbreviations (e.g., Database → db)
        for (String abbrev : generateAbbreviations(typeName)) {
            addSuggestion(result, abbrev, icon, 800.0, addedNames);
        }

        // 4. Prefixed versions
        double prefixPriority = 600.0;
        for (String prefix : PREFIXES) {
            String prefixedName = prefix + capitalize(toCamelCase(typeName));
            addSuggestion(result, prefixedName, icon, prefixPriority, addedNames);
            prefixPriority -= 50.0;
        }
    }

    /**
     * Add a suggestion if not already added.
     */
    private void addSuggestion(@NotNull CompletionResultSet result, String name,
                               String typeText, double priority, Set<String> addedNames) {
        if (name == null || name.isEmpty() || addedNames.contains(name)) {
            return;
        }
        addedNames.add(name);

        var icon = typeText.equals("component")
                ? KiteStructureViewIcons.COMPONENT
                : KiteStructureViewIcons.RESOURCE;

        LookupElementBuilder element = LookupElementBuilder.create(name)
                .withTypeText(typeText + " instance")
                .withIcon(icon)
                .withInsertHandler((ctx, item) -> {
                    // Add space and opening brace after name
                    ctx.getDocument().insertString(ctx.getTailOffset(), " {");
                    ctx.getEditor().getCaretModel().moveToOffset(ctx.getTailOffset());
                });

        result.addElement(PrioritizedLookupElement.withPriority(element, priority));
    }

    /**
     * Convert PascalCase to camelCase.
     * E.g., "DatabaseConfig" → "databaseConfig"
     */
    private String toCamelCase(String pascalCase) {
        if (pascalCase == null || pascalCase.isEmpty()) {
            return pascalCase;
        }
        // Handle all-uppercase prefixes (e.g., "AWSLambda" → "awsLambda")
        int upperCount = 0;
        for (int i = 0; i < pascalCase.length(); i++) {
            if (Character.isUpperCase(pascalCase.charAt(i))) {
                upperCount++;
            } else {
                break;
            }
        }

        if (upperCount > 1 && upperCount < pascalCase.length()) {
            // Multiple uppercase at start (e.g., "AWSLambda")
            // Keep all but last uppercase as lowercase
            return pascalCase.substring(0, upperCount - 1).toLowerCase() +
                   pascalCase.substring(upperCount - 1);
        }

        return Character.toLowerCase(pascalCase.charAt(0)) + pascalCase.substring(1);
    }

    /**
     * Capitalize the first letter.
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    /**
     * Extract the last word from a PascalCase name.
     * E.g., "DatabaseConfig" → "config", "WebServer" → "server"
     */
    @Nullable
    private String extractLastWord(String pascalCase) {
        if (pascalCase == null || pascalCase.length() <= 1) {
            return null;
        }

        // Find the last uppercase letter that starts a word
        int lastWordStart = -1;
        for (int i = pascalCase.length() - 1; i > 0; i--) {
            if (Character.isUpperCase(pascalCase.charAt(i))) {
                lastWordStart = i;
                break;
            }
        }

        if (lastWordStart > 0) {
            return pascalCase.substring(lastWordStart).toLowerCase();
        }
        return null;
    }

    /**
     * Generate abbreviations based on type name.
     * E.g., "Database" → ["db"], "DatabaseConfig" → ["db", "config"]
     */
    private List<String> generateAbbreviations(String typeName) {
        List<String> abbreviations = new ArrayList<>();
        String lowerType = typeName.toLowerCase();

        // Check direct abbreviations
        for (Map.Entry<String, String> entry : ABBREVIATIONS.entrySet()) {
            if (lowerType.contains(entry.getKey())) {
                abbreviations.add(entry.getValue());
            }
        }

        // Split into words and check each
        List<String> words = splitCamelCase(typeName);
        for (String word : words) {
            String lowerWord = word.toLowerCase();
            String abbrev = ABBREVIATIONS.get(lowerWord);
            if (abbrev != null && !abbreviations.contains(abbrev)) {
                abbreviations.add(abbrev);
            }
        }

        return abbreviations;
    }

    /**
     * Split a PascalCase name into words.
     * E.g., "DatabaseConfig" → ["Database", "Config"]
     */
    private List<String> splitCamelCase(String name) {
        List<String> words = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c) && current.length() > 0) {
                words.add(current.toString());
                current = new StringBuilder();
            }
            current.append(c);
        }

        if (current.length() > 0) {
            words.add(current.toString());
        }

        return words;
    }

    /**
     * Context about the instance name position.
     */
    private record InstanceNameContext(String typeName, boolean isComponent) {
    }
}
