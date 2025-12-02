package cloud.kitelang.intellij.navigation;

import cloud.kitelang.intellij.KiteLanguage;
import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import cloud.kitelang.intellij.reference.KiteImportHelper;
import cloud.kitelang.intellij.structure.KiteStructureViewIcons;
import cloud.kitelang.intellij.util.KitePsiUtil;
import cloud.kitelang.intellij.util.KiteSchemaHelper;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.codeInsight.daemon.impl.PsiElementListNavigator;
import com.intellij.ide.util.DefaultPsiElementCellRenderer;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Provides gutter icons for declarations (functions, resources, components, etc.)
 * that show a dropdown with usages when clicked.
 */
public class KiteLineMarkerProvider implements LineMarkerProvider {

    @Override
    public @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        // Only process in Kite files
        if (element.getLanguage() != KiteLanguage.INSTANCE) {
            return null;
        }

        // We mark declaration names (function names, resource names, etc.)
        if (element.getNode() == null) {
            return null;
        }

        IElementType elementType = element.getNode().getElementType();
        if (elementType != KiteTokenTypes.IDENTIFIER) {
            return null;
        }

        // Check if this identifier is a declaration name
        DeclarationInfo declInfo = getDeclarationInfo(element);
        if (declInfo == null) {
            return null;
        }

