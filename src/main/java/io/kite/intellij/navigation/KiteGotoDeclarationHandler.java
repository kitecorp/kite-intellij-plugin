package io.kite.intellij.navigation;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import io.kite.intellij.KiteLanguage;
import io.kite.intellij.psi.KiteElementTypes;
import io.kite.intellij.psi.KiteTokenTypes;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Handler for "Go to Declaration" (Cmd+Click) in Kite files.
 */
public class KiteGotoDeclarationHandler implements GotoDeclarationHandler {

    @Override
    public PsiElement @Nullable [] getGotoDeclarationTargets(@Nullable PsiElement sourceElement, int offset, Editor editor) {
        if (sourceElement == null) {
            return null;
        }

        // Only handle Kite files
        PsiFile file = sourceElement.getContainingFile();
        if (file == null || file.getLanguage() != KiteLanguage.INSTANCE) {
            return null;
        }

        // Only handle IDENTIFIER tokens
        if (sourceElement.getNode().getElementType() != KiteTokenTypes.IDENTIFIER) {
            return null;
        }

        String targetName = sourceElement.getText();
        System.err.println("[KiteGoto] Looking for: " + targetName);

        // Check if this is a property access (identifier after a DOT)
        // Skip whitespace to find the actual previous token
        PsiElement prevSibling = skipWhitespaceBackward(sourceElement.getPrevSibling());
        System.err.println("[KiteGoto] prevSibling (after skip): " + (prevSibling != null ? prevSibling.getNode().getElementType() + " = '" + prevSibling.getText() + "'" : "null"));
        if (prevSibling != null && prevSibling.getNode().getElementType() == KiteTokenTypes.DOT) {
            // This is a property access like server.size - find the property in the object
            PsiElement objectIdentifier = skipWhitespaceBackward(prevSibling.getPrevSibling());
            System.err.println("[KiteGoto] objectIdentifier: " + (objectIdentifier != null ? objectIdentifier.getNode().getElementType() + " = '" + objectIdentifier.getText() + "'" : "null"));
            if (objectIdentifier != null && objectIdentifier.getNode().getElementType() == KiteTokenTypes.IDENTIFIER) {
                String objectName = objectIdentifier.getText();

                // Find the declaration of the object (e.g., resource server)
                PsiElement objectDeclaration = findDeclarationElement(file, objectName, objectIdentifier);
                System.err.println("[KiteGoto] objectDeclaration: " + (objectDeclaration != null ? objectDeclaration.getNode().getElementType() : "null"));
                if (objectDeclaration != null) {
                    // Search for property within the object's body
                    PsiElement property = findPropertyInDeclaration(objectDeclaration, targetName);
                    System.err.println("[KiteGoto] property found: " + (property != null ? property.getText() : "null"));
                    if (property != null) {
                        return new PsiElement[]{property};
                    }
                }
            }
            // If we can't resolve the property, return null (don't fall through to global search)
            return null;
        }

        // Regular declaration lookup
        PsiElement declaration = findDeclaration(file, targetName, sourceElement);
        if (declaration != null) {
            return new PsiElement[]{declaration};
        }

        return null;
    }

    /**
     * Find a property assignment within a declaration body.
     * Looks for patterns like: propertyName = value
     */
    @Nullable
    private PsiElement findPropertyInDeclaration(PsiElement declaration, String propertyName) {
        // Search within the declaration for property assignments
        return findPropertyRecursive(declaration, propertyName);
    }

