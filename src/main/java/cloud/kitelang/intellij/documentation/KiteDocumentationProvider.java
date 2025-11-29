package cloud.kitelang.intellij.documentation;

import cloud.kitelang.intellij.KiteLanguage;
import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Documentation provider for Kite language.
 * Shows quick documentation popup when pressing Ctrl+Q (or F1 on Mac) on declarations.
 */
public class KiteDocumentationProvider extends AbstractDocumentationProvider {

    // Decorator documentation
    private static final Map<String, DecoratorDoc> DECORATOR_DOCS = new HashMap<>();

    static {
        // Validation decorators
        DECORATOR_DOCS.put("minValue", new DecoratorDoc(
                "minValue", "validation",
                "Minimum value constraint for numbers.",
                "(n)",
                "number (0 to 999999)",
                "input, output",
                "number",
                "@minValue(1)\ninput number port = 8080"
        ));
        DECORATOR_DOCS.put("maxValue", new DecoratorDoc(
                "maxValue", "validation",
                "Maximum value constraint for numbers.",
                "(n)",
                "number (0 to 999999)",
                "input, output",
                "number",
                "@maxValue(65535)\ninput number port = 8080"
        ));
        DECORATOR_DOCS.put("minLength", new DecoratorDoc(
                "minLength", "validation",
                "Minimum length constraint for strings and arrays.",
                "(n)",
                "number (0 to 999999)",
                "input, output",
                "string, array",
                "@minLength(3)\ninput string name\n\n@minLength(1)\ninput string[] tags"
        ));
        DECORATOR_DOCS.put("maxLength", new DecoratorDoc(
                "maxLength", "validation",
                "Maximum length constraint for strings and arrays.",
                "(n)",
                "number (0 to 999999)",
                "input, output",
                "string, array",
                "@maxLength(255)\ninput string name\n\n@maxLength(10)\ninput string[] tags"
        ));
        DECORATOR_DOCS.put("nonEmpty", new DecoratorDoc(
                "nonEmpty", "validation",
                "Ensures strings or arrays are not empty.",
                "",
                "none",
                "input",
                "string, array",
                "@nonEmpty\ninput string name\n\n@nonEmpty\ninput string[] tags"
        ));
        DECORATOR_DOCS.put("validate", new DecoratorDoc(
                "validate", "validation",
                "Custom validation with regex pattern or preset.",
                "(regex: \"pattern\") or (preset: \"name\")",
                "named: regex: string or preset: string",
                "input, output",
                "string, array",
                "@validate(regex: \"^[a-z]+$\")\ninput string name\n\n@validate(preset: \"email\")\ninput string email"
        ));
        DECORATOR_DOCS.put("allowed", new DecoratorDoc(
                "allowed", "validation",
                "Whitelist of allowed values.",
                "([values])",
                "array of literals (1 to 256 elements)",
                "input",
                "string, number, object, array",
                "@allowed([\"dev\", \"staging\", \"prod\"])\ninput string environment = \"dev\"\n\n@allowed([80, 443, 8080])\ninput number port = 80"
        ));
        DECORATOR_DOCS.put("unique", new DecoratorDoc(
                "unique", "validation",
                "Ensures array elements are unique.",
                "",
                "none",
                "input",
                "array",
                "@unique\ninput string[] tags = [\"web\", \"api\"]"
        ));

        // Resource decorators
        DECORATOR_DOCS.put("existing", new DecoratorDoc(
                "existing", "resource",
                "Reference existing cloud resources by ARN, URL, or ID.\n\nSupported formats:\n• ARN: arn:aws:s3:::bucket-name\n• URL: https://example.com or s3://bucket/key\n• EC2 Instance ID: i-0123456789abcdef0\n• KMS Alias: alias/my-key\n• Log Group: /aws/lambda/my-function\n• Tags: Environment=prod,Team=platform",
                "(\"reference\")",
                "string (ARN, URL, ID, alias, tags)",
                "resource",
                null,
                "@existing(\"arn:aws:s3:::my-bucket\")\nresource S3.Bucket existing_bucket {}\n\n@existing(\"i-0123456789abcdef0\")\nresource EC2.Instance existing_instance {}"
        ));
        DECORATOR_DOCS.put("sensitive", new DecoratorDoc(
                "sensitive", "resource",
                "Mark sensitive data (passwords, secrets, API keys). The value will be hidden in logs and outputs.",
                "",
                "none",
                "input, output",
                null,
                "@sensitive\ninput string api_key\n\n@sensitive\noutput string connection_string"
        ));
        DECORATOR_DOCS.put("dependsOn", new DecoratorDoc(
                "dependsOn", "resource",
                "Explicit dependency declaration between resources/components. The dependent resources will be created first.",
                "(resource) or ([resources])",
                "resource/component reference, or array of references",
                "resource, component (instances only)",
                null,
                "@dependsOn(subnet)\nresource EC2.Instance server { ... }\n\n@dependsOn([vpc, subnet, security_group])\nresource RDS.Instance database { ... }"
        ));
        DECORATOR_DOCS.put("tags", new DecoratorDoc(
                "tags", "resource",
                "Add cloud provider tags to resources.\n\nFormats:\n• Object: @tags({ Environment: \"prod\", Team: \"platform\" })\n• Array: @tags([\"Environment=prod\", \"Team=platform\"])\n• String: @tags(\"Environment=prod\")",
                "({key: value}) or ([strings]) or (\"string\")",
                "object, array of strings, or string",
                "resource, component (instances only)",
                null,
                "@tags({ Environment: \"prod\", Team: \"platform\" })\nresource S3.Bucket photos { name = \"photos\" }\n\n@tags([\"Environment=staging\"])\nresource EC2.Instance server { ... }"
        ));
        DECORATOR_DOCS.put("provider", new DecoratorDoc(
                "provider", "resource",
                "Target specific cloud providers for resource provisioning.",
                "(\"provider\") or ([\"providers\"])",
                "string or array of strings",
                "resource, component (instances only)",
                null,
                "@provider(\"aws\")\nresource S3.Bucket photos { name = \"photos\" }\n\n@provider([\"aws\", \"azure\"])\nresource Storage.Bucket multi_cloud { ... }"
        ));
        // Keep provisionOn as alias for backwards compatibility
        DECORATOR_DOCS.put("provisionOn", new DecoratorDoc(
                "provisionOn", "resource",
                "Deprecated: Use @provider instead.\n\nTarget specific cloud providers for resource provisioning.",
                "([\"providers\"])",
                "string or array of strings",
                "resource, component (instances only)",
                null,
                "@provider([\"aws\", \"azure\"])\nresource Database main { }"
        ));

        // Metadata decorators
        DECORATOR_DOCS.put("description", new DecoratorDoc(
                "description", "metadata",
                "Documentation for any declaration.",
                "(\"text\")",
                "string",
                "resource, component, input, output, var, schema, schema property, fun",
                null,
                "@description(\"The port number for the web server\")\ninput number port = 8080\n\n@description(\"Main application database\")\nresource RDS.Instance database { ... }"
        ));
        DECORATOR_DOCS.put("count", new DecoratorDoc(
                "count", "metadata",
                "Create N instances of a resource or component. Injects a special 'count' variable (0-indexed) that can be used in expressions.",
                "(n)",
                "number",
                "resource, component (instances only)",
                null,
                "@count(3)\nresource EC2.Instance server {\n  name = \"server-$count\"  // \"server-0\", \"server-1\", \"server-2\"\n}"
        ));
        DECORATOR_DOCS.put("cloud", new DecoratorDoc(
                "cloud", "metadata",
                "Indicates that this property's value is set by the cloud provider at runtime, not by the user.",
                "",
                "none",
                "schema property",
                null,
                "schema Instance {\n  string id\n  @cloud\n  string publicIp\n}"
        ));
    }

    /**
     * Get decorator documentation HTML by name.
     * This is used by the completion contributor to show documentation in the autocomplete popup.
     *
     * @param decoratorName the name of the decorator (without @)
     * @return HTML documentation or null if decorator is unknown
     */
    @Nullable
    public static String getDecoratorDocumentation(String decoratorName) {
        DecoratorDoc doc = DECORATOR_DOCS.get(decoratorName);
        if (doc != null) {
            return generateDecoratorDocumentationStatic(doc);
        }
        return null;
    }

