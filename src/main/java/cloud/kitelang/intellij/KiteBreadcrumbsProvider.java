package cloud.kitelang.intellij;

import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Breadcrumbs provider for Kite language.
 * Shows the navigation path at the top/bottom of the editor (e.g., component > resource > property).
 */
public class KiteBreadcrumbsProvider implements BreadcrumbsProvider {

    @Override
    public Language[] getLanguages() {
        return new Language[]{KiteLanguage.INSTANCE};
    }

    @Override
    public boolean acceptElement(@NotNull PsiElement element) {
        IElementType elementType = element.getNode().getElementType();

        // Accept major structural elements for breadcrumbs
        return elementType == KiteElementTypes.COMPONENT_DECLARATION ||
               elementType == KiteElementTypes.RESOURCE_DECLARATION ||
               elementType == KiteElementTypes.SCHEMA_DECLARATION ||
               elementType == KiteElementTypes.FUNCTION_DECLARATION ||
               elementType == KiteElementTypes.TYPE_DECLARATION ||
               elementType == KiteElementTypes.VARIABLE_DECLARATION ||
               elementType == KiteElementTypes.INPUT_DECLARATION ||
               elementType == KiteElementTypes.OUTPUT_DECLARATION ||
               elementType == KiteElementTypes.FOR_STATEMENT ||
               elementType == KiteElementTypes.WHILE_STATEMENT ||
               elementType == KiteElementTypes.OBJECT_LITERAL;
    }

    @NotNull
    @Override
    public String getElementInfo(@NotNull PsiElement element) {
        IElementType elementType = element.getNode().getElementType();

        // Extract the declaration name from the element's text
        String text = element.getText();
        if (text == null || text.isEmpty()) {
            return getDefaultLabel(elementType);
        }

        // Get first line and clean it up
        String firstLine = text.split("\n")[0].trim();

        // Remove trailing brace
        if (firstLine.endsWith("{")) {
            firstLine = firstLine.substring(0, firstLine.length() - 1).trim();
        }

        // Parse based on element type
        if (elementType == KiteElementTypes.COMPONENT_DECLARATION) {
            // Format: "component TypeName instanceName" -> "TypeName instanceName" or just "TypeName"
            return extractComponentName(firstLine);
        } else if (elementType == KiteElementTypes.RESOURCE_DECLARATION) {
            // Format: "resource Type.Name instanceName" -> "instanceName"
            return extractResourceName(firstLine);
        } else if (elementType == KiteElementTypes.SCHEMA_DECLARATION) {
            // Format: "schema SchemaName" -> "SchemaName"
            return extractAfterKeyword(firstLine, "schema");
        } else if (elementType == KiteElementTypes.FUNCTION_DECLARATION) {
            // Format: "fun functionName(...)" -> "functionName()"
            return extractFunctionName(firstLine);
        } else if (elementType == KiteElementTypes.TYPE_DECLARATION) {
            // Format: "type TypeName = ..." -> "TypeName"
            return extractAfterKeyword(firstLine, "type");
        } else if (elementType == KiteElementTypes.VARIABLE_DECLARATION) {
            // Format: "var [type] varName = ..." -> "varName"
            return extractDeclarationName(firstLine, "var");
        } else if (elementType == KiteElementTypes.INPUT_DECLARATION) {
            // Format: "input [type] inputName = ..." -> "inputName"
            return extractDeclarationName(firstLine, "input");
        } else if (elementType == KiteElementTypes.OUTPUT_DECLARATION) {
            // Format: "output [type] outputName = ..." -> "outputName"
            return extractDeclarationName(firstLine, "output");
        } else if (elementType == KiteElementTypes.FOR_STATEMENT) {
            // Format: "for item in collection" -> "for item"
            return extractForInfo(firstLine);
        } else if (elementType == KiteElementTypes.WHILE_STATEMENT) {
            // Format: "while condition" -> "while"
            return "while";
        } else if (elementType == KiteElementTypes.OBJECT_LITERAL) {
            // For object literals, try to find the property name from parent context
            return getObjectLiteralLabel(element);
        }

        return getDefaultLabel(elementType);
    }

    @Nullable
    @Override
    public String getElementTooltip(@NotNull PsiElement element) {
        // Show full first line as tooltip
        String text = element.getText();
        if (text == null || text.isEmpty()) {
            return null;
        }

        String firstLine = text.split("\n")[0].trim();
        if (firstLine.endsWith("{")) {
            firstLine = firstLine.substring(0, firstLine.length() - 1).trim();
        }

        // Truncate if too long
        if (firstLine.length() > 100) {
            firstLine = firstLine.substring(0, 100) + "...";
        }

        return firstLine;
    }

    private String getDefaultLabel(IElementType elementType) {
        if (elementType == KiteElementTypes.COMPONENT_DECLARATION) return "component";
        if (elementType == KiteElementTypes.RESOURCE_DECLARATION) return "resource";
        if (elementType == KiteElementTypes.SCHEMA_DECLARATION) return "schema";
        if (elementType == KiteElementTypes.FUNCTION_DECLARATION) return "function";
        if (elementType == KiteElementTypes.TYPE_DECLARATION) return "type";
        if (elementType == KiteElementTypes.VARIABLE_DECLARATION) return "var";
        if (elementType == KiteElementTypes.INPUT_DECLARATION) return "input";
        if (elementType == KiteElementTypes.OUTPUT_DECLARATION) return "output";
        if (elementType == KiteElementTypes.FOR_STATEMENT) return "for";
        if (elementType == KiteElementTypes.WHILE_STATEMENT) return "while";
        if (elementType == KiteElementTypes.OBJECT_LITERAL) return "{...}";
        return "element";
    }

