package cloud.kitelang.intellij.navigation;

import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Navigation item for Kite declarations.
 * Used by Go to Class and Go to Symbol features.
 */
public class KiteNavigationItem implements NavigationItem {

    private final PsiElement element;
    private final String name;
    private final String type;
    private final Icon icon;

    public KiteNavigationItem(@NotNull PsiElement element,
                               @NotNull String name,
                               @NotNull String type,
                               @Nullable Icon icon) {
        this.element = element;
        this.name = name;
        this.type = type;
        this.icon = icon;
    }

    @Override
    @Nullable
    public String getName() {
        return name;
    }

    @Override
    @Nullable
    public ItemPresentation getPresentation() {
        return new ItemPresentation() {
            @Override
            @Nullable
            public String getPresentableText() {
                return name;
            }

            @Override
            @Nullable
            public String getLocationString() {
                var file = element.getContainingFile();
                return file != null ? file.getName() : null;
            }

            @Override
            @Nullable
            public Icon getIcon(boolean unused) {
                return icon;
            }
        };
    }

    @Override
    public void navigate(boolean requestFocus) {
        if (element instanceof NavigationItem navItem) {
            navItem.navigate(requestFocus);
        }
    }

    @Override
    public boolean canNavigate() {
        return element instanceof NavigationItem navItem && navItem.canNavigate();
    }

    @Override
    public boolean canNavigateToSource() {
        return element instanceof NavigationItem navItem && navItem.canNavigateToSource();
    }

    /**
     * Returns the underlying PSI element.
     */
    @NotNull
    public PsiElement getElement() {
        return element;
    }

    /**
     * Returns the declaration type (schema, component, function, etc.)
     */
    @NotNull
    public String getType() {
        return type;
    }
}