    /**
     * Static version of generateDecoratorDocumentation for use by other classes.
     * Uses DocumentationMarkup for proper formatting in IntelliJ's lookup documentation popup.
     */
    private static String generateDecoratorDocumentationStatic(DecoratorDoc doc) {
        StringBuilder sb = new StringBuilder();

        // Use div-based layout with inline styles (same as non-static version)
        sb.append("<div style=\"overflow-x: auto; max-width: 800px;\">");

        // Header: decorator name and category
        sb.append("<div style=\"margin-bottom: 8px;\">");
        sb.append("<b>Decorator</b> ");
        sb.append("<span style=\"color: ").append(COLOR_DECORATOR).append(";\">@").append(doc.name).append("</span>");
        sb.append(" <span style=\"color: #888;\">(").append(doc.category).append(")</span>");
        sb.append("</div>");

        // Description
        sb.append("<div style=\"margin-bottom: 8px;\">");
        sb.append(escapeHtmlStatic(doc.description));
        sb.append("</div>");

        // Syntax
        if (!doc.syntax.isEmpty()) {
            sb.append("<div style=\"margin-bottom: 4px;\">");
            sb.append("<span>Syntax:</span> ");
            sb.append("<code><span style=\"color: ").append(COLOR_DECORATOR).append(";\">@").append(doc.name).append("</span>").append(escapeHtmlStatic(doc.syntax)).append("</code>");
            sb.append("</div>");
        }

        // Argument info table
        sb.append("<div style=\"margin-bottom: 8px; background-color: ").append(getSectionBackgroundColorStatic()).append("; padding: 8px; border-radius: 4px;\">");
        sb.append("<span style=\"font-weight: bold;\">Arguments:</span>");
        sb.append("<table style=\"border-collapse: collapse; width: 100%; margin-top: 4px;\">");

        // Argument type
        sb.append("<tr><td style=\"padding: 2px 8px 2px 0; color: #888;\">Type:</td>");
        sb.append("<td style=\"padding: 2px 0;\"><code>").append(escapeHtmlStatic(doc.argumentType)).append("</code></td></tr>");

        // Targets
        sb.append("<tr><td style=\"padding: 2px 8px 2px 0; color: #888;\">Targets:</td>");
        sb.append("<td style=\"padding: 2px 0;\"><code>").append(escapeHtmlStatic(doc.targets)).append("</code></td></tr>");

        // Applies to (only if specified)
        if (doc.appliesTo != null) {
            sb.append("<tr><td style=\"padding: 2px 8px 2px 0; color: #888;\">Applies to:</td>");
            sb.append("<td style=\"padding: 2px 0;\"><code>").append(escapeHtmlStatic(doc.appliesTo)).append("</code></td></tr>");
        }

        sb.append("</table>");
        sb.append("</div>");

        // Example with syntax highlighting (uses different background to distinguish from info section)
        sb.append("<div style=\"margin-bottom: 8px; background-color: ").append(getCodeBackgroundColorStatic()).append("; padding: 8px; border-radius: 4px;\">");
        sb.append("<span style=\"font-weight: bold;\">Example:</span>");
        sb.append("<pre style=\"margin: 4px 0 0 0; padding: 0; font-family: monospace; background: transparent;\">");
        sb.append(colorizeDecoratorExampleStatic(doc.example));
        sb.append("</pre>");
        sb.append("</div>");

        sb.append("</div>");

        return sb.toString();
    }

