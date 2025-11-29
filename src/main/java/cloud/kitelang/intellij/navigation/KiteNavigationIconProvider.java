package cloud.kitelang.intellij.navigation;

import cloud.kitelang.intellij.KiteLanguage;
import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import cloud.kitelang.intellij.structure.KiteStructureViewIcons;
import com.intellij.ide.IconProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Icon provider for Kite language elements.
 * Provides context-aware icons for elements in navigation popups.
 */
public class KiteNavigationIconProvider extends IconProvider {

    @Override
    public @Nullable Icon getIcon(@NotNull PsiElement element, int flags) {
        // Handle our custom navigatable wrapper - get icon from its presentation
        if (element instanceof KiteNavigatablePsiElement) {
            KiteNavigatablePsiElement wrapper = (KiteNavigatablePsiElement) element;
            if (wrapper.getPresentation() != null) {
                return wrapper.getPresentation().getIcon(false);
            }
        }

        // Only handle Kite language elements
        PsiFile file = element.getContainingFile();
        if (file == null || file.getLanguage() != KiteLanguage.INSTANCE) {
            return null;
        }

        // For identifier tokens, determine icon based on containing declaration
        if (element.getNode() != null && element.getNode().getElementType() == KiteTokenTypes.IDENTIFIER) {
            return getIconForContext(element);
        }

        // For declaration elements, return appropriate icon
        IElementType type = element.getNode() != null ? element.getNode().getElementType() : null;
        if (type != null) {
            if (type == KiteElementTypes.RESOURCE_DECLARATION) {
                return KiteStructureViewIcons.RESOURCE;
            } else if (type == KiteElementTypes.COMPONENT_DECLARATION) {
                return KiteStructureViewIcons.COMPONENT;
            } else if (type == KiteElementTypes.FUNCTION_DECLARATION) {
                return KiteStructureViewIcons.FUNCTION;
            } else if (type == KiteElementTypes.SCHEMA_DECLARATION) {
                return KiteStructureViewIcons.SCHEMA;
            } else if (type == KiteElementTypes.INPUT_DECLARATION) {
                return KiteStructureViewIcons.INPUT;
            } else if (type == KiteElementTypes.OUTPUT_DECLARATION) {
                return KiteStructureViewIcons.OUTPUT;
            } else if (type == KiteElementTypes.VARIABLE_DECLARATION) {
                return KiteStructureViewIcons.VARIABLE;
            } else if (type == KiteElementTypes.TYPE_DECLARATION) {
                return KiteStructureViewIcons.TYPE;
            }
        }

        return null;
    }

    /**
     * Get an icon based on the containing declaration type.
     */
    private Icon getIconForContext(PsiElement element) {
        PsiElement parent = element.getParent();
        while (parent != null && !(parent instanceof PsiFile)) {
            IElementType type = parent.getNode().getElementType();

            if (type == KiteElementTypes.RESOURCE_DECLARATION) {
                return KiteStructureViewIcons.RESOURCE;
            } else if (type == KiteElementTypes.COMPONENT_DECLARATION) {
                return KiteStructureViewIcons.COMPONENT;
            } else if (type == KiteElementTypes.FUNCTION_DECLARATION) {
                return KiteStructureViewIcons.FUNCTION;
            } else if (type == KiteElementTypes.SCHEMA_DECLARATION) {
                return KiteStructureViewIcons.SCHEMA;
            } else if (type == KiteElementTypes.INPUT_DECLARATION) {
                return KiteStructureViewIcons.INPUT;
            } else if (type == KiteElementTypes.OUTPUT_DECLARATION) {
                return KiteStructureViewIcons.OUTPUT;
            } else if (type == KiteElementTypes.VARIABLE_DECLARATION) {
                return KiteStructureViewIcons.VARIABLE;
            } else if (type == KiteElementTypes.TYPE_DECLARATION) {
                return KiteStructureViewIcons.TYPE;
            }

            parent = parent.getParent();
        }
        // Default to variable icon for usages at file scope
        return KiteStructureViewIcons.VARIABLE;
    }
}
