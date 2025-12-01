package cloud.kitelang.intellij.documentation;

import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.FakePsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static cloud.kitelang.intellij.documentation.KiteDocumentationHtmlHelper.*;

/**
 * Documentation data and HTML generation for Kite decorators.
 * <p>
 * This class manages:
 * <ul>
 *   <li>Decorator documentation registry - all built-in decorators and their docs</li>
 *   <li>HTML generation for decorator documentation popups</li>
 *   <li>Lookup items and fake PSI elements for autocomplete documentation</li>
 * </ul>
 * <p>
 * <b>Decorator Categories:</b>
 * <ul>
 *   <li>Validation: @minValue, @maxValue, @minLength, @maxLength, @nonEmpty, @validate, @allowed, @unique</li>
 *   <li>Resource: @existing, @sensitive, @dependsOn, @tags, @provider, @provisionOn</li>
 *   <li>Metadata: @description, @count, @cloud</li>
 * </ul>
 *
 * @see KiteDocumentationProvider for the main documentation provider
 * @see KiteDocumentationHtmlHelper for HTML formatting utilities
 */
public final class KiteDecoratorDocumentation {

    private KiteDecoratorDocumentation() {
        // Utility class
    }

    // Decorator documentation registry
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
     * Get all decorator names.
     *
     * @return Set of all decorator names
     */
    @NotNull
    public static Set<String> getAllDecoratorNames() {
        return Collections.unmodifiableSet(DECORATOR_DOCS.keySet());
    }

    /**
     * Check if a decorator name is known.
     *
     * @param name The decorator name (without @)
     * @return true if the decorator is documented
     */
    public static boolean isKnownDecorator(String name) {
        return DECORATOR_DOCS.containsKey(name);
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
            return generateDecoratorDocumentation(doc);
        }
        return null;
    }

    /**
     * Generate documentation HTML for a decorator.
     * Uses div-based layout with inline styles for IntelliJ's documentation popup.
     */
    @NotNull
    public static String generateDecoratorDocumentation(DecoratorDoc doc) {
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

        // Arguments section with shared background
        sb.append("<div style=\"margin-bottom: 8px; background-color: ").append(getCodeBackgroundColor()).append("; padding: 8px; border-radius: 4px;\">");

        // Argument type
        sb.append("<div style=\"margin-bottom: 4px;\">");
        sb.append("<span style=\"color: #888;\">Type:</span> ");
        sb.append("<code>").append(escapeHtml(doc.argumentType)).append("</code>");
        sb.append("</div>");

        // Targets
        sb.append("<div style=\"margin-bottom: 4px;\">");
        sb.append("<span style=\"color: #888;\">Targets:</span> ");
        sb.append("<code>").append(escapeHtml(doc.targets)).append("</code>");
        sb.append("</div>");

        // Applies to (only if specified)
        if (doc.appliesTo != null) {
            sb.append("<div>");
            sb.append("<span style=\"color: #888;\">Applies to:</span> ");
            sb.append("<code>").append(escapeHtml(doc.appliesTo)).append("</code>");
            sb.append("</div>");
        }

        sb.append("</div>");

        // Example with syntax highlighting
        sb.append("<div style=\"margin-bottom: 8px; background-color: ").append(getCodeBackgroundColor()).append("; padding: 8px; border-radius: 4px;\">");
        sb.append("<span style=\"font-weight: bold;\">Example:</span>");
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
    public static String generateUnknownDecoratorDoc(String name) {
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
    public static String colorizeDecoratorExample(String example) {
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
                result.append(escapeHtml(example.substring(start, i)));
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
                String str = escapeHtml(example.substring(start, i));
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

                if (KEYWORDS.contains(word)) {
                    result.append("<span style=\"color: ").append(COLOR_KEYWORD).append(";\">")
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
                while (i < len && (Character.isDigit(example.charAt(i)) || example.charAt(i) == '.')) {
                    i++;
                }
                String num = example.substring(start, i);
                result.append("<span style=\"color: ").append(COLOR_NUMBER).append(";\">")
                        .append(escapeHtml(num)).append("</span>");
                continue;
            }

            // Handle newlines
            if (c == '\n') {
                result.append("\n");
                i++;
                continue;
            }

            // Other characters - escape and append
            result.append(escapeHtml(String.valueOf(c)));
            i++;
        }

        return result.toString();
    }

    /**
     * Holds documentation for a decorator.
     */
    public static class DecoratorDoc {
        public final String name;
        public final String category;
        public final String description;
        public final String syntax;
        public final String argumentType;  // What type of argument it takes
        public final String targets;       // What declarations it can apply to
        public final String appliesTo;     // What value types it applies to (null if N/A)
        public final String example;

        public DecoratorDoc(String name, String category, String description, String syntax,
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
    public static class DecoratorDocElement extends FakePsiElement {
        private final String decoratorName;
        private final PsiElement parent;

        public DecoratorDocElement(String decoratorName, PsiElement parent) {
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
}
