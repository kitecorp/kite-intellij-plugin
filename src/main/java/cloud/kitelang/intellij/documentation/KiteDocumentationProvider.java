package cloud.kitelang.intellij.documentation;

import cloud.kitelang.intellij.KiteLanguage;
import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static cloud.kitelang.intellij.documentation.KiteDecoratorDocumentation.*;
import static cloud.kitelang.intellij.documentation.KiteDocumentationExtractor.*;
import static cloud.kitelang.intellij.documentation.KiteDocumentationHtmlHelper.*;

/**
 * Documentation provider for Kite language.
 * <p>
 * Shows quick documentation popup when pressing Ctrl+Q (or F1 on Mac) on declarations.
 * <p>
 * <b>Supported Documentation:</b>
 * <ul>
 *   <li>Variables, inputs, outputs - shows type, default value, decorators</li>
 *   <li>Resources - shows resource type and decorators</li>
 *   <li>Components - shows inputs and outputs with types and defaults</li>
 *   <li>Schemas - shows schema name</li>
 *   <li>Functions - shows parameters and return type</li>
 *   <li>Decorators - shows detailed decorator documentation</li>
 * </ul>
 * <p>
 * <b>Features:</b>
 * <ul>
 *   <li>Syntax highlighting in code snippets</li>
 *   <li>Theme-aware background colors</li>
 *   <li>Aligned formatting for inputs/outputs</li>
 *   <li>Preceding comment extraction</li>
 * </ul>
 *
 * @see KiteDecoratorDocumentation for decorator documentation
 * @see KiteDocumentationExtractor for PSI extraction utilities
 * @see KiteDocumentationHtmlHelper for HTML formatting
 */
public class KiteDocumentationProvider extends AbstractDocumentationProvider {

    @Override
    public @Nullable PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
        // Handle decorator lookup items - create a unique fake element for each decorator
        if (object instanceof DecoratorLookupItem decoratorItem) {
            return new DecoratorDocElement(decoratorItem.name(), element);
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
            String doc = getDecoratorDocumentation(decoratorName);
            if (doc != null) {
                return doc;
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
            while (isWhitespaceElement(next)) {
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
     * Generate HTML documentation for a declaration.
     */
    @NotNull
    private String generateDocumentation(PsiElement declaration) {
        StringBuilder sb = new StringBuilder();
        IElementType type = declaration.getNode().getElementType();

        String kind = getDeclarationKind(type);
        String name = getDeclarationName(declaration, type);
        String signature = getSignature(declaration);
        String comment = getPrecedingComment(declaration);

        // Wrapper div with horizontal scroll
        sb.append("<div style=\"white-space: nowrap; overflow-x: auto; max-width: 800px;\">");

        // Header: kind and name
        sb.append("<div style=\"margin-bottom: 8px;\">");
        sb.append("<b>").append(kind).append("</b>");
        if (name != null) {
            sb.append(" ").append(name);
        }
        sb.append("</div>");

        // Signature
        if (signature != null && !signature.isEmpty()) {
            sb.append("<div style=\"margin-bottom: 4px;\">");
            sb.append("<span>Declaration:</span> ");
            sb.append("<code>").append(colorizeCode(signature)).append("</code>");
            sb.append("</div>");
        }

        // Type-specific information
        String typeInfo = getTypeSpecificInfo(declaration, type);
        if (typeInfo != null && !typeInfo.isEmpty()) {
            sb.append(typeInfo);
        }

        // Decorators section
        List<String> decorators = extractDecorators(declaration);
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

        // Comment section
        if (comment != null && !comment.isEmpty()) {
            sb.append("<div style=\"margin-top: 8px;margin-bottom: 8px;\">");
            sb.append(escapeHtml(comment));
            sb.append("</div>");
        }

        sb.append("</div>");

        return sb.toString();
    }

    /**
     * Get type-specific additional information.
     */
    @Nullable
    private String getTypeSpecificInfo(PsiElement declaration, IElementType type) {
        StringBuilder sb = new StringBuilder();

        if (type == KiteElementTypes.RESOURCE_DECLARATION) {
            String resourceType = extractResourceType(declaration);
            if (resourceType != null) {
                sb.append("<div style=\"margin-bottom: 4px;\">");
                sb.append("<span>Resource Type:</span> ");
                sb.append("<code>").append(escapeHtml(resourceType)).append("</code>");
                sb.append("</div>");
            }
        } else if (type == KiteElementTypes.COMPONENT_DECLARATION) {
            String componentType = extractComponentType(declaration);
            if (componentType != null) {
                sb.append("<div style=\"margin-bottom: 4px;\">");
                sb.append("<span>Component Type:</span> ");
                sb.append("<code>").append(escapeHtml(componentType)).append("</code>");
                sb.append("</div>");
            }

            // Extract inputs
            List<String[]> inputs = extractComponentMembersWithParts(declaration, KiteElementTypes.INPUT_DECLARATION);
            if (!inputs.isEmpty()) {
                sb.append("<div style=\"margin-bottom: 8px; background-color: ").append(getSectionBackgroundColor()).append("; padding: 8px; border-radius: 4px;\">");
                sb.append("<span>Inputs:</span>");
                sb.append("<pre style=\"margin: 4px 0 0 0; padding: 0; font-family: monospace; background: transparent;\">");
                sb.append(formatAlignedMembersPlain(inputs));
                sb.append("</pre>");
                sb.append("</div>");
            }

            // Extract outputs
            List<String[]> outputs = extractComponentMembersWithParts(declaration, KiteElementTypes.OUTPUT_DECLARATION);
            if (!outputs.isEmpty()) {
                sb.append("<div style=\"margin-bottom: 8px; background-color: ").append(getSectionBackgroundColor()).append("; padding: 8px; border-radius: 4px;\">");
                sb.append("<span>Outputs:</span>");
                sb.append("<pre style=\"margin: 4px 0 0 0; padding: 0; font-family: monospace; background: transparent;\">");
                sb.append(formatAlignedMembersPlain(outputs));
                sb.append("</pre>");
                sb.append("</div>");
            }
        } else if (type == KiteElementTypes.VARIABLE_DECLARATION ||
                   type == KiteElementTypes.INPUT_DECLARATION ||
                   type == KiteElementTypes.OUTPUT_DECLARATION) {
            String varType = extractVariableType(declaration);
            if (varType != null) {
                sb.append("<div style=\"margin-bottom: 4px;\">");
                sb.append("<span>Type:</span> ");
                sb.append("<code>").append(escapeHtml(varType)).append("</code>");
                sb.append("</div>");
            }

            String defaultValue = extractDefaultValue(declaration);
            if (defaultValue != null) {
                sb.append("<div style=\"margin-bottom: 4px;\">");
                sb.append("<span>Default:</span> ");
                sb.append("<code>").append(colorizeCode(defaultValue)).append("</code>");
                sb.append("</div>");
            }
        } else if (type == KiteElementTypes.FUNCTION_DECLARATION) {
            String params = extractFunctionParams(declaration);
            if (params != null) {
                sb.append("<div style=\"margin-bottom: 4px;\">");
                sb.append("<span>Parameters:</span> ");
                sb.append("<code>").append(escapeHtml(params)).append("</code>");
                sb.append("</div>");
            }
        }

        return !sb.isEmpty() ? sb.toString() : null;
    }
}
