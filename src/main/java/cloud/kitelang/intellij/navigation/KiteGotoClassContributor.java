package cloud.kitelang.intellij.navigation;

import cloud.kitelang.intellij.KiteFileType;
import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.structure.KiteStructureViewIcons;
import cloud.kitelang.intellij.util.KiteDeclarationHelper;
import cloud.kitelang.intellij.util.KitePsiUtil;
import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Go to Class contributor for Kite.
 * Finds schemas and components (type-level declarations).
 * Accessible via Cmd+O (Mac) or Ctrl+N (Windows/Linux).
 */
public class KiteGotoClassContributor implements ChooseByNameContributor {

    @Override
    @NotNull
    public String[] getNames(@NotNull Project project, boolean includeNonProjectItems) {
        List<String> names = new ArrayList<>();

        var scope = includeNonProjectItems
                ? GlobalSearchScope.allScope(project)
                : GlobalSearchScope.projectScope(project);

        var psiManager = PsiManager.getInstance(project);
        var virtualFiles = FileTypeIndex.getFiles(KiteFileType.INSTANCE, scope);

        for (var virtualFile : virtualFiles) {
            var psiFile = psiManager.findFile(virtualFile);
            if (psiFile == null) continue;

            collectClassNames(psiFile, names);
        }

        return names.toArray(new String[0]);
    }

    @Override
    @NotNull
    public NavigationItem[] getItemsByName(@NotNull String name,
                                           @NotNull String pattern,
                                           @NotNull Project project,
                                           boolean includeNonProjectItems) {
        List<NavigationItem> items = new ArrayList<>();

        var scope = includeNonProjectItems
                ? GlobalSearchScope.allScope(project)
                : GlobalSearchScope.projectScope(project);

        var psiManager = PsiManager.getInstance(project);
        var virtualFiles = FileTypeIndex.getFiles(KiteFileType.INSTANCE, scope);

        for (var virtualFile : virtualFiles) {
            var psiFile = psiManager.findFile(virtualFile);
            if (psiFile == null) continue;

            collectClassItems(psiFile, name, items);
        }

        return items.toArray(new NavigationItem[0]);
    }

    /**
     * Collect names of class-level declarations (schemas, components).
     */
    private void collectClassNames(PsiElement element, List<String> names) {
        if (element.getNode() == null) return;

        IElementType type = element.getNode().getElementType();

        if (type == KiteElementTypes.SCHEMA_DECLARATION ||
            type == KiteElementTypes.COMPONENT_DECLARATION) {

            var declName = KitePsiUtil.findDeclarationName(element, type);
            if (declName != null && !declName.isEmpty()) {
                names.add(declName);
            }
        }

        // Recurse into children
        for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            collectClassNames(child, names);
        }
    }

    /**
     * Collect navigation items matching the given name.
     */
    private void collectClassItems(PsiElement element, String targetName, List<NavigationItem> items) {
        if (element.getNode() == null) return;

        IElementType type = element.getNode().getElementType();

        if (type == KiteElementTypes.SCHEMA_DECLARATION ||
            type == KiteElementTypes.COMPONENT_DECLARATION) {

            var declName = KitePsiUtil.findDeclarationName(element, type);
            if (targetName.equals(declName)) {
                var nameElement = KiteDeclarationHelper.findNameElementInDeclaration(element, type);
                var actualElement = nameElement != null ? nameElement : element;

                String typeName = type == KiteElementTypes.SCHEMA_DECLARATION ? "schema" : "component";
                Icon icon = type == KiteElementTypes.SCHEMA_DECLARATION
                        ? KiteStructureViewIcons.SCHEMA
                        : KiteStructureViewIcons.COMPONENT;

                items.add(new KiteNavigationItem(actualElement, declName, typeName, icon));
            }
        }

        // Recurse into children
        for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            collectClassItems(child, targetName, items);
        }
    }
}
