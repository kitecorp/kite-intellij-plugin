package io.kite.intellij.navigation;

import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.psi.tree.IElementType;
import io.kite.intellij.psi.KiteElementTypes;
import io.kite.intellij.structure.KiteStructureViewIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * A wrapper around a Kite PSI element that provides custom navigation presentation.
 * Shows the full line of code, context type, file name, and line number in the popup.
 */
public class KiteNavigatablePsiElement extends FakePsiElement implements NavigationItem {
    private final PsiElement myElement;
    private final String myLineText;
    private final String myLocationString;
    private final Icon myIcon;

    public KiteNavigatablePsiElement(@NotNull PsiElement element) {
        this.myElement = element;
        this.myLineText = getLineText(element);
        this.myLocationString = buildLocationString(element);
        this.myIcon = getIconForContext(element);
    }

    @Override
    public PsiElement getParent() {
        return myElement.getParent();
    }

    @Override
    public @NotNull PsiElement getNavigationElement() {
        return myElement;
    }

    @Override
    public PsiFile getContainingFile() {
        return myElement.getContainingFile();
    }

    @Override
    public boolean isValid() {
        return myElement.isValid();
    }

    @Override
    public int getTextOffset() {
        return myElement.getTextOffset();
    }

    @Override
    public @Nullable String getName() {
        return myLineText;
    }

    @Override
    public void navigate(boolean requestFocus) {
        Navigatable navigatable = PsiNavigationSupport.getInstance().getDescriptor(myElement);
        if (navigatable != null) {
            navigatable.navigate(requestFocus);
        }
    }

    @Override
    public boolean canNavigate() {
        return PsiNavigationSupport.getInstance().canNavigate(myElement);
    }

    @Override
    public boolean canNavigateToSource() {
        return canNavigate();
    }

    @Override
    public ItemPresentation getPresentation() {
        return new ItemPresentation() {
            @Override
            public @Nullable String getPresentableText() {
                return myLineText;
            }

            @Override
            public @Nullable String getLocationString() {
                return myLocationString;
            }

            @Override
            public @Nullable Icon getIcon(boolean unused) {
                return myIcon;
            }
        };
    }

    /**
     * Get the full line of code containing this element.
     */
    private static String getLineText(PsiElement element) {
        PsiFile file = element.getContainingFile();
        if (file == null) {
            return element.getText();
        }

        String fileText = file.getText();
        int offset = element.getTextOffset();

        // Find line start
        int lineStart = offset;
        while (lineStart > 0 && fileText.charAt(lineStart - 1) != '\n') {
            lineStart--;
        }

        // Find line end
        int lineEnd = offset;
        while (lineEnd < fileText.length() && fileText.charAt(lineEnd) != '\n') {
            lineEnd++;
        }

        return fileText.substring(lineStart, lineEnd).trim();
    }

    /**
     * Build a location string like "in Resource - filename.kite:42"
     */
    private static String buildLocationString(PsiElement element) {
        PsiFile file = element.getContainingFile();
        if (file == null) {
            return "";
        }

        String contextType = determineContextType(element);
        String fileName = file.getName();
        int lineNumber = getLineNumber(element);

        if (contextType != null) {
            return "in " + contextType + " - " + fileName + ":" + lineNumber;
        } else {
            return fileName + ":" + lineNumber;
        }
    }

    /**
     * Get the 1-based line number for the element.
     */
    private static int getLineNumber(PsiElement element) {
        PsiFile file = element.getContainingFile();
        if (file == null) {
            return 0;
        }

        String text = file.getText();
        int offset = element.getTextOffset();
        int line = 1;
        for (int i = 0; i < offset && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    /**
     * Determine what type of declaration context this element is in.
     */
    private static String determineContextType(PsiElement element) {
        PsiElement parent = element.getParent();
        while (parent != null && !(parent instanceof PsiFile)) {
            IElementType type = parent.getNode().getElementType();

            if (type == KiteElementTypes.RESOURCE_DECLARATION) {
                return "Resource";
            } else if (type == KiteElementTypes.COMPONENT_DECLARATION) {
                return "Component";
            } else if (type == KiteElementTypes.FUNCTION_DECLARATION) {
                return "Function";
            } else if (type == KiteElementTypes.SCHEMA_DECLARATION) {
                return "Schema";
            } else if (type == KiteElementTypes.INPUT_DECLARATION) {
                return "Input";
            } else if (type == KiteElementTypes.OUTPUT_DECLARATION) {
                return "Output";
            } else if (type == KiteElementTypes.VARIABLE_DECLARATION) {
                return "Variable";
            } else if (type == KiteElementTypes.TYPE_DECLARATION) {
                return "Type";
            } else if (type == KiteElementTypes.FOR_STATEMENT) {
                return "For Loop";
            }

            parent = parent.getParent();
        }
        return null;
    }

    /**
     * Get the icon for the context type.
     */
    private static Icon getIconForContext(PsiElement element) {
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
        return KiteStructureViewIcons.VARIABLE;
    }
}
