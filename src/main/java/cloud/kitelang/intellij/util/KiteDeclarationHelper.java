package cloud.kitelang.intellij.util;

import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import cloud.kitelang.intellij.structure.KiteStructureViewIcons;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Helper class for declaration-related operations.
 * Provides methods to collect, find, and analyze declarations in Kite files.
 */
public final class KiteDeclarationHelper {

    private KiteDeclarationHelper() {
        // Utility class - no instances
    }

    /**
     * Collect all declarations from the file.
     */
    public static void collectDeclarations(PsiFile file, DeclarationVisitor visitor) {
        collectDeclarationsRecursive(file, visitor);
    }

    private static void collectDeclarationsRecursive(PsiElement element, DeclarationVisitor visitor) {
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
     * Collect for-loop variables.
     */
    public static void collectForLoopVariables(PsiFile file, ForLoopVariableVisitor visitor) {
        collectForLoopVariablesRecursive(file, visitor);
    }

    private static void collectForLoopVariablesRecursive(PsiElement element, ForLoopVariableVisitor visitor) {
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
     * Find a declaration by name in the file.
     */
    @Nullable
    public static PsiElement findDeclaration(PsiFile file, String name) {
        final PsiElement[] result = {null};

        collectDeclarations(file, (declName, declarationType, element) -> {
            if (name.equals(declName) && result[0] == null) {
                result[0] = element;
            }
        });

        return result[0];
    }

    /**
     * Check if an element type is a declaration type.
     */
    public static boolean isDeclarationType(IElementType type) {
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
     * Check if a declaration type is a type/schema/component definition (not a value).
     * These should be excluded from value position completions.
     */
    public static boolean isTypeDeclaration(IElementType type) {
        return type == KiteElementTypes.SCHEMA_DECLARATION ||
               type == KiteElementTypes.TYPE_DECLARATION ||
               type == KiteElementTypes.COMPONENT_DECLARATION;
    }

    /**
     * Find the name in a declaration.
     *
     * @see KitePsiUtil#findDeclarationName(PsiElement, IElementType)
     */
    @Nullable
    public static String findNameInDeclaration(PsiElement declaration, IElementType declarationType) {
        return KitePsiUtil.findDeclarationName(declaration, declarationType);
    }

    /**
     * Find the name element (PsiElement) in a declaration.
     * Returns the actual identifier element, not just the text.
     * <p>
     * Handles different declaration patterns:
     * <ul>
     *   <li>For-loops: identifier after FOR keyword and before IN</li>
     *   <li>Functions: name after FUN keyword and before LPAREN</li>
     *   <li>Default (variables, resources, schemas, types): last identifier before = or { or +=</li>
     * </ul>
     */
    @Nullable
    public static PsiElement findNameElementInDeclaration(PsiElement declaration, IElementType declarationType) {
        // For-loop: identifier after FOR keyword
        if (declarationType == KiteElementTypes.FOR_STATEMENT) {
            var foundFor = false;
            var child = declaration.getFirstChild();
            while (child != null) {
                if (child.getNode() != null) {
                    var childType = child.getNode().getElementType();
                    if (childType == KiteTokenTypes.FOR) {
                        foundFor = true;
                    } else if (foundFor && childType == KiteTokenTypes.IDENTIFIER) {
                        return child;
                    }
                }
                child = child.getNextSibling();
            }
            return null;
        }

        // Function: name after FUN keyword and before LPAREN
        if (declarationType == KiteElementTypes.FUNCTION_DECLARATION) {
            var foundFun = false;
            var child = declaration.getFirstChild();
            while (child != null) {
                if (child.getNode() != null) {
                    var childType = child.getNode().getElementType();
                    if (childType == KiteTokenTypes.FUN) {
                        foundFun = true;
                    } else if (foundFun && childType == KiteTokenTypes.IDENTIFIER) {
                        return child; // First identifier after FUN is the function name
                    } else if (childType == KiteTokenTypes.LPAREN) {
                        break; // Stop at opening paren
                    }
                }
                child = child.getNextSibling();
            }
            return null;
        }

        // Default: find last identifier before = or { or +=
        PsiElement lastIdentifier = null;
        var child = declaration.getFirstChild();
        while (child != null) {
            var childType = child.getNode().getElementType();
            if (childType == KiteTokenTypes.IDENTIFIER) {
                lastIdentifier = child;
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
     * Find a declaration's name element by name in the file.
     * Returns the name identifier PsiElement, not the declaration element.
     */
    @Nullable
    public static PsiElement findDeclarationNameElement(PsiFile file, String name) {
        final PsiElement[] result = {null};

        collectDeclarations(file, (declName, declarationType, element) -> {
            if (name.equals(declName) && result[0] == null) {
                result[0] = findNameElementInDeclaration(element, declarationType);
            }
        });

        return result[0];
    }

    /**
     * Get display text for a declaration type.
     */
    public static String getTypeTextForDeclaration(IElementType type) {
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
     * Get icon for a declaration type.
     */
    @Nullable
    public static Icon getIconForDeclaration(IElementType type) {
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

    /**
     * Visitor interface for iterating over declarations.
     */
    @FunctionalInterface
    public interface DeclarationVisitor {
        void visit(String name, IElementType declarationType, PsiElement element);
    }

    /**
     * Visitor interface for iterating over for-loop variables.
     */
    @FunctionalInterface
    public interface ForLoopVariableVisitor {
        void visit(String name, PsiElement element);
    }
}
