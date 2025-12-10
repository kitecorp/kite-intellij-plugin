package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteLanguage;
import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import cloud.kitelang.intellij.util.KitePsiUtil;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Annotator that detects duplicate property names in schemas, resources, and component instances.
 * Shows an error when the same property name is defined multiple times.
 * <p>
 * Example:
 * <pre>
 * schema Config {
 *     string host
 *     number host      // Error: Duplicate property 'host'
 * }
 *
 * resource Config web {
 *     host = "localhost"
 *     host = "127.0.0.1"  // Error: Duplicate property 'host' assignment
 * }
 * </pre>
 */
public class KiteDuplicatePropertyAnnotator implements Annotator {

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element.getNode() == null) return;

        IElementType type = element.getNode().getElementType();

        // Check schemas for duplicate property definitions
        if (type == KiteElementTypes.SCHEMA_DECLARATION) {
            checkSchemaProperties(element, holder);
        }

        // Check resources for duplicate property assignments
        if (type == KiteElementTypes.RESOURCE_DECLARATION) {
            checkBlockAssignments(element, holder, "property");
        }

        // Check component instances for duplicate input assignments
        if (type == KiteElementTypes.COMPONENT_DECLARATION && isComponentInstance(element)) {
            checkBlockAssignments(element, holder, "input");
        }
    }

    /**
     * Check if a component declaration is an instance (has 2 identifiers: TypeName instanceName).
     */
    private boolean isComponentInstance(PsiElement componentDecl) {
        int identifierCount = 0;
        for (PsiElement child = componentDecl.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;
            IElementType type = child.getNode().getElementType();

            if (type == KiteTokenTypes.LBRACE) {
                break;
            }
            if (type == KiteTokenTypes.IDENTIFIER) {
                identifierCount++;
            }
        }
        return identifierCount >= 2;
    }

    /**
     * Check schema for duplicate property definitions.
     * Pattern: [@cloud] type propertyName (e.g., "string host" or "@cloud string arn")
     */
    private void checkSchemaProperties(PsiElement schemaDecl, AnnotationHolder holder) {
        Map<String, List<PsiElement>> properties = new LinkedHashMap<>();
        boolean insideBody = false;
        String currentType = null;
        boolean skipNextIdentifier = false; // For skipping decorator names

        for (PsiElement child = schemaDecl.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;
            IElementType type = child.getNode().getElementType();

            if (type == KiteTokenTypes.LBRACE) {
                insideBody = true;
                continue;
            }
            if (type == KiteTokenTypes.RBRACE) {
                break;
            }

            if (!insideBody) continue;

            if (KitePsiUtil.isWhitespace(type)) continue;

            // Handle decorator: @decoratorName - skip the @ and the following identifier
            if (type == KiteTokenTypes.AT) {
                skipNextIdentifier = true;
                continue;
            }

            // Track type identifier
            if (type == KiteTokenTypes.IDENTIFIER || type == KiteTokenTypes.ANY) {
                if (skipNextIdentifier) {
                    // This is a decorator name, skip it
                    skipNextIdentifier = false;
                    continue;
                }
                if (currentType == null) {
                    // This is the type
                    currentType = child.getText();
                } else {
                    // This is the property name
                    String propName = child.getText();
                    properties.computeIfAbsent(propName, k -> new ArrayList<>()).add(child);
                    currentType = null; // Reset for next property
                }
            }

            // Handle array type suffix []
            if (type == KiteElementTypes.ARRAY_LITERAL) {
                // Type is followed by [], keep currentType set
                continue;
            }

            // Newlines reset the state
            if (type == KiteTokenTypes.NL || type == KiteTokenTypes.NEWLINE) {
                currentType = null;
                skipNextIdentifier = false;
            }
        }

        // Report duplicates
        markDuplicates(properties, holder, "property");
    }

    /**
     * Check block (resource or component instance) for duplicate property/input assignments.
     * Pattern: propertyName = value
     */
    private void checkBlockAssignments(PsiElement blockDecl, AnnotationHolder holder, String propertyType) {
        Map<String, List<PsiElement>> assignments = new LinkedHashMap<>();
        boolean insideBody = false;

        for (PsiElement child = blockDecl.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;
            IElementType type = child.getNode().getElementType();

            if (type == KiteTokenTypes.LBRACE) {
                insideBody = true;
                continue;
            }
            if (type == KiteTokenTypes.RBRACE) {
                break;
            }

            if (!insideBody) continue;

            if (KitePsiUtil.isWhitespace(type)) continue;

            // Look for identifier followed by =
            if (type == KiteTokenTypes.IDENTIFIER) {
                PsiElement next = KitePsiUtil.skipWhitespace(child.getNextSibling());
                if (next != null && next.getNode() != null &&
                    next.getNode().getElementType() == KiteTokenTypes.ASSIGN) {
                    String propName = child.getText();
                    assignments.computeIfAbsent(propName, k -> new ArrayList<>()).add(child);
                }
            }
        }

        // Report duplicates
        markDuplicates(assignments, holder, propertyType);
    }

    /**
     * Mark duplicate properties/assignments with error annotations.
     */
    private void markDuplicates(Map<String, List<PsiElement>> properties, AnnotationHolder holder, String propertyType) {
        for (var entry : properties.entrySet()) {
            List<PsiElement> elements = entry.getValue();
            if (elements.size() > 1) {
                // Mark all but the first as duplicates
                for (int i = 1; i < elements.size(); i++) {
                    holder.newAnnotation(HighlightSeverity.ERROR,
                                    "Duplicate " + propertyType + " '" + entry.getKey() + "'")
                            .range(elements.get(i))
                            .create();
                }
            }
        }
    }
}
