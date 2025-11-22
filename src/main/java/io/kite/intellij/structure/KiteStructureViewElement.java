package io.kite.intellij.structure;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.tree.IElementType;
import io.kite.intellij.psi.KiteElementTypes;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Structure view element for Kite language.
 * Represents a node in the structure tree.
 */
public class KiteStructureViewElement implements StructureViewTreeElement, SortableTreeElement {
    private final PsiElement element;

    public KiteStructureViewElement(PsiElement element) {
        this.element = element;
    }

    @Override
    public Object getValue() {
        return element;
    }

    @Override
    public void navigate(boolean requestFocus) {
        if (element instanceof NavigationItem) {
            ((NavigationItem) element).navigate(requestFocus);
        }
    }

    @Override
    public boolean canNavigate() {
        return element instanceof NavigationItem && ((NavigationItem) element).canNavigate();
    }

    @Override
    public boolean canNavigateToSource() {
        return element instanceof NavigationItem && ((NavigationItem) element).canNavigateToSource();
    }

    @NotNull
    @Override
    public String getAlphaSortKey() {
        if (element instanceof PsiNamedElement) {
            String name = ((PsiNamedElement) element).getName();
            return name != null ? name : "";
        }
        return "";
    }

    @NotNull
    @Override
    public ItemPresentation getPresentation() {
        if (element instanceof NavigationItem) {
            ItemPresentation presentation = ((NavigationItem) element).getPresentation();
            if (presentation != null) {
                // Wrap the presentation to add our custom icon
                return new ItemPresentation() {
                    @Override
                    public String getPresentableText() {
                        return presentation.getPresentableText();
                    }

                    @Override
                    public String getLocationString() {
                        return presentation.getLocationString();
                    }

                    @Override
                    public javax.swing.Icon getIcon(boolean unused) {
                        return getIconForElement();
                    }
                };
            }
        }

        // Create a simple presentation with the element's text
        String text = element.getText();
        if (text != null && !text.trim().isEmpty()) {
            String firstLine = text.split("\n")[0].trim();

            // Remove trailing opening brace and whitespace
            if (firstLine.endsWith("{")) {
                firstLine = firstLine.substring(0, firstLine.length() - 1).trim();
            }

            if (firstLine.length() > 50) {
                firstLine = firstLine.substring(0, 50) + "...";
            }
            return new PresentationData(firstLine, null, getIconForElement(), null);
        }

        return new PresentationData("", null, getIconForElement(), null);
    }

    private javax.swing.Icon getIconForElement() {
        IElementType elementType = element.getNode().getElementType();

        if (elementType == KiteElementTypes.RESOURCE_DECLARATION) {
            return KiteStructureViewIcons.RESOURCE;
        } else if (elementType == KiteElementTypes.COMPONENT_DECLARATION) {
            return KiteStructureViewIcons.COMPONENT;
        } else if (elementType == KiteElementTypes.SCHEMA_DECLARATION) {
            return KiteStructureViewIcons.SCHEMA;
        } else if (elementType == KiteElementTypes.FUNCTION_DECLARATION) {
            return KiteStructureViewIcons.FUNCTION;
        } else if (elementType == KiteElementTypes.TYPE_DECLARATION) {
            return KiteStructureViewIcons.TYPE;
        } else if (elementType == KiteElementTypes.VARIABLE_DECLARATION) {
            return KiteStructureViewIcons.VARIABLE;
        } else if (elementType == KiteElementTypes.INPUT_DECLARATION) {
            return KiteStructureViewIcons.INPUT;
        } else if (elementType == KiteElementTypes.OUTPUT_DECLARATION) {
            return KiteStructureViewIcons.OUTPUT;
        } else if (elementType == KiteElementTypes.IMPORT_STATEMENT) {
            return KiteStructureViewIcons.IMPORT;
        }

        return null;
    }

    @NotNull
    @Override
    public TreeElement[] getChildren() {
        List<TreeElement> treeElements = new ArrayList<>();

        for (PsiElement child : element.getChildren()) {
            IElementType elementType = child.getNode().getElementType();

            // Check if this is a declaration element type
            if (elementType == KiteElementTypes.RESOURCE_DECLARATION ||
                elementType == KiteElementTypes.COMPONENT_DECLARATION ||
                elementType == KiteElementTypes.SCHEMA_DECLARATION ||
                elementType == KiteElementTypes.FUNCTION_DECLARATION ||
                elementType == KiteElementTypes.TYPE_DECLARATION ||
                elementType == KiteElementTypes.VARIABLE_DECLARATION ||
                elementType == KiteElementTypes.INPUT_DECLARATION ||
                elementType == KiteElementTypes.OUTPUT_DECLARATION) {
                treeElements.add(new KiteStructureViewElement(child));
            }
        }

        return treeElements.toArray(new TreeElement[0]);
    }
}