    private static String escapeHtmlStatic(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String getSectionBackgroundColorStatic() {
        return JBColor.isBright() ? "#F5F5F5" : "#2D2D2D";
    }

    // Slightly different background for code examples to distinguish from info sections
    private static String getCodeBackgroundColorStatic() {
        return JBColor.isBright() ? "#EBEBEB" : "#383838";
    }

    // Colorize decorator example using tokenized approach to avoid regex interference
    private static String colorizeDecoratorExampleStatic(String example) {
        if (example == null || example.isEmpty()) return "";

        StringBuilder result = new StringBuilder();
        int i = 0;
        int len = example.length();

        while (i < len) {
            char c = example.charAt(i);

            // Handle @ symbol (decorator start)
            if (c == '@') {
                result.append("<span style=\"color: ").append(COLOR_DECORATOR).append(";\">@");
                i++;
                // Capture decorator name
                int start = i;
                while (i < len && (Character.isLetterOrDigit(example.charAt(i)) || example.charAt(i) == '_')) {
                    i++;
                }
                result.append(escapeHtmlStatic(example.substring(start, i)));
                result.append("</span>");
                continue;
            }

            // Handle string literals (quoted strings)
            if (c == '"') {
                int start = i;
                i++; // skip opening quote
                while (i < len && example.charAt(i) != '"') {
                    if (example.charAt(i) == '\\' && i + 1 < len) {
                        i++; // skip escaped char
                    }
                    i++;
                }
                if (i < len) i++; // skip closing quote
                String str = escapeHtmlStatic(example.substring(start, i));
                result.append("<span style=\"color: ").append(COLOR_STRING).append(";\">").append(str).append("</span>");
                continue;
            }

            // Handle identifiers and keywords
            if (Character.isLetter(c) || c == '_') {
                int start = i;
                while (i < len && (Character.isLetterOrDigit(example.charAt(i)) || example.charAt(i) == '_')) {
                    i++;
                }
                String word = example.substring(start, i);

                if (KEYWORDS_STATIC.contains(word)) {
                    result.append("<span style=\"color: ").append(COLOR_KEYWORD).append(";\">")
                            .append(escapeHtmlStatic(word)).append("</span>");
                } else if (TYPE_NAMES_STATIC.contains(word)) {
                    result.append("<span style=\"color: ").append(COLOR_TYPE).append(";\">")
                            .append(escapeHtmlStatic(word)).append("</span>");
                } else {
                    result.append(escapeHtmlStatic(word));
                }
                continue;
            }

            // Handle numbers
            if (Character.isDigit(c)) {
                int start = i;
                while (i < len && (Character.isDigit(example.charAt(i)) || example.charAt(i) == '.')) {
                    i++;
                }
                String num = example.substring(start, i);
                result.append("<span style=\"color: ").append(COLOR_NUMBER).append(";\">")
                        .append(escapeHtmlStatic(num)).append("</span>");
                continue;
            }

            // Handle newlines
            if (c == '\n') {
                result.append("\n");
                i++;
                continue;
            }

            // Other characters - escape and append
            result.append(escapeHtmlStatic(String.valueOf(c)));
            i++;
        }

        return result.toString();
    }

    // Static keyword and type sets for use in static methods
    private static final java.util.Set<String> KEYWORDS_STATIC = new java.util.HashSet<>(java.util.Arrays.asList(
            "resource", "component", "schema", "fun", "var", "input", "output", "type",
            "if", "else", "for", "while", "in", "return", "import", "from", "init", "this",
            "true", "false", "null"
    ));

    private static final java.util.Set<String> TYPE_NAMES_STATIC = new java.util.HashSet<>(java.util.Arrays.asList(
            "string", "number", "boolean", "object", "any", "void", "list", "map"
    ));

    /**
     * Holds documentation for a decorator.
     */
    private static class DecoratorDoc {
        final String name;
        final String category;
        final String description;
        final String syntax;
        final String argumentType;  // What type of argument it takes
        final String targets;       // What declarations it can apply to
        final String appliesTo;     // What value types it applies to (null if N/A)
        final String example;

        DecoratorDoc(String name, String category, String description, String syntax,
                     String argumentType, String targets, String appliesTo, String example) {
            this.name = name;
            this.category = category;
            this.description = description;
            this.syntax = syntax;
            this.argumentType = argumentType;
            this.targets = targets;
            this.appliesTo = appliesTo;
            this.example = example;
        }
    }

    /**
     * Wrapper class to identify decorator lookup items.
     * Used to enable documentation popup for decorators in autocomplete.
     */
    public static class DecoratorLookupItem {
        public final String name;

        public DecoratorLookupItem(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Fake PSI element that holds decorator name for documentation lookup.
     * This allows IntelliJ to properly cache documentation per-decorator.
     */
    private static class DecoratorDocElement extends FakePsiElement {
        private final String decoratorName;
        private final PsiElement parent;

        DecoratorDocElement(String decoratorName, PsiElement parent) {
            this.decoratorName = decoratorName;
            this.parent = parent;
        }

        @Override
        public PsiElement getParent() {
            return parent;
        }

        @Override
        public String getText() {
            return "@" + decoratorName;
        }

        public String getDecoratorName() {
            return decoratorName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DecoratorDocElement that = (DecoratorDocElement) o;
            return decoratorName.equals(that.decoratorName);
        }

        @Override
        public int hashCode() {
            return decoratorName.hashCode();
        }
    }

    @Override
    public @Nullable PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
        // Handle decorator lookup items - create a unique fake element for each decorator
        if (object instanceof DecoratorLookupItem decoratorItem) {
            return new DecoratorDocElement(decoratorItem.name, element);
        }
        return null;
    }

    @Override
    public @Nullable String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
        // Check if this is a decorator doc element from autocomplete
        if (element instanceof DecoratorDocElement decoratorDocElement) {
            return getDecoratorDocumentation(decoratorDocElement.getDecoratorName());
        }

        if (element == null) {
            return null;
        }

        // Only handle Kite files
        PsiFile file = element.getContainingFile();
        if (file == null || file.getLanguage() != KiteLanguage.INSTANCE) {
            return null;
        }

        // Check if this is a decorator name (identifier after @)
        if (isDecoratorName(element)) {
            String decoratorName = element.getText();
            DecoratorDoc doc = DECORATOR_DOCS.get(decoratorName);
            if (doc != null) {
                return generateDecoratorDocumentation(doc);
            }
            // Unknown decorator - still show basic info
            return generateUnknownDecoratorDoc(decoratorName);
        }

        // Find the declaration containing this element
        PsiElement declaration = findDeclaration(element);
        if (declaration == null) {
            return null;
        }

        return generateDocumentation(declaration);
    }

    /**
     * Check if the element is a decorator name (identifier immediately after @).
     */
    private boolean isDecoratorName(PsiElement element) {
        if (element == null || element.getNode() == null) {
            return false;
        }
        if (element.getNode().getElementType() != KiteTokenTypes.IDENTIFIER) {
            return false;
        }
        // Check if preceded by @
        PsiElement prev = element.getPrevSibling();
        while (prev != null && isWhitespaceElement(prev)) {
            prev = prev.getPrevSibling();
        }
        return prev != null && prev.getNode() != null &&
               prev.getNode().getElementType() == KiteTokenTypes.AT;
    }

    /**
     * Generate documentation HTML for a decorator with full syntax highlighting.
     * Used for Ctrl+Q on decorator names in code (not in autocomplete popup).
     */
    @NotNull
    private String generateDecoratorDocumentation(DecoratorDoc doc) {
        StringBuilder sb = new StringBuilder();

        sb.append("<div style=\"overflow-x: auto; max-width: 800px;\">");

        // Header: decorator name and category
        sb.append("<div style=\"margin-bottom: 8px;\">");
        sb.append("<b>Decorator</b> ");
        sb.append("<span style=\"color: ").append(COLOR_DECORATOR).append(";\">@").append(doc.name).append("</span>");
        sb.append(" <span style=\"color: #888;\">(").append(doc.category).append(")</span>");
        sb.append("</div>");

        // Description
        sb.append("<div style=\"margin-bottom: 8px;\">");
        sb.append(escapeHtml(doc.description));
        sb.append("</div>");

        // Syntax
        if (!doc.syntax.isEmpty()) {
            sb.append("<div style=\"margin-bottom: 4px;\">");
            sb.append("<span>Syntax:</span> ");
            sb.append("<code><span style=\"color: ").append(COLOR_DECORATOR).append(";\">@").append(doc.name).append("</span>").append(escapeHtml(doc.syntax)).append("</code>");
            sb.append("</div>");
        }

        // Argument info table
        sb.append("<div style=\"margin-bottom: 8px; background-color: ").append(getSectionBackgroundColor()).append("; padding: 8px; border-radius: 4px;\">");
        sb.append("<table style=\"border-collapse: collapse; width: 100%;\">");

        // Argument type
        sb.append("<tr><td style=\"padding: 2px 8px 2px 0; color: #888;\">Argument:</td>");
        sb.append("<td style=\"padding: 2px 0;\"><code>").append(escapeHtml(doc.argumentType)).append("</code></td></tr>");

        // Targets
        sb.append("<tr><td style=\"padding: 2px 8px 2px 0; color: #888;\">Targets:</td>");
        sb.append("<td style=\"padding: 2px 0;\"><code>").append(escapeHtml(doc.targets)).append("</code></td></tr>");

        // Applies to (only if specified)
        if (doc.appliesTo != null) {
            sb.append("<tr><td style=\"padding: 2px 8px 2px 0; color: #888;\">Applies to:</td>");
            sb.append("<td style=\"padding: 2px 0;\"><code>").append(escapeHtml(doc.appliesTo)).append("</code></td></tr>");
        }

        sb.append("</table>");
        sb.append("</div>");

        // Example
        sb.append("<div style=\"margin-bottom: 8px; background-color: ").append(getSectionBackgroundColor()).append("; padding: 8px; border-radius: 4px;\">");
        sb.append("<span>Example:</span>");
        sb.append("<pre style=\"margin: 4px 0 0 0; padding: 0; font-family: monospace; background: transparent;\">");
        sb.append(colorizeDecoratorExample(doc.example));
        sb.append("</pre>");
        sb.append("</div>");

        sb.append("</div>");

        return sb.toString();
    }

    /**
     * Generate documentation for an unknown decorator.
     */
    @NotNull
    private String generateUnknownDecoratorDoc(String name) {
        StringBuilder sb = new StringBuilder();

        sb.append("<div style=\"white-space: nowrap; overflow-x: auto; max-width: 800px;\">");

        sb.append("<div style=\"margin-bottom: 8px;\">");
        sb.append("<b>Decorator</b> ");
        sb.append("<span style=\"color: ").append(COLOR_DECORATOR).append(";\">@").append(escapeHtml(name)).append("</span>");
        sb.append("</div>");

        sb.append("<div style=\"margin-bottom: 8px; color: #999;\">");
        sb.append("Unknown decorator. This is not a built-in decorator.");
        sb.append("</div>");

        sb.append("</div>");

        return sb.toString();
    }

    /**
     * Colorize a decorator example with syntax highlighting.
     */
    @NotNull
    private String colorizeDecoratorExample(String example) {
        StringBuilder result = new StringBuilder();
        String[] lines = example.split("\n");

        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                result.append("\n");
            }
            String line = lines[i];

            // Check if line starts with @
            if (line.trim().startsWith("@")) {
                result.append(colorizeDecoratorNoBreaks(line));
            } else {
                result.append(colorizeCodeNoBreaks(line));
            }
        }

