package cloud.kitelang.intellij.util;

import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

import static cloud.kitelang.intellij.util.KitePsiUtil.skipWhitespaceForward;

/**
 * Helper class for property-related operations.
 * Provides methods to collect and analyze properties in declarations and object literals.
 */
public final class KitePropertyHelper {

    private KitePropertyHelper() {
        // Utility class - no instances
    }

    /**
     * Visitor interface for iterating over properties.
     */
    @FunctionalInterface
    public interface PropertyVisitor {
        void visit(String name, PsiElement element);
    }

    /**
     * Visitor interface for iterating over properties with their value elements.
     */
    @FunctionalInterface
    public interface PropertyValueVisitor {
        void visit(String name, PsiElement valueElement);
    }

    /**
     * Collect the names of properties already defined in a resource block.
     */
    public static Set<String> collectExistingPropertyNames(PsiElement resourceDecl) {
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
     * Collect only direct properties from inside a declaration's braces (not nested).
     * For server., should show "size" and "tag" but NOT "Environment", "Name", etc.
     * For components, only collects OUTPUT declarations (inputs are not accessible from outside).
     */
    public static void collectPropertiesFromDeclaration(PsiElement declaration, PropertyVisitor visitor) {
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
                    String name = KiteDeclarationHelper.findNameInDeclaration(child, childType);
                    if (name != null && !name.isEmpty()) {
                        visitor.visit(name, child);
                    }
                }
                // For non-components: collect INPUT_DECLARATION and OUTPUT_DECLARATION
                else if (!isComponent && (childType == KiteElementTypes.INPUT_DECLARATION ||
                    childType == KiteElementTypes.OUTPUT_DECLARATION)) {
                    String name = KiteDeclarationHelper.findNameInDeclaration(child, childType);
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
            if (child.getFirstChild() != null && !KiteDeclarationHelper.isDeclarationType(childType)) {
                collectPropertiesAtDepth(child, visitor, braceDepth);
            }

            child = child.getNextSibling();
        }
    }

    private static void collectPropertiesAtDepth(PsiElement element, PropertyVisitor visitor, int currentDepth) {
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

            if (child.getFirstChild() != null && !KiteDeclarationHelper.isDeclarationType(childType)) {
                collectPropertiesAtDepth(child, visitor, currentDepth);
            }

            child = child.getNextSibling();
        }
    }

    /**
     * Collect properties from a context element (could be a declaration or object literal).
     */
    public static void collectPropertiesFromContext(PsiElement context, PropertyVisitor visitor) {
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
     * Collect properties from an object literal.
     */
    public static void collectPropertiesFromObjectLiteral(PsiElement objectLiteral, PropertyVisitor visitor) {
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

    private static void collectPropertiesFromObjectLiteralRecursive(PsiElement element, PropertyVisitor visitor, int currentDepth) {
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

    /**
     * Visit properties in a context and get their value elements.
     */
    public static void visitPropertiesInContext(PsiElement context, PropertyValueVisitor visitor) {
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
            if (child.getFirstChild() != null && !KiteDeclarationHelper.isDeclarationType(childType)) {
                visitPropertiesInContextRecursive(child, visitor, braceDepth);
            }

            child = child.getNextSibling();
        }
    }

    private static void visitPropertiesInContextRecursive(PsiElement element, PropertyValueVisitor visitor, int currentDepth) {
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

            if (child.getFirstChild() != null && !KiteDeclarationHelper.isDeclarationType(childType)) {
                visitPropertiesInContextRecursive(child, visitor, currentDepth);
            }

            child = child.getNextSibling();
        }
    }

    /**
     * Find the value element of a property within a context.
     * e.g., for "tag = { ... }", returns the object literal element.
     */
    @Nullable
    public static PsiElement findPropertyValue(PsiElement context, String propertyName) {
        final PsiElement[] result = {null};

        // Search for the property in the context
        visitPropertiesInContext(context, (name, valueElement) -> {
            if (name.equals(propertyName) && result[0] == null) {
                result[0] = valueElement;
            }
        });

        return result[0];
    }
}