    private String extractComponentName(String line) {
        // "component WebServer api" -> "WebServer api"
        // "component WebServer" -> "WebServer"
        if (line.startsWith("component ")) {
            String rest = line.substring("component ".length()).trim();
            // Remove any trailing content after = or {
            int eqIndex = rest.indexOf('=');
            if (eqIndex > 0) {
                rest = rest.substring(0, eqIndex).trim();
            }
            return rest.isEmpty() ? "component" : rest;
        }
        return "component";
    }

    private String extractResourceName(String line) {
        // "resource VM.Instance server" -> "server"
        // "resource AWS.S3.Bucket myBucket" -> "myBucket"
        if (line.startsWith("resource ")) {
            String rest = line.substring("resource ".length()).trim();
            // Split by spaces, last word is the instance name
            String[] parts = rest.split("\\s+");
            if (parts.length >= 2) {
                return parts[parts.length - 1]; // Return the last part (instance name)
            } else if (parts.length == 1) {
                return parts[0]; // Just the type name
            }
        }
        return "resource";
    }

    private String extractAfterKeyword(String line, String keyword) {
        // "schema DatabaseConfig" -> "DatabaseConfig"
        // "type UserRole = ..." -> "UserRole"
        String prefix = keyword + " ";
        if (line.startsWith(prefix)) {
            String rest = line.substring(prefix.length()).trim();
            // Take only the first word/identifier
            int spaceIndex = rest.indexOf(' ');
            int eqIndex = rest.indexOf('=');
            int endIndex = rest.length();
            if (spaceIndex > 0) endIndex = Math.min(endIndex, spaceIndex);
            if (eqIndex > 0) endIndex = Math.min(endIndex, eqIndex);
            return rest.substring(0, endIndex).trim();
        }
        return keyword;
    }

    private String extractFunctionName(String line) {
        // "fun calculateCost(params)" -> "calculateCost()"
        if (line.startsWith("fun ")) {
            String rest = line.substring("fun ".length()).trim();
            int parenIndex = rest.indexOf('(');
            if (parenIndex > 0) {
                return rest.substring(0, parenIndex) + "()";
            }
            return rest;
        }
        return "function";
    }

    private String extractDeclarationName(String line, String keyword) {
        // "var number port = 8080" -> "port"
        // "input string name = ..." -> "name"
        // "output string endpoint" -> "endpoint"
        String prefix = keyword + " ";
        if (line.startsWith(prefix)) {
            String rest = line.substring(prefix.length()).trim();
            String[] parts = rest.split("\\s+");

            // parts could be: ["number", "port", "=", "8080"] or ["port", "=", "8080"]
            // We want the identifier before '=' or at the end
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].equals("=")) {
                    // The identifier is the part before '='
                    if (i > 0) {
                        return parts[i - 1];
                    }
                    break;
                }
            }

            // No '=' found, so last part is the name (e.g., "input string name")
            // But we need to skip the type
            if (parts.length >= 2 && isTypeName(parts[0])) {
                return parts[1];
            } else if (parts.length >= 1) {
                return parts[0];
            }
        }
        return keyword;
    }

    private boolean isTypeName(String s) {
        // Common type names in Kite
        return s.equals("string") || s.equals("number") || s.equals("boolean") ||
               s.equals("any") || s.equals("object") || s.startsWith("[") ||
               Character.isUpperCase(s.charAt(0)); // Custom types start with uppercase
    }

    private String extractForInfo(String line) {
        // "for item in collection" -> "for item"
        if (line.startsWith("for ")) {
            String rest = line.substring("for ".length()).trim();
            int inIndex = rest.indexOf(" in ");
            if (inIndex > 0) {
                return "for " + rest.substring(0, inIndex).trim();
            }
            return "for " + rest;
        }
        return "for";
    }

    private String getObjectLiteralLabel(PsiElement element) {
        // Try to find the property name this object is assigned to
        // Walk backwards through siblings to find the identifier before the colon/assign

        PsiElement sibling = element.getPrevSibling();
        int sibCount = 0;
        while (sibling != null && sibCount < 20) {
            IElementType sibType = sibling.getNode().getElementType();

            // Skip whitespace and newlines
            // Note: IntelliJ uses TokenType.WHITE_SPACE for whitespace, not our custom WHITESPACE
            if (sibType == KiteTokenTypes.WHITESPACE ||
                sibType == KiteTokenTypes.NL ||
                sibType == TokenType.WHITE_SPACE) {
                sibling = sibling.getPrevSibling();
                sibCount++;
                continue;
            }

            // Found an identifier - this is the property name
            if (sibType == KiteTokenTypes.IDENTIFIER) {
                return sibling.getText();
            }

            // If we hit a colon or assign, continue looking for the identifier
            if (sibType == KiteTokenTypes.COLON || sibType == KiteTokenTypes.ASSIGN) {
                sibling = sibling.getPrevSibling();
                sibCount++;
                continue;
            }

            // If we hit anything else (comma, brace, other tokens), stop searching
            break;
        }
        return "{...}";
    }
}