        return result.toString();
    }

    @Override
    public @Nullable PsiElement getCustomDocumentationElement(@NotNull Editor editor, @NotNull PsiFile file,
                                                              @Nullable PsiElement contextElement, int targetOffset) {
        if (contextElement == null || file.getLanguage() != KiteLanguage.INSTANCE) {
            return null;
        }

        IElementType elementType = contextElement.getNode().getElementType();

        // Handle identifiers - check if it's a decorator first
        if (elementType == KiteTokenTypes.IDENTIFIER) {
            // Check if this is a decorator name (after @)
            if (isDecoratorName(contextElement)) {
                // Return the element itself - generateDoc will handle it
                return contextElement;
            }

            String name = contextElement.getText();
            PsiElement declaration = findDeclarationByName(file, name);
            if (declaration != null) {
                return declaration;
            }
            // If this identifier is itself a declaration name, return the parent declaration
            PsiElement parent = contextElement.getParent();
            if (isDeclaration(parent)) {
                return parent;
            }
        }

        // Handle @ symbol - show doc for the decorator that follows
        if (elementType == KiteTokenTypes.AT) {
            PsiElement next = contextElement.getNextSibling();
            while (next != null && isWhitespaceElement(next)) {
                next = next.getNextSibling();
            }
            if (next != null && next.getNode() != null &&
                next.getNode().getElementType() == KiteTokenTypes.IDENTIFIER) {
                return next;
            }
        }

        // Handle interpolation tokens
        if (elementType == KiteTokenTypes.INTERP_IDENTIFIER || elementType == KiteTokenTypes.INTERP_SIMPLE) {
            String varName = contextElement.getText();
            if (elementType == KiteTokenTypes.INTERP_SIMPLE && varName.startsWith("$")) {
                varName = varName.substring(1);
            }
            return findDeclarationByName(file, varName);
        }

        return null;
    }

    /**
     * Find the declaration containing or being the given element.
     */
    @Nullable
    private PsiElement findDeclaration(PsiElement element) {
        PsiElement current = element;
        while (current != null) {
            if (isDeclaration(current)) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    /**
     * Check if the element is a declaration.
     */
    private boolean isDeclaration(PsiElement element) {
        if (element == null || element.getNode() == null) {
            return false;
        }
        IElementType type = element.getNode().getElementType();
        return type == KiteElementTypes.VARIABLE_DECLARATION ||
               type == KiteElementTypes.INPUT_DECLARATION ||
               type == KiteElementTypes.OUTPUT_DECLARATION ||
               type == KiteElementTypes.RESOURCE_DECLARATION ||
               type == KiteElementTypes.COMPONENT_DECLARATION ||
               type == KiteElementTypes.SCHEMA_DECLARATION ||
               type == KiteElementTypes.FUNCTION_DECLARATION ||
               type == KiteElementTypes.TYPE_DECLARATION ||
               type == KiteElementTypes.FOR_STATEMENT;
    }

    /**
     * Find a declaration by name in the file.
     */
    @Nullable
    private PsiElement findDeclarationByName(PsiFile file, String name) {
        return findDeclarationRecursive(file, name);
    }

    @Nullable
    private PsiElement findDeclarationRecursive(PsiElement element, String name) {
        if (element.getNode() == null) {
            return null;
        }

        IElementType type = element.getNode().getElementType();

        if (isDeclaration(element)) {
            String declName = getDeclarationName(element, type);
            if (name.equals(declName)) {
                return element;
            }
        }

        // Recurse into children
        PsiElement child = element.getFirstChild();
        while (child != null) {
            PsiElement result = findDeclarationRecursive(child, name);
            if (result != null) {
                return result;
            }
            child = child.getNextSibling();
        }

        return null;
    }

    /**
     * Get the name from a declaration element.
     */
    @Nullable
    private String getDeclarationName(PsiElement declaration, IElementType declarationType) {
        if (declarationType == KiteElementTypes.FOR_STATEMENT) {
            // For statements: find identifier after 'for' keyword
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

        // For most declarations: find the last identifier before '=' or '{'
        PsiElement lastIdentifier = null;
        PsiElement child = declaration.getFirstChild();
        while (child != null) {
            IElementType childType = child.getNode().getElementType();
            if (childType == KiteTokenTypes.IDENTIFIER) {
                lastIdentifier = child;
            } else if (childType == KiteTokenTypes.ASSIGN ||
                       childType == KiteTokenTypes.LBRACE ||
                       childType == KiteTokenTypes.PLUS_ASSIGN) {
                if (lastIdentifier != null) {
                    return lastIdentifier.getText();
                }
            }
            child = child.getNextSibling();
        }

        return lastIdentifier != null ? lastIdentifier.getText() : null;
    }

    /**
     * Generate HTML documentation for a declaration.
     */
    @NotNull
    private String generateDocumentation(PsiElement declaration) {
        StringBuilder sb = new StringBuilder();
        IElementType type = declaration.getNode().getElementType();

        // Get the declaration kind
        String kind = getDeclarationKind(type);
        String name = getDeclarationName(declaration, type);
        String signature = getSignature(declaration, type);

        // Get preceding comment (if any)
        String comment = getPrecedingComment(declaration);

        // Add a wrapper div with no text wrapping - content stays on single lines with horizontal scroll
        // Use overflow-x: auto to enable horizontal scrolling, white-space: nowrap to prevent wrapping
        sb.append("<div style=\"white-space: nowrap; overflow-x: auto; max-width: 800px;\">");

        // Build documentation HTML (without background highlight)
        sb.append("<div style=\"margin-bottom: 8px;\">");
        sb.append("<b>").append(kind).append("</b>");
        if (name != null) {
            sb.append(" ").append(name);
        }
        sb.append("</div>");

        // Add signature section (plain HTML without background)
        if (signature != null && !signature.isEmpty()) {
            sb.append("<div style=\"margin-bottom: 4px;\">");
            sb.append("<span>Declaration:</span> ");
            sb.append("<code>").append(colorizeCode(signature)).append("</code>");
            sb.append("</div>");
        }

        // Add type-specific information (plain HTML without background)
        String typeInfo = getTypeSpecificInfo(declaration, type);
        if (typeInfo != null && !typeInfo.isEmpty()) {
            sb.append(typeInfo);
        }

        // Add decorators section (if any) - AFTER default value, BEFORE comment
        java.util.List<String> decorators = extractDecorators(declaration);
        if (!decorators.isEmpty()) {
            sb.append("<div style=\"margin-bottom: 8px; background-color: ").append(getSectionBackgroundColor()).append("; padding: 8px; border-radius: 4px;\">");
            sb.append("<span>Decorators:</span>");
            sb.append("<pre style=\"margin: 4px 0 0 0; padding: 0; font-family: monospace; background: transparent;\">");
            for (int i = 0; i < decorators.size(); i++) {
                if (i > 0) {
                    sb.append("\n");
                }
                sb.append(colorizeDecoratorNoBreaks(decorators.get(i)));
            }
            sb.append("</pre>");
            sb.append("</div>");
        }

        // Add comment section LAST (after type-specific info like Outputs)
        // Comments can be long, so they appear at the end
        // Use theme-aware text color (inherits from dialog - white in dark theme, black in light theme)
        if (comment != null && !comment.isEmpty()) {
            sb.append("<div style=\"margin-top: 8px;margin-bottom: 8px;\">");
            sb.append(escapeHtml(comment));
            sb.append("</div>");
        }

        // Close the wrapper div
        sb.append("</div>");

        return sb.toString();
    }

    /**
     * Get the kind label for a declaration type.
     */
    @NotNull
    private String getDeclarationKind(IElementType type) {
        if (type == KiteElementTypes.VARIABLE_DECLARATION) return "Variable";
        if (type == KiteElementTypes.INPUT_DECLARATION) return "Input";
        if (type == KiteElementTypes.OUTPUT_DECLARATION) return "Output";
        if (type == KiteElementTypes.RESOURCE_DECLARATION) return "Resource";
        if (type == KiteElementTypes.COMPONENT_DECLARATION) return "Component";
        if (type == KiteElementTypes.SCHEMA_DECLARATION) return "Schema";
        if (type == KiteElementTypes.FUNCTION_DECLARATION) return "Function";
        if (type == KiteElementTypes.TYPE_DECLARATION) return "Type";
        if (type == KiteElementTypes.FOR_STATEMENT) return "Loop Variable";
        return "Declaration";
    }

    /**
     * Get the signature (first line) of the declaration.
     */
    @Nullable
    private String getSignature(PsiElement declaration, IElementType type) {
        String text = declaration.getText();
        // Get first line only (up to first newline or { )
        int newlineIndex = text.indexOf('\n');
        int braceIndex = text.indexOf('{');

        int endIndex = text.length();
        if (newlineIndex > 0 && newlineIndex < endIndex) {
            endIndex = newlineIndex;
        }
        if (braceIndex > 0 && braceIndex < endIndex) {
            endIndex = braceIndex; // Stop BEFORE the brace (not including it)
        }

        String signature = text.substring(0, endIndex).trim();

        return signature;
    }

    /**
     * Get any preceding comment for the declaration.
     * Supports both line comments ({@code //}) and block comments ({@code /* ... *&#47;}).
     * Can collect multiple consecutive line comments into a single documentation block.
     * Skips over decorators (like @allowed([...])) to find comments above them.
     */
    @Nullable
    private String getPrecedingComment(PsiElement declaration) {
        PsiElement prev = declaration.getPrevSibling();

        // Skip whitespace
        while (prev != null && isWhitespaceElement(prev)) {
            prev = prev.getPrevSibling();
        }

        // Skip over decorators to find comments above them
        prev = skipOverDecorators(prev);

        if (prev == null) {
            return null;
        }

        // Check if it's a comment by token type (for Kite language elements)
        IElementType prevType = prev.getNode() != null ? prev.getNode().getElementType() : null;

        // Handle block comments
        if (prevType == KiteTokenTypes.BLOCK_COMMENT || prev instanceof PsiComment) {
            String commentText = prev.getText();
            if (commentText.startsWith("/*") && commentText.endsWith("*/")) {
                // Remove /* and */ and clean up the content
                String content = commentText.substring(2, commentText.length() - 2);
                return cleanBlockCommentContent(content);
            } else if (commentText.startsWith("//")) {
                return commentText.substring(2).trim();
            }
            return commentText.trim();
        }

        // Handle line comments - collect consecutive line comments
        if (prevType == KiteTokenTypes.LINE_COMMENT) {
            StringBuilder commentBuilder = new StringBuilder();
            java.util.List<String> lines = new java.util.ArrayList<>();

            // Collect all consecutive line comments
            while (prev != null) {
                prevType = prev.getNode() != null ? prev.getNode().getElementType() : null;

                if (prevType == KiteTokenTypes.LINE_COMMENT) {
                    String line = prev.getText();
                    if (line.startsWith("//")) {
                        line = line.substring(2).trim();
                    }
                    lines.add(0, line); // Add at beginning since we're going backwards
                } else if (isWhitespaceElement(prev)) {
                    // Continue through whitespace
                } else {
                    // Stop at non-comment, non-whitespace
                    break;
                }
                prev = prev.getPrevSibling();
            }

            if (!lines.isEmpty()) {
                return String.join("\n", lines);
            }
        }

        return null;
    }

    /**
     * Skip over decorators to find what's before them.
     * Decorators are token sequences starting with @ followed by identifier and optional parentheses.
     */
    @Nullable
    private PsiElement skipOverDecorators(PsiElement element) {
        if (element == null) return null;

        IElementType type = element.getNode() != null ? element.getNode().getElementType() : null;

        // If we're at a closing paren, we might be at the end of a decorator
        while (element != null) {
            type = element.getNode() != null ? element.getNode().getElementType() : null;

            // Check if this looks like the end of a decorator (closing paren or identifier after @)
            if (type == KiteTokenTypes.RPAREN) {
                // Skip back through the decorator arguments to find @
                element = skipToAtSymbol(element);
                if (element == null) return null;
                // Now element is at @, skip whitespace before it
                element = element.getPrevSibling();
                while (element != null && isWhitespaceElement(element)) {
                    element = element.getPrevSibling();
                }
            } else if (type == KiteTokenTypes.IDENTIFIER) {
                // Check if there's an @ immediately before this identifier (no-arg decorator)
                PsiElement beforeIdent = element.getPrevSibling();
                while (beforeIdent != null && isWhitespaceElement(beforeIdent)) {
                    beforeIdent = beforeIdent.getPrevSibling();
                }
                if (beforeIdent != null && beforeIdent.getNode() != null &&
                    beforeIdent.getNode().getElementType() == KiteTokenTypes.AT) {
                    // This is a no-arg decorator like @deprecated, skip it
                    element = beforeIdent.getPrevSibling();
                    while (element != null && isWhitespaceElement(element)) {
                        element = element.getPrevSibling();
                    }
                } else {
                    // Not a decorator, we're done
                    break;
                }
            } else if (type == KiteTokenTypes.AT) {
                // At an @ symbol, skip to before it
                element = element.getPrevSibling();
                while (element != null && isWhitespaceElement(element)) {
                    element = element.getPrevSibling();
                }
            } else {
                // Not part of a decorator
                break;
            }
        }

        return element;
    }

    /**
     * Skip backwards from a closing paren to find the @ symbol of a decorator.
     */
    @Nullable
    private PsiElement skipToAtSymbol(PsiElement closeParen) {
        int parenDepth = 1;
        PsiElement current = closeParen.getPrevSibling();

        // Skip through to find the opening paren
        while (current != null && parenDepth > 0) {
            IElementType type = current.getNode() != null ? current.getNode().getElementType() : null;
            if (type == KiteTokenTypes.RPAREN) {
                parenDepth++;
            } else if (type == KiteTokenTypes.LPAREN) {
                parenDepth--;
            }
            if (parenDepth > 0) {
                current = current.getPrevSibling();
            }
        }

        if (current == null) return null;

        // current is at LPAREN, skip to identifier
        current = current.getPrevSibling();
        while (current != null && isWhitespaceElement(current)) {
            current = current.getPrevSibling();
        }

        if (current == null) return null;
        IElementType type = current.getNode() != null ? current.getNode().getElementType() : null;
        if (type != KiteTokenTypes.IDENTIFIER) return null;

        // Skip to @
        current = current.getPrevSibling();
        while (current != null && isWhitespaceElement(current)) {
            current = current.getPrevSibling();
        }

        if (current != null && current.getNode() != null &&
            current.getNode().getElementType() == KiteTokenTypes.AT) {
            return current;
        }

        return null;
    }

    /**
     * Extract decorators from before a declaration.
     * Returns a list of decorator strings like "@allowed([\"dev\", \"prod\"])".
     */
    @NotNull
    private java.util.List<String> extractDecorators(PsiElement declaration) {
        java.util.List<String> decorators = new java.util.ArrayList<>();
        PsiElement prev = declaration.getPrevSibling();

        // Skip whitespace
        while (prev != null && isWhitespaceElement(prev)) {
            prev = prev.getPrevSibling();
        }

        // Collect decorators (walking backwards)
        while (prev != null) {
            IElementType type = prev.getNode() != null ? prev.getNode().getElementType() : null;

            // Check if this is the end of a decorator (closing paren or identifier after @)
            if (type == KiteTokenTypes.RPAREN) {
                // Decorator with arguments: @name(...)
                String decorator = collectDecoratorWithArgs(prev);
                if (decorator != null) {
                    decorators.add(0, decorator); // Add at beginning since we're going backwards
                    prev = skipToAtSymbol(prev);
                    if (prev != null) {
                        prev = prev.getPrevSibling();
                        while (prev != null && isWhitespaceElement(prev)) {
                            prev = prev.getPrevSibling();
                        }
                    }
                } else {
                    break;
                }
            } else if (type == KiteTokenTypes.IDENTIFIER) {
                // Check if there's an @ immediately before this identifier (no-arg decorator)
                PsiElement beforeIdent = prev.getPrevSibling();
                while (beforeIdent != null && isWhitespaceElement(beforeIdent)) {
                    beforeIdent = beforeIdent.getPrevSibling();
                }
                if (beforeIdent != null && beforeIdent.getNode() != null &&
                    beforeIdent.getNode().getElementType() == KiteTokenTypes.AT) {
                    // This is a no-arg decorator like @deprecated
                    decorators.add(0, "@" + prev.getText());
                    prev = beforeIdent.getPrevSibling();
                    while (prev != null && isWhitespaceElement(prev)) {
                        prev = prev.getPrevSibling();
                    }
                } else {
                    // Not a decorator
                    break;
                }
            } else {
                // Not part of a decorator
                break;
            }
        }

        return decorators;
    }

    /**
     * Collect a decorator with arguments, starting from the closing paren.
     * Returns the full decorator string like "@allowed([\"dev\", \"prod\"])".
     */
    @Nullable
    private String collectDecoratorWithArgs(PsiElement closeParen) {
        StringBuilder sb = new StringBuilder();
        java.util.List<String> tokens = new java.util.ArrayList<>();
        int parenDepth = 1;

        tokens.add(0, ")");
        PsiElement current = closeParen.getPrevSibling();

        // Collect tokens back to opening paren
        while (current != null && parenDepth > 0) {
            IElementType type = current.getNode() != null ? current.getNode().getElementType() : null;
            if (type == KiteTokenTypes.RPAREN) {
                parenDepth++;
                tokens.add(0, ")");
            } else if (type == KiteTokenTypes.LPAREN) {
                parenDepth--;
                tokens.add(0, "(");
            } else if (!isWhitespaceElement(current)) {
                tokens.add(0, current.getText());
            }
            current = current.getPrevSibling();
        }

        if (parenDepth != 0) return null;

        // Get identifier
        while (current != null && isWhitespaceElement(current)) {
            current = current.getPrevSibling();
        }
        if (current == null) return null;
        IElementType type = current.getNode() != null ? current.getNode().getElementType() : null;
        if (type != KiteTokenTypes.IDENTIFIER) return null;
        tokens.add(0, current.getText());

        // Get @
        current = current.getPrevSibling();
        while (current != null && isWhitespaceElement(current)) {
            current = current.getPrevSibling();
        }
        if (current == null || current.getNode() == null ||
            current.getNode().getElementType() != KiteTokenTypes.AT) {
            return null;
        }
        tokens.add(0, "@");

        // Build the decorator string
        for (String token : tokens) {
            sb.append(token);
        }

        return sb.toString();
    }

    /**
     * Check if an element is whitespace.
     */
    private boolean isWhitespaceElement(PsiElement element) {
        if (element instanceof PsiWhiteSpace) {
            return true;
        }
        if (element.getNode() != null) {
            IElementType type = element.getNode().getElementType();
            return type == KiteTokenTypes.WHITESPACE ||
                   type == KiteTokenTypes.NL ||
                   type == KiteTokenTypes.NEWLINE ||
                   type == com.intellij.psi.TokenType.WHITE_SPACE;
        }
        return false;
    }

    /**
     * Clean up block comment content by removing leading asterisks and normalizing indentation.
     */
    @NotNull
    private String cleanBlockCommentContent(String content) {
        String[] lines = content.split("\n");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            // Remove leading asterisks (common in multi-line comments)
            if (line.startsWith("*")) {
                line = line.substring(1).trim();
            }
            if (!line.isEmpty()) {
                if (result.length() > 0) {
                    result.append("\n");
                }
                result.append(line);
            }
        }

        return result.toString();
    }

    /**
     * Get type-specific additional information.
     */
    @Nullable
    private String getTypeSpecificInfo(PsiElement declaration, IElementType type) {
        StringBuilder sb = new StringBuilder();

        if (type == KiteElementTypes.RESOURCE_DECLARATION) {
            // Extract resource type
            String resourceType = extractResourceType(declaration);
            if (resourceType != null) {
                sb.append("<div style=\"margin-bottom: 4px;\">");
                sb.append("<span>Resource Type:</span> ");
                sb.append("<code>").append(escapeHtml(resourceType)).append("</code>");
                sb.append("</div>");
            }
        } else if (type == KiteElementTypes.COMPONENT_DECLARATION) {
            // Extract component type
            String componentType = extractComponentType(declaration);
            if (componentType != null) {
                sb.append("<div style=\"margin-bottom: 4px;\">");
                sb.append("<span>Component Type:</span> ");
                sb.append("<code>").append(escapeHtml(componentType)).append("</code>");
                sb.append("</div>");
            }

            // Extract inputs
            java.util.List<String[]> inputs = extractComponentMembersWithParts(declaration, KiteElementTypes.INPUT_DECLARATION);
            if (!inputs.isEmpty()) {
                sb.append("<div style=\"margin-bottom: 8px; background-color: " + getSectionBackgroundColor() + "; padding: 8px; border-radius: 4px;\">");
                sb.append("<span>Inputs:</span>");
                sb.append("<pre style=\"margin: 4px 0 0 0; padding: 0; font-family: monospace; background: transparent;\">");
                sb.append(formatAlignedMembersPlain(inputs));
                sb.append("</pre>");
                sb.append("</div>");
            }

            // Extract outputs
            java.util.List<String[]> outputs = extractComponentMembersWithParts(declaration, KiteElementTypes.OUTPUT_DECLARATION);
            if (!outputs.isEmpty()) {
                sb.append("<div style=\"margin-bottom: 8px; background-color: " + getSectionBackgroundColor() + "; padding: 8px; border-radius: 4px;\">");
                sb.append("<span>Outputs:</span>");
                sb.append("<pre style=\"margin: 4px 0 0 0; padding: 0; font-family: monospace; background: transparent;\">");
                sb.append(formatAlignedMembersPlain(outputs));
                sb.append("</pre>");
                sb.append("</div>");
            }
        } else if (type == KiteElementTypes.VARIABLE_DECLARATION ||
                   type == KiteElementTypes.INPUT_DECLARATION ||
                   type == KiteElementTypes.OUTPUT_DECLARATION) {
            // Extract variable type
            String varType = extractVariableType(declaration);
            if (varType != null) {
                sb.append("<div style=\"margin-bottom: 4px;\">");
                sb.append("<span>Type:</span> ");
                sb.append("<code>").append(escapeHtml(varType)).append("</code>");
                sb.append("</div>");
            }

            // Extract default value
            String defaultValue = extractDefaultValue(declaration);
            if (defaultValue != null) {
                sb.append("<div style=\"margin-bottom: 4px;\">");
                sb.append("<span>Default:</span> ");
                sb.append("<code>").append(colorizeCode(defaultValue)).append("</code>");
                sb.append("</div>");
            }
        } else if (type == KiteElementTypes.FUNCTION_DECLARATION) {
            // Extract parameters
            String params = extractFunctionParams(declaration);
            if (params != null) {
                sb.append("<div style=\"margin-bottom: 4px;\">");
                sb.append("<span>Parameters:</span> ");
                sb.append("<code>").append(escapeHtml(params)).append("</code>");
                sb.append("</div>");
            }
        }

        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * Extract resource type from a resource declaration.
     * e.g., "resource VM.Instance server { }" -> "VM.Instance"
     *
     * Pattern: resource <type> <name> { ... }
     * where <type> can be dotted like VM.Instance
     */
    @Nullable
    private String extractResourceType(PsiElement declaration) {
        StringBuilder typeBuilder = new StringBuilder();
        boolean foundResource = false;
        boolean lastWasDot = false;

        PsiElement child = declaration.getFirstChild();
        while (child != null) {
            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.RESOURCE) {
                foundResource = true;
            } else if (foundResource) {
                if (childType == KiteTokenTypes.IDENTIFIER) {
                    if (typeBuilder.length() > 0 && !lastWasDot) {
                        // Previous was identifier without dot between - this is the name, stop
                        break;
                    }
                    typeBuilder.append(child.getText());
                    lastWasDot = false;
                } else if (childType == KiteTokenTypes.DOT) {
                    typeBuilder.append(".");
                    lastWasDot = true;
                } else if (childType == KiteTokenTypes.WHITESPACE ||
                           childType == com.intellij.psi.TokenType.WHITE_SPACE) {
                    // Whitespace after identifier without dot means next identifier is the name
                    // But only if we already have some type content
                    if (typeBuilder.length() > 0 && !lastWasDot) {
                        // Look ahead to see if there's a name identifier after whitespace
                        PsiElement next = child.getNextSibling();
                        while (next != null && (next.getNode().getElementType() == KiteTokenTypes.WHITESPACE ||
                                                next.getNode().getElementType() == com.intellij.psi.TokenType.WHITE_SPACE)) {
                            next = next.getNextSibling();
                        }
                        if (next != null && next.getNode().getElementType() == KiteTokenTypes.IDENTIFIER) {
                            // Next identifier after whitespace is the name - stop here
                            break;
                        }
                    }
                } else if (childType == KiteTokenTypes.LBRACE) {
                    // Hit the opening brace, stop
                    break;
                }
            }

            child = child.getNextSibling();
        }

        return typeBuilder.length() > 0 ? typeBuilder.toString() : null;
    }

    /**
     * Extract component type from a component declaration.
     */
    @Nullable
    private String extractComponentType(PsiElement declaration) {
        boolean foundComponent = false;

        PsiElement child = declaration.getFirstChild();
        while (child != null) {
            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.COMPONENT) {
                foundComponent = true;
            } else if (foundComponent && childType == KiteTokenTypes.IDENTIFIER) {
                return child.getText();
            }

            child = child.getNextSibling();
        }

        return null;
    }

    /**
     * Extract variable type from a var/input/output declaration.
     * e.g., "input number port = 8080" -> "number"
     */
    @Nullable
    private String extractVariableType(PsiElement declaration) {
        boolean foundKeyword = false;

        PsiElement child = declaration.getFirstChild();
        while (child != null) {
            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.VAR ||
                childType == KiteTokenTypes.INPUT ||
                childType == KiteTokenTypes.OUTPUT) {
                foundKeyword = true;
            } else if (foundKeyword && childType == KiteTokenTypes.IDENTIFIER) {
                // First identifier after keyword is the type
                return child.getText();
            }

            child = child.getNextSibling();
        }

        return null;
    }

    /**
     * Extract default value from a declaration.
     * Object and array literals are replaced with {...} and [...] placeholders to avoid wrapping.
     */
    @Nullable
    private String extractDefaultValue(PsiElement declaration) {
        boolean foundAssign = false;
        StringBuilder value = new StringBuilder();
        int braceDepth = 0;   // Track nested object literals
        int bracketDepth = 0; // Track nested array literals

        PsiElement child = declaration.getFirstChild();
        while (child != null) {
            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.ASSIGN) {
                foundAssign = true;
            } else if (foundAssign) {
                // Handle object literals - replace with {...}
                if (childType == KiteTokenTypes.LBRACE) {
                    braceDepth++;
                    if (braceDepth == 1) {
                        value.append("{...}");
                    }
                } else if (childType == KiteTokenTypes.RBRACE) {
                    braceDepth--;
                }
                // Handle array literals - replace with [...]
                else if (childType == KiteTokenTypes.LBRACK) {
                    bracketDepth++;
                    if (bracketDepth == 1) {
                        value.append("[...]");
                    }
                } else if (childType == KiteTokenTypes.RBRACK) {
                    bracketDepth--;
                }
                // Only collect tokens when not inside a literal
                else if (braceDepth == 0 && bracketDepth == 0 &&
                         childType != KiteTokenTypes.WHITESPACE &&
                         childType != com.intellij.psi.TokenType.WHITE_SPACE) {
                    String text = child.getText().trim();
                    if (!text.isEmpty()) {
                        // Don't add space before/after certain characters:
                        // - quotes (handles split string tokens)
                        // - dots (handles property access chains like server.tag.New)
                        boolean isQuote = text.equals("\"") || text.equals("'");
                        boolean isDot = text.equals(".");
                        boolean lastWasQuote = value.length() > 0 &&
                            (value.charAt(value.length() - 1) == '"' || value.charAt(value.length() - 1) == '\'');
                        boolean lastWasDot = value.length() > 0 && value.charAt(value.length() - 1) == '.';

                        if (value.length() > 0 && !isQuote && !lastWasQuote && !isDot && !lastWasDot) {
                            value.append(" ");
                        }
                        value.append(text);
                    }
                }
            }

            child = child.getNextSibling();
        }

        String result = value.toString().trim();
        // Truncate if too long
        if (result.length() > 50) {
            result = result.substring(0, 47) + "...";
        }
        return result.isEmpty() ? null : result;
    }

    /**
     * Extract function parameters from a function declaration.
     */
    @Nullable
    private String extractFunctionParams(PsiElement declaration) {
        String text = declaration.getText();
        int parenStart = text.indexOf('(');
        int parenEnd = text.indexOf(')');

        if (parenStart > 0 && parenEnd > parenStart) {
            return text.substring(parenStart + 1, parenEnd).trim();
        }

        return null;
    }

    /**
     * Extract component members (inputs or outputs) from a component declaration.
     * Returns a list of String arrays: [0]=type, [1]=name, [2]=defaultValue (or null)
     */
    @NotNull
    private java.util.List<String[]> extractComponentMembersWithParts(PsiElement declaration, IElementType memberType) {
        java.util.List<String[]> members = new java.util.ArrayList<>();
        extractMembersWithPartsRecursive(declaration, memberType, members);
        return members;
    }

    private void extractMembersWithPartsRecursive(PsiElement element, IElementType memberType, java.util.List<String[]> members) {
        if (element.getNode() == null) {
            return;
        }

        IElementType type = element.getNode().getElementType();

        if (type == memberType) {
            // Format the member declaration: returns [type, name, defaultValue]
            String[] parts = formatMemberDeclarationParts(element, memberType);
            if (parts != null) {
                members.add(parts);
            }
            return; // Don't recurse into found members
        }

        // Recurse into children
        PsiElement child = element.getFirstChild();
        while (child != null) {
            extractMembersWithPartsRecursive(child, memberType, members);
            child = child.getNextSibling();
        }
    }

    /**
     * Format a member declaration into parts for alignment.
     * e.g., "input number port = 8080" -> ["number", "port", "8080"]
     * @return String array: [0]=type, [1]=name, [2]=defaultValue (or null if no default)
     */
    @Nullable
    private String[] formatMemberDeclarationParts(PsiElement member, IElementType memberType) {
        boolean foundKeyword = false;
        boolean foundType = false;
        String varType = null;
        String varName = null;
        StringBuilder defaultValueBuilder = new StringBuilder();
        boolean foundAssign = false;
        int braceDepth = 0;  // Track nested object literals

        PsiElement child = member.getFirstChild();
        while (child != null) {
            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.INPUT || childType == KiteTokenTypes.OUTPUT) {
                foundKeyword = true;
            } else if (foundKeyword && !foundType && childType == KiteTokenTypes.ANY) {
                // Handle 'any' keyword as a type
                varType = "any";
                foundType = true;
            } else if (foundKeyword && !foundType && childType == KiteTokenTypes.IDENTIFIER) {
                // First identifier after keyword is the type
                varType = child.getText();
                foundType = true;
            } else if (foundType && varName == null && childType == KiteTokenTypes.IDENTIFIER) {
                // Second identifier is the name
                varName = child.getText();
            } else if (childType == KiteTokenTypes.ASSIGN) {
                foundAssign = true;
            } else if (foundAssign) {
                // Track brace depth for object literals
                if (childType == KiteTokenTypes.LBRACE) {
                    braceDepth++;
                    // For object literals, just show "{...}" placeholder
                    if (braceDepth == 1) {
                        defaultValueBuilder.append("{...}");
                    }
                } else if (childType == KiteTokenTypes.RBRACE) {
                    braceDepth--;
                } else if (braceDepth == 0 &&
                           childType != KiteTokenTypes.WHITESPACE &&
                           childType != com.intellij.psi.TokenType.WHITE_SPACE) {
                    // Only collect tokens when not inside an object literal
                    defaultValueBuilder.append(child.getText());
                }
            }

            child = child.getNextSibling();
        }

        if (varType != null && varName != null) {
            String defaultValue = defaultValueBuilder.toString().trim();
            if (defaultValue.isEmpty()) {
                defaultValue = null;
            } else if (defaultValue.length() > 30) {
                // Truncate long default values
                defaultValue = defaultValue.substring(0, 27) + "...";
            }
            return new String[] { varType, varName, defaultValue };
        }

        return null;
    }

    /**
     * Format members as plain text with vertical alignment at '='.
     * Uses regular spaces for alignment (for use inside <pre> tags).
     * Applies syntax highlighting colors to types and values.
     */
    @NotNull
    private String formatAlignedMembersPlain(java.util.List<String[]> members) {
        if (members.isEmpty()) {
            return "";
        }

        // Find max length of "type name" portion (before '=')
        int maxLeftLength = 0;
        for (String[] parts : members) {
            int leftLength = parts[0].length() + 1 + parts[1].length(); // "type name"
            if (leftLength > maxLeftLength) {
                maxLeftLength = leftLength;
            }
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < members.size(); i++) {
            if (i > 0) {
                result.append("\n");
            }

            String[] parts = members.get(i);
            String typeName = parts[0];
            String varName = parts[1];

            // Colorize the type name if it's a known type
            if (TYPE_NAMES.contains(typeName)) {
                result.append("<span style=\"color: ").append(COLOR_TYPE).append(";\">")
                      .append(escapeHtmlNoBreaks(typeName)).append("</span>");
            } else {
                result.append(escapeHtmlNoBreaks(typeName));
            }
            result.append(" ").append(escapeHtmlNoBreaks(varName));

            if (parts[2] != null) {
                // Add padding to align '='
                String left = typeName + " " + varName;
                int padding = maxLeftLength - left.length();
                for (int p = 0; p < padding; p++) {
                    result.append(" ");
                }
                result.append(" = ").append(colorizeCodeNoBreaks(parts[2]));
            }
        }

        return result.toString();
    }

    /**
     * Format members with vertical alignment at '='.
     * Uses HTML non-breaking spaces (&nbsp;) for alignment.
     * Applies syntax highlighting colors to types and values.
     */
    @NotNull
    private String formatAlignedMembers(java.util.List<String[]> members) {
        if (members.isEmpty()) {
            return "";
        }

        // Find max length of "type name" portion (before '=')
        int maxLeftLength = 0;
        for (String[] parts : members) {
            int leftLength = parts[0].length() + 1 + parts[1].length(); // "type name"
            if (leftLength > maxLeftLength) {
                maxLeftLength = leftLength;
            }
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < members.size(); i++) {
            if (i > 0) {
                result.append("<br/>");
            }

            String[] parts = members.get(i);
            String typeName = parts[0];
            String varName = parts[1];

            // Colorize the type name if it's a known type
            if (TYPE_NAMES.contains(typeName)) {
                result.append("<span style=\"color: ").append(COLOR_TYPE).append(";\">")
                      .append(escapeHtml(typeName)).append("</span>");
            } else {
                result.append(escapeHtml(typeName));
            }
            result.append(" ").append(escapeHtml(varName));

            if (parts[2] != null) {
                // Add padding to align '='
                String left = typeName + " " + varName;
                int padding = maxLeftLength - left.length();
                for (int p = 0; p < padding; p++) {
                    result.append("&nbsp;");
                }
                result.append(" = ").append(colorizeCode(parts[2]));
            }
        }

        return result.toString();
    }

    /**
     * Escape HTML special characters.
     */
    @NotNull
    private String escapeHtml(String text) {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("\n", "<br/>");
    }

    /**
     * Escape HTML special characters without converting newlines.
     * For use inside <pre> tags where newlines should be preserved as-is.
     */
    @NotNull
    private String escapeHtmlNoBreaks(String text) {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    // CSS colors for syntax highlighting in documentation (matching editor colors)
    private static final String COLOR_KEYWORD = "#AB5FDB";   // Purple - keywords (matches KiteSyntaxHighlighter.KEYWORD)
    private static final String COLOR_TYPE = "#498BF6";      // Blue - type names (matches KiteAnnotator.TYPE_NAME)
    private static final String COLOR_STRING = "#6A9955";    // Green - string literals (matches KiteSyntaxHighlighter.STRING)
    private static final String COLOR_NUMBER = "#6897BB";    // Blue - number literals (matches IntelliJ Darcula)

    // Keywords that should be colored
    private static final java.util.Set<String> KEYWORDS = new java.util.HashSet<>(java.util.Arrays.asList(
        "resource", "component", "schema", "fun", "var", "input", "output", "type",
        "if", "else", "for", "while", "in", "return", "import", "from", "init", "this",
        "true", "false", "null"
    ));

    // Built-in type names
    private static final java.util.Set<String> TYPE_NAMES = new java.util.HashSet<>(java.util.Arrays.asList(
        "string", "number", "boolean", "object", "any", "void", "list", "map"
    ));

    /**
     * Colorize code text with syntax highlighting (no newline conversion).
     * For use inside <pre> tags where newlines should be preserved.
     */
    @NotNull
    private String colorizeCodeNoBreaks(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        int i = 0;
        int len = text.length();

        while (i < len) {
            char c = text.charAt(i);

            // Handle string literals (quoted strings)
            if (c == '"') {
                int start = i;
                i++; // skip opening quote
                while (i < len && text.charAt(i) != '"') {
                    if (text.charAt(i) == '\\' && i + 1 < len) {
                        i++; // skip escaped char
                    }
                    i++;
                }
                if (i < len) i++; // skip closing quote
                String str = escapeHtmlNoBreaks(text.substring(start, i));
                result.append("<span style=\"color: ").append(COLOR_STRING).append(";\">").append(str).append("</span>");
                continue;
            }

            // Handle identifiers and keywords
            if (Character.isLetter(c) || c == '_') {
                int start = i;
                while (i < len && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_')) {
                    i++;
                }
                String word = text.substring(start, i);

                if (KEYWORDS.contains(word)) {
                    result.append("<span style=\"color: ").append(COLOR_KEYWORD).append("; font-weight: bold;\">")
                          .append(escapeHtmlNoBreaks(word)).append("</span>");
                } else if (TYPE_NAMES.contains(word)) {
                    result.append("<span style=\"color: ").append(COLOR_TYPE).append(";\">")
                          .append(escapeHtmlNoBreaks(word)).append("</span>");
                } else {
                    result.append(escapeHtmlNoBreaks(word));
                }
                continue;
            }

            // Handle numbers
            if (Character.isDigit(c)) {
                int start = i;
                while (i < len && (Character.isDigit(text.charAt(i)) || text.charAt(i) == '.')) {
                    i++;
                }
                String num = text.substring(start, i);
                result.append("<span style=\"color: ").append(COLOR_NUMBER).append(";\">")
                      .append(escapeHtmlNoBreaks(num)).append("</span>");
                continue;
            }

            // Other characters - just escape and append
            result.append(escapeHtmlNoBreaks(String.valueOf(c)));
            i++;
        }

        return result.toString();
    }

    /**
     * Colorize code text with syntax highlighting.
     * This applies coloring to keywords, types, strings, and numbers.
     */
    @NotNull
    private String colorizeCode(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        int i = 0;
        int len = text.length();

        while (i < len) {
            char c = text.charAt(i);

            // Handle string literals (quoted strings)
            if (c == '"') {
                int start = i;
                i++; // skip opening quote
                while (i < len && text.charAt(i) != '"') {
                    if (text.charAt(i) == '\\' && i + 1 < len) {
                        i++; // skip escaped char
                    }
                    i++;
                }
                if (i < len) i++; // skip closing quote
                String str = escapeHtml(text.substring(start, i));
                result.append("<span style=\"color: ").append(COLOR_STRING).append(";\">").append(str).append("</span>");
                continue;
            }

            // Handle identifiers and keywords
            if (Character.isLetter(c) || c == '_') {
                int start = i;
                while (i < len && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_')) {
                    i++;
                }
                String word = text.substring(start, i);

                if (KEYWORDS.contains(word)) {
                    result.append("<span style=\"color: ").append(COLOR_KEYWORD).append("; font-weight: bold;\">")
                          .append(escapeHtml(word)).append("</span>");
                } else if (TYPE_NAMES.contains(word)) {
                    result.append("<span style=\"color: ").append(COLOR_TYPE).append(";\">")
                          .append(escapeHtml(word)).append("</span>");
                } else {
                    result.append(escapeHtml(word));
                }
                continue;
            }

            // Handle numbers
            if (Character.isDigit(c)) {
                int start = i;
                while (i < len && (Character.isDigit(text.charAt(i)) || text.charAt(i) == '.')) {
                    i++;
                }
                String num = text.substring(start, i);
                result.append("<span style=\"color: ").append(COLOR_NUMBER).append(";\">")
                      .append(escapeHtml(num)).append("</span>");
                continue;
            }

            // Other characters - just escape and append
            result.append(escapeHtml(String.valueOf(c)));
            i++;
        }

        return result.toString();
    }

    /**
     * Colorize decorator text with syntax highlighting.
     * The @ symbol and decorator name get special coloring, arguments get colorized like code.
     */
    @NotNull
    private String colorizeDecoratorNoBreaks(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        int i = 0;
        int len = text.length();

        // Handle @ symbol
        if (i < len && text.charAt(i) == '@') {
            result.append("<span style=\"color: ").append(COLOR_DECORATOR).append(";\">@</span>");
            i++;
        }

        // Handle decorator name (identifier after @)
        if (i < len && (Character.isLetter(text.charAt(i)) || text.charAt(i) == '_')) {
            int start = i;
            while (i < len && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_')) {
                i++;
            }
            String decoratorName = text.substring(start, i);
            result.append("<span style=\"color: ").append(COLOR_DECORATOR).append(";\">")
                  .append(escapeHtmlNoBreaks(decoratorName)).append("</span>");
        }

        // Handle arguments (everything after decorator name) - colorize like code
        if (i < len) {
            String args = text.substring(i);
            result.append(colorizeCodeNoBreaks(args));
        }

        return result.toString();
    }

    // Decorator color - orange to match editor
    private static final String COLOR_DECORATOR = "#CC7832";

    /**
     * Gets a theme-aware section background color.
     * In light theme: slightly darker than background (like a subtle card)
     * In dark theme: slightly lighter than background (like a subtle card)
     * @return hex color string like "#e8e8e8" for light theme or "#3c3f41" for dark theme
     */
    private static String getSectionBackgroundColor() {
        // JBColor automatically picks the right color based on current theme
        // First value is for light theme, second is for dark theme
        Color bgColor = new JBColor(
            new Color(0xe8, 0xe8, 0xe8),  // Light theme: light gray
            new Color(0x3c, 0x3f, 0x41)   // Dark theme: slightly lighter than default dark bg
        );
        return String.format("#%02x%02x%02x", bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue());
    }
}