        // Create a line marker for this declaration
        return new LineMarkerInfo<>(
                element,
                element.getTextRange(),
                declInfo.icon,
                getTooltipProvider(declInfo.type, element.getText()),
                createNavigationHandler(element),
                GutterIconRenderer.Alignment.RIGHT,
                () -> declInfo.type + " " + element.getText()
        );
    }

    /**
     * Determine if the identifier is a declaration name and return info about it.
     * Only shows markers for schemas and component definitions (not instantiations).
     */
    @Nullable
    private DeclarationInfo getDeclarationInfo(PsiElement identifier) {
        PsiElement parent = identifier.getParent();
        if (parent == null || parent.getNode() == null) {
            return null;
        }

        IElementType parentType = parent.getNode().getElementType();

        // Only show markers for schemas and component definitions
        if (parentType == KiteElementTypes.COMPONENT_DECLARATION) {
            // Only show for component definitions, not instantiations
            // Definition: component WebServer { ... } (1 identifier)
            // Instantiation: component WebServer serviceA { ... } (2 identifiers)
            if (!isComponentInstantiation(parent) && isComponentDefinitionName(identifier, parent)) {
                return new DeclarationInfo("Component", KiteStructureViewIcons.COMPONENT);
            }
        } else if (parentType == KiteElementTypes.SCHEMA_DECLARATION) {
            // Schema name is the IDENTIFIER after SCHEMA keyword
            if (isSchemaName(identifier, parent)) {
                return new DeclarationInfo("Schema", KiteStructureViewIcons.SCHEMA);
            }
        }

        return null;
    }

    /**
     * Check if this is a component definition (not instantiation) and if the identifier is the type name.
     */
    private boolean isComponentDefinitionName(PsiElement identifier, PsiElement parent) {
        // For component definition, the name is the first identifier after COMPONENT keyword
        boolean foundComponent = false;
        for (PsiElement child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;
            IElementType type = child.getNode().getElementType();

            if (type == KiteTokenTypes.COMPONENT) {
                foundComponent = true;
            } else if (foundComponent && type == KiteTokenTypes.IDENTIFIER) {
                return child == identifier;
            } else if (type == KiteTokenTypes.LBRACE) {
                break;
            }
        }
        return false;
    }

    /**
     * Check if a component declaration is an instantiation (has both type and instance name).
     * Definition: component WebServer { ... } (1 identifier before LBRACE)
     * Instantiation: component WebServer serviceA { ... } (2 identifiers before LBRACE)
     */
    private boolean isComponentInstantiation(PsiElement componentDecl) {
        return KiteSchemaHelper.isComponentInstantiation(componentDecl);
    }

    /**
     * Check if the identifier is a function name (first IDENTIFIER after FUN).
     */
    private boolean isFunctionName(PsiElement identifier, PsiElement parent) {
        boolean foundFun = false;
        for (PsiElement child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;
            IElementType type = child.getNode().getElementType();

            if (type == KiteTokenTypes.FUN) {
                foundFun = true;
            } else if (foundFun && type == KiteTokenTypes.IDENTIFIER) {
                return child == identifier;
            } else if (type == KiteTokenTypes.LPAREN) {
                break;
            }
        }
        return false;
    }

    /**
     * Check if the identifier is a resource/component name (last IDENTIFIER before LBRACE).
     */
    private boolean isResourceOrComponentName(PsiElement identifier, PsiElement parent) {
        PsiElement lastIdentifier = null;
        for (PsiElement child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;
            IElementType type = child.getNode().getElementType();

            if (type == KiteTokenTypes.IDENTIFIER) {
                lastIdentifier = child;
            } else if (type == KiteTokenTypes.LBRACE) {
                break;
            }
        }
        return lastIdentifier == identifier;
    }

    /**
     * Check if the identifier is a schema name (IDENTIFIER after SCHEMA).
     */
    private boolean isSchemaName(PsiElement identifier, PsiElement parent) {
        boolean foundSchema = false;
        for (PsiElement child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;
            IElementType type = child.getNode().getElementType();

            if (type == KiteTokenTypes.SCHEMA) {
                foundSchema = true;
            } else if (foundSchema && type == KiteTokenTypes.IDENTIFIER) {
                return child == identifier;
            } else if (type == KiteTokenTypes.LBRACE) {
                break;
            }
        }
        return false;
    }

    /**
     * Check if the identifier is a variable/input/output name (last IDENTIFIER before ASSIGN).
     */
    private boolean isVariableName(PsiElement identifier, PsiElement parent) {
        PsiElement lastIdentifier = null;
        for (PsiElement child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;
            IElementType type = child.getNode().getElementType();

            if (type == KiteTokenTypes.IDENTIFIER) {
                lastIdentifier = child;
            } else if (type == KiteTokenTypes.ASSIGN) {
                break;
            }
        }
        return lastIdentifier == identifier;
    }

    /**
     * Check if the identifier is a type name (IDENTIFIER after TYPE keyword).
     */
    private boolean isTypeName(PsiElement identifier, PsiElement parent) {
        boolean foundType = false;
        for (PsiElement child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;
            IElementType type = child.getNode().getElementType();

            if (type == KiteTokenTypes.TYPE) {
                foundType = true;
            } else if (foundType && type == KiteTokenTypes.IDENTIFIER) {
                return child == identifier;
            } else if (type == KiteTokenTypes.ASSIGN) {
                break;
            }
        }
        return false;
    }

    /**
     * Create a tooltip provider for the gutter icon.
     */
    private Function<PsiElement, String> getTooltipProvider(String type, String name) {
        return element -> type + " '" + name + "' - Click to find usages";
    }

    /**
     * Create a navigation handler that shows usages popup when the gutter icon is clicked.
     */
    private GutterIconNavigationHandler<PsiElement> createNavigationHandler(PsiElement declaration) {
        return (MouseEvent e, PsiElement elt) -> {
            // Find all usages of this declaration
            List<NavigatablePsiElement> usages = findUsages(elt);

            if (usages.isEmpty()) {
                // No usages found - could show a message or do nothing
                return;
            }

            // Show the usages popup
            PsiElementListNavigator.openTargets(
                    e,
                    usages.toArray(new NavigatablePsiElement[0]),
                    "Usages of '" + elt.getText() + "'",
                    "Found " + usages.size() + " usage(s)",
                    new KiteUsageCellRenderer()
            );
        };
    }

    /**
     * Find all usages of the given declaration name.
     */
    private List<NavigatablePsiElement> findUsages(PsiElement declaration) {
        String name = declaration.getText();
        List<NavigatablePsiElement> usages = new ArrayList<>();
        Set<String> visitedFiles = new HashSet<>();

        PsiFile currentFile = declaration.getContainingFile();
        if (currentFile == null) {
            return usages;
        }

        // Search in current file
        findUsagesInFile(currentFile, name, declaration, usages);
        visitedFiles.add(currentFile.getVirtualFile().getPath());

        // Search in imported files (files that import this file might use the declaration)
        // For now, just search in the current file and files this file imports
        List<PsiFile> importedFiles = KiteImportHelper.getImportedFiles(currentFile);
        for (PsiFile importedFile : importedFiles) {
            if (importedFile != null && importedFile.getVirtualFile() != null) {
                String path = importedFile.getVirtualFile().getPath();
                if (!visitedFiles.contains(path)) {
                    findUsagesInFile(importedFile, name, declaration, usages);
                    visitedFiles.add(path);
                }
            }
        }

        return usages;
    }

    /**
     * Find usages of the given name in the given file.
     */
    private void findUsagesInFile(PsiFile file, String name, PsiElement declaration, List<NavigatablePsiElement> usages) {
        findUsagesInElement(file, name, declaration, usages);
    }

    /**
     * Recursively find usages of the given name in the element and its children.
     */
    private void findUsagesInElement(PsiElement element, String name, PsiElement declaration, List<NavigatablePsiElement> usages) {
        if (element.getNode() == null) {
            return;
        }

        IElementType type = element.getNode().getElementType();

        // Check if this is an identifier with the same name
        if (type == KiteTokenTypes.IDENTIFIER && name.equals(element.getText())) {
            // Make sure it's not the declaration itself
            if (element != declaration && !isSameElement(element, declaration)) {
                // Make sure it's a reference (not a declaration name)
                if (isReference(element)) {
                    usages.add(new KiteNavigatablePsiElement(element));
                }
            }
        }

        // Also check STRING_TEXT for $name or ${name} pattern (string interpolation)
        if (type == KiteTokenTypes.STRING_TEXT) {
            String text = element.getText();
            if (text.contains("$" + name) || text.contains("${" + name + "}")) {
                // Found a string interpolation reference
                usages.add(new KiteNavigatablePsiElement(element));
            }
        }

        // Recurse into children
        for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            findUsagesInElement(child, name, declaration, usages);
        }
    }

    /**
     * Check if two elements are the same (same offset in same file).
     */
    private boolean isSameElement(PsiElement a, PsiElement b) {
        if (a.getTextOffset() != b.getTextOffset()) {
            return false;
        }
        PsiFile fileA = a.getContainingFile();
        PsiFile fileB = b.getContainingFile();
        if (fileA == null || fileB == null) {
            return fileA == fileB;
        }
        return fileA.getVirtualFile() != null && fileB.getVirtualFile() != null &&
               fileA.getVirtualFile().getPath().equals(fileB.getVirtualFile().getPath());
    }

    /**
     * Check if the identifier is a reference (not a declaration name).
     * A reference is an identifier that is not followed by = { += :
     */
    private boolean isReference(PsiElement identifier) {
        var next = KitePsiUtil.skipWhitespace(identifier.getNextSibling());
        if (next == null) {
            return true; // End of block, probably a reference
        }

        IElementType nextType = next.getNode().getElementType();

        // If followed by = { += : then it's likely a declaration name
        if (nextType == KiteTokenTypes.ASSIGN ||
            nextType == KiteTokenTypes.LBRACE ||
            nextType == KiteTokenTypes.PLUS_ASSIGN ||
            nextType == KiteTokenTypes.COLON) {
            return false;
        }

        // Also check if it's the name in a declaration (after keyword)
        var prev = KitePsiUtil.skipWhitespaceBackward(identifier.getPrevSibling());
        if (prev != null) {
            IElementType prevType = prev.getNode().getElementType();
            // If preceded by FUN, it's the function name
            if (prevType == KiteTokenTypes.FUN ||
                prevType == KiteTokenTypes.TYPE ||
                prevType == KiteTokenTypes.SCHEMA) {
                return false;
            }

            // If preceded by VAR/INPUT/OUTPUT, check if this is the variable name
            if (prevType == KiteTokenTypes.VAR ||
                prevType == KiteTokenTypes.INPUT ||
                prevType == KiteTokenTypes.OUTPUT) {
                // Check if followed by = (type name = value or name = value)
                var afterThis = KitePsiUtil.skipWhitespace(identifier.getNextSibling());
                if (afterThis != null) {
                    IElementType afterType = afterThis.getNode().getElementType();
                    if (afterType == KiteTokenTypes.ASSIGN) {
                        return false; // It's the variable name
                    }
                    // Could be: var type name = value
                    // In this case, this identifier is the type, not the name
                }
            }

            // If preceded by another identifier and followed by identifier or =
            // it might be a type annotation, so check further
            if (prevType == KiteTokenTypes.IDENTIFIER) {
                // Check if we're in a declaration context
                var prevPrev = KitePsiUtil.skipWhitespaceBackward(prev.getPrevSibling());
                if (prevPrev != null) {
                    IElementType prevPrevType = prevPrev.getNode().getElementType();
                    if (prevPrevType == KiteTokenTypes.VAR ||
                        prevPrevType == KiteTokenTypes.INPUT ||
                        prevPrevType == KiteTokenTypes.OUTPUT) {
                        // This could be: var type name - this is the name
                        var afterThis = KitePsiUtil.skipWhitespace(identifier.getNextSibling());
                        return afterThis == null || afterThis.getNode().getElementType() != KiteTokenTypes.ASSIGN; // It's the variable name
                    }
                }
            }
        }

        return true;
    }

    /**
     * Information about a declaration.
     */
    private record DeclarationInfo(String type, Icon icon) {
    }

    /**
     * Custom cell renderer for usage items in the popup.
     */
    private static class KiteUsageCellRenderer extends DefaultPsiElementCellRenderer {
        @Override
        public String getElementText(PsiElement element) {
            if (element instanceof KiteNavigatablePsiElement navElement) {
                return navElement.getName();
            }
            return super.getElementText(element);
        }

        @Override
        public String getContainerText(PsiElement element, String name) {
            if (element instanceof KiteNavigatablePsiElement navElement) {
                if (navElement.getPresentation() != null) {
                    return navElement.getPresentation().getLocationString();
                }
            }
            return super.getContainerText(element, name);
        }

        @Override
        protected Icon getIcon(PsiElement element) {
            if (element instanceof KiteNavigatablePsiElement navElement) {
                if (navElement.getPresentation() != null) {
                    return navElement.getPresentation().getIcon(false);
                }
            }
            return super.getIcon(element);
        }
    }
}