    @Nullable
    private PsiElement findPropertyRecursive(PsiElement declaration, String propertyName) {
        // Find all leaf elements in the declaration and check each one
        // Use text search within the declaration's text range
        String text = declaration.getText();
        int startOffset = declaration.getTextOffset();

        // Find pattern "propertyName =" or "propertyName=" in the text
        String pattern1 = propertyName + " =";
        String pattern2 = propertyName + "=";

        int idx = text.indexOf(pattern1);
        if (idx == -1) {
            idx = text.indexOf(pattern2);
        }

        if (idx != -1) {
            // Found the pattern, now find the actual PSI element at this position
            int absoluteOffset = startOffset + idx;
            PsiFile file = declaration.getContainingFile();
            PsiElement elementAtOffset = file.findElementAt(absoluteOffset);

            if (elementAtOffset != null &&
                elementAtOffset.getNode().getElementType() == KiteTokenTypes.IDENTIFIER &&
                propertyName.equals(elementAtOffset.getText())) {
                return elementAtOffset;
            }
        }

        return null;
    }

    private boolean isWhitespace(IElementType type) {
        return type == KiteTokenTypes.WHITESPACE ||
               type == KiteTokenTypes.NL ||
               type == KiteTokenTypes.NEWLINE;
    }

    /**
     * Skip whitespace tokens when traversing backward.
     */
    @Nullable
    private PsiElement skipWhitespaceBackward(@Nullable PsiElement element) {
        while (element != null && isWhitespace(element.getNode().getElementType())) {
            element = element.getPrevSibling();
        }
        return element;
    }

    /**
     * Find the declaration element (the whole declaration node) for a given name.
     */
    @Nullable
    private PsiElement findDeclarationElement(PsiElement element, String targetName, PsiElement sourceElement) {
        IElementType type = element.getNode().getElementType();

        if (isDeclarationType(type)) {
            PsiElement nameElement = findNameInDeclaration(element, type);
            if (nameElement != null && targetName.equals(nameElement.getText())) {
                if (!nameElement.getTextRange().equals(sourceElement.getTextRange())) {
                    return element;  // Return the whole declaration, not just the name
                }
            }
        }

        PsiElement child = element.getFirstChild();
        while (child != null) {
            PsiElement result = findDeclarationElement(child, targetName, sourceElement);
            if (result != null) {
                return result;
            }
            child = child.getNextSibling();
        }

        return null;
    }

    /**
     * Find the declaration for the given name in the file.
     */
    @Nullable
    private PsiElement findDeclaration(PsiElement element, String targetName, PsiElement sourceElement) {
        IElementType type = element.getNode().getElementType();

        // Check if this is a declaration type
        if (isDeclarationType(type)) {
            PsiElement nameElement = findNameInDeclaration(element, type);
            if (nameElement != null && targetName.equals(nameElement.getText())) {
                // Don't resolve to self
                if (!nameElement.getTextRange().equals(sourceElement.getTextRange())) {
                    return nameElement;
                }
            }
        }

        // Recurse into children
        PsiElement child = element.getFirstChild();
        while (child != null) {
            PsiElement result = findDeclaration(child, targetName, sourceElement);
            if (result != null) {
                return result;
            }
            child = child.getNextSibling();
        }

        return null;
    }

    private boolean isDeclarationType(IElementType type) {
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
     * Find the name identifier within a declaration.
     */
    @Nullable
    private PsiElement findNameInDeclaration(PsiElement declaration, IElementType declarationType) {
        if (declarationType == KiteElementTypes.FOR_STATEMENT) {
            // For loop: "for identifier in ..." - name is right after 'for'
            boolean foundFor = false;
            PsiElement child = declaration.getFirstChild();
            while (child != null) {
                IElementType childType = child.getNode().getElementType();
                if (childType == KiteTokenTypes.FOR) {
                    foundFor = true;
                } else if (foundFor && childType == KiteTokenTypes.IDENTIFIER) {
                    return child;
                }
                child = child.getNextSibling();
            }
        }

        // For var/input/output: keyword [type] name [= value]
        // For resource/component/schema/function: keyword [type] name { ... }
        // Find the identifier that comes before '=' or '{'

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
                    return lastIdentifier;
                }
            }
            child = child.getNextSibling();
        }

        return lastIdentifier;
    }
}
